package com.forensim.app.core.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forensim.app.ForensimApplication
import com.forensim.app.core.model.SimEventDto
import com.forensim.app.core.network.ApiException
import com.forensim.app.core.network.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

/** A practice case report finished while offline, waiting to be scored by the server. */
@Serializable
data class PendingSubmission(
    @SerialName("attempt_id") val attemptId: String,
    /** The case code, so the case can't be replayed offline while its report waits. */
    val code: String,
    val title: String,
    val events: List<SimEventDto>,
    @SerialName("queued_at") val queuedAt: Long,
)

/** Everything practice-related made on this phone that the server hasn't received yet. */
@Serializable
data class PendingSync(
    /** Finished training parts (sent in order; the server enforces 1 -> 2 -> 3). */
    val training: List<Int> = emptyList(),
    /** Attempts whose saved progress (the local checkpoint) is newer than the server's copy. */
    val checkpoints: List<String> = emptyList(),
    val submissions: List<PendingSubmission> = emptyList(),
) {
    val isEmpty: Boolean get() = training.isEmpty() && checkpoints.isEmpty() && submissions.isEmpty()
    /** What the student is told is waiting (progress saves are housekeeping and not counted). */
    val visibleCount: Int get() = training.size + submissions.size
}

enum class SyncOutcome {
    /** Nothing is left to send. */
    DONE,
    /** Still offline (or the server is busy): try again later. */
    RETRY,
    /** The session expired: the queue waits for the student to sign in again. */
    SIGNED_OUT,
}

/**
 * Offline practice sync (plan section 9). Practice work done without a connection — finished
 * training parts, practice case reports and case progress — is kept in the signed-in user's own
 * cache folder and sent the next time the server is reachable: when the app starts or a student
 * signs in, when the Simulate tab opens, and from a WorkManager job that waits for a network.
 *
 * Every call is idempotent on the server: `record_training_part` ignores repeats,
 * `submit-simulation` returns the existing score for an attempt that is already scored, and
 * `save_case_progress` refuses (and the queue drops) progress for a submitted attempt. Graded
 * cases are never queued: they still need a connection to submit.
 */
class PracticeSync(
    private val context: Context,
    private val client: SupabaseClient,
    private val cache: JsonCache,
    private val simulation: SimulationRepository,
) {
    private val _pending = MutableStateFlow(PendingSync())
    val pending: StateFlow<PendingSync> = _pending.asStateFlow()

    /** Guards the stored queue (short critical sections only). */
    private val store = Mutex()
    /** One flush at a time; queueing is never blocked by a slow network. */
    private val flushing = Mutex()
    private var loadedFor: String? = null

    /** Loads the signed-in user's queue (call on sign-in). */
    suspend fun load() {
        val uid = client.session.value?.userId ?: return
        store.withLock {
            loadedFor = uid
            _pending.value = cache.read(KEY, PendingSync.serializer()) ?: PendingSync()
        }
    }

    fun clear() {
        loadedFor = null
        _pending.value = PendingSync()
    }

    fun hasPendingReport(code: String) = pending.value.submissions.any { it.code.equals(code, ignoreCase = true) }

    suspend fun queueTraining(part: Int) {
        update { it.copy(training = (it.training + part).distinct().sorted()) }
        simulation.rememberTrainingDone(part)
        schedule()
    }

    suspend fun queueCheckpoint(attemptId: String) {
        update { p -> if (attemptId in p.checkpoints || p.submissions.any { it.attemptId == attemptId }) p else p.copy(checkpoints = p.checkpoints + attemptId) }
        schedule()
    }

    suspend fun queueSubmission(s: PendingSubmission) {
        update { p ->
            p.copy(
                submissions = p.submissions.filterNot { it.attemptId == s.attemptId } + s,
                checkpoints = p.checkpoints - s.attemptId,
            )
        }
        // This attempt is finished on this phone: don't let it be reopened offline.
        simulation.forgetCaseStart(s.code)
        schedule()
    }

    /** Sends whatever is waiting. Safe to call often: it returns at once when the queue is empty. */
    suspend fun flush(): SyncOutcome = flushing.withLock {
        val uid = client.session.value?.userId ?: return@withLock SyncOutcome.SIGNED_OUT
        if (loadedFor != uid) load()
        var outcome = SyncOutcome.DONE
        // A few passes, so anything queued while a pass was running is sent too.
        repeat(3) {
            val snapshot = _pending.value
            if (snapshot.isEmpty) return@withLock outcome
            outcome = sendAll(snapshot, uid)
            if (outcome != SyncOutcome.DONE) return@withLock outcome
        }
        if (_pending.value.isEmpty) SyncOutcome.DONE else SyncOutcome.RETRY
    }

    private suspend fun sendAll(p: PendingSync, uid: String): SyncOutcome {
        // Training first and in order: later parts need the earlier ones on the server.
        for (part in p.training) {
            when (val r = attempt(uid) { simulation.recordTraining(part) }) {
                Sent, Rejected -> update(uid) { it.copy(training = it.training - part) }
                else -> return (r as Failed).outcome
            }
        }
        for (s in p.submissions) {
            when (val r = attempt(uid) { simulation.submit(s.attemptId, s.events) }) {
                Sent, Rejected -> {
                    update(uid) { q -> q.copy(submissions = q.submissions.filterNot { it.attemptId == s.attemptId }, checkpoints = q.checkpoints - s.attemptId) }
                    simulation.clearCheckpoint(s.attemptId)
                }
                else -> return (r as Failed).outcome
            }
        }
        for (id in p.checkpoints) {
            if (p.submissions.any { it.attemptId == id }) continue
            val cp = simulation.localCheckpoint(id)
            val r = if (cp == null) Rejected else attempt(uid) { simulation.uploadCheckpoint(id, cp) }
            when (r) {
                Sent, Rejected -> update(uid) { it.copy(checkpoints = it.checkpoints - id) }
                else -> return (r as Failed).outcome
            }
        }
        return SyncOutcome.DONE
    }

    private sealed interface Attempt
    private data object Sent : Attempt
    /** The server refused it for good (e.g. the case was closed): drop it rather than retry forever. */
    private data object Rejected : Attempt
    private data class Failed(val outcome: SyncOutcome) : Attempt

    private suspend fun attempt(uid: String, block: suspend () -> Unit): Attempt = try {
        // The queue belongs to [uid]: never send it with another account's session.
        if (client.session.value?.userId != uid) Failed(SyncOutcome.SIGNED_OUT) else { block(); Sent }
    } catch (e: ApiException) {
        when {
            e.status == 401 -> Failed(SyncOutcome.SIGNED_OUT)
            retryable(e) -> Failed(SyncOutcome.RETRY)
            else -> {
                android.util.Log.w("ForensimSync", "Dropped a queued practice item: ${e.status} ${e.message}")
                Rejected
            }
        }
    } catch (e: java.io.IOException) {
        Failed(SyncOutcome.RETRY)
    }

    private suspend fun update(uid: String? = null, f: (PendingSync) -> PendingSync) = store.withLock {
        // Never write one user's queue into another's folder after an account switch mid-flush.
        if (uid != null && client.session.value?.userId != uid) return@withLock
        val next = f(_pending.value)
        _pending.value = next
        cache.write(KEY, PendingSync.serializer(), next)
    }

    /** Runs [Worker] once a network is available (keeps an already waiting run). */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<Worker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request) }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as? ForensimApplication)?.container ?: return Result.success()
            // Started in a fresh process: wait for the stored session to be restored.
            val state = withTimeoutOrNull(30_000) { c.auth.state.first { it !is AuthState.Loading } } ?: return Result.retry()
            if (state !is AuthState.SignedIn) return Result.success() // sent at the next sign-in
            return when (c.practiceSync.flush()) {
                SyncOutcome.DONE, SyncOutcome.SIGNED_OUT -> Result.success()
                SyncOutcome.RETRY -> Result.retry()
            }
        }
    }

    companion object {
        private const val KEY = "practice_sync_pending"
        private const val WORK = "practice-sync"

        /** Worth keeping and sending later: no connection, a busy server or a timeout. */
        fun retryable(e: ApiException) = e.isNetwork || e.status == 429 || e.status == 408 || e.status >= 500
    }
}
