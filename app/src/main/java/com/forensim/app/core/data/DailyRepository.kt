package com.forensim.app.core.data

import com.forensim.app.core.network.ApiException
import com.forensim.app.core.network.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** One day of the home carousel: the quiz-of-the-day answer and the evidence-of-the-day answer. */
@Serializable
data class DailyEntry(
    val day: String,
    @SerialName("quiz_choice") val quizChoice: String? = null,
    @SerialName("quiz_correct") val quizCorrect: Boolean? = null,
    @SerialName("evidence_answer") val evidenceAnswer: String? = null,
    @SerialName("evidence_correct") val evidenceCorrect: Boolean? = null,
)

/** An answer made offline, sent the next time the server is reachable. */
@Serializable
private data class PendingAnswer(val kind: String, val day: String, val value: String, val correct: Boolean)

/**
 * Daily quiz / evidence answers and the streak, stored on the student's account (daily_activity
 * table) so they follow the student across phones and aren't shared on a shared phone. Answers
 * show instantly (optimistic) and are queued if offline; the server keeps exactly one answer per day.
 */
class DailyRepository(private val client: SupabaseClient, private val cache: JsonCache) {
    private val _entries = MutableStateFlow<Map<String, DailyEntry>>(emptyMap())
    val entries: StateFlow<Map<String, DailyEntry>> = _entries.asStateFlow()
    private val mutex = Mutex()
    private var loadedFor: String? = null

    /** Shows cached answers right away, sends any queued ones, then refreshes from the server. */
    suspend fun refresh() {
        if (!ensureLoaded()) return
        flushPending()
        runCatching {
            client.select("daily_activity?select=day,quiz_choice,quiz_correct,evidence_answer,evidence_correct&order=day.desc&limit=400",
                ListSerializer(DailyEntry.serializer()))
        }.onSuccess { rows ->
            // Keep optimistic answers that haven't reached the server yet.
            val pending = pending().associateBy { it.day }
            val merged = rows.associateBy { it.day }.toMutableMap()
            _entries.value.forEach { (day, local) -> if (day in pending) merged[day] = merge(merged[day], local) }
            setEntries(merged)
        }
    }

    suspend fun answerQuiz(day: LocalDate, choice: String, correct: Boolean) =
        answer(PendingAnswer(QUIZ, day.toString(), choice, correct))

    suspend fun answerEvidence(day: LocalDate, answer: String, correct: Boolean) =
        answer(PendingAnswer(EVIDENCE, day.toString(), answer, correct))

    /**
     * Loads this account's saved days from the phone once. Answering before that (the card is
     * tapped before refresh() ran) would otherwise save only today and wipe the cached history,
     * so an offline streak would drop back to 1. Returns false when nobody is signed in.
     */
    private suspend fun ensureLoaded(): Boolean {
        val uid = client.session.value?.userId ?: return false
        if (loadedFor != uid) {
            _entries.value = cache.read(CACHE, ListSerializer(DailyEntry.serializer())).orEmpty().associateBy { it.day }
            loadedFor = uid
        }
        return true
    }

    private suspend fun answer(a: PendingAnswer) {
        if (!ensureLoaded()) return
        val current = _entries.value[a.day]
        // One try: never overwrite an answer that already exists for the day.
        if (a.kind == QUIZ && current?.quizChoice != null) return
        if (a.kind == EVIDENCE && current?.evidenceAnswer != null) return
        setEntries(_entries.value + (a.day to applyLocal(current ?: DailyEntry(a.day), a)))
        try {
            send(a)
        } catch (e: ApiException) {
            if (e.isNetwork) queue(a) // offline: keep it and send later
        } catch (e: java.io.IOException) {
            queue(a)
        }
    }

    private suspend fun send(a: PendingAnswer) {
        val fn = if (a.kind == QUIZ) "answer_daily_quiz" else "answer_daily_evidence"
        val valueKey = if (a.kind == QUIZ) "p_choice" else "p_answer"
        val row = client.rpc(fn, buildJsonObject {
            put("p_day", a.day); put(valueKey, a.value); put("p_correct", a.correct)
        }, DailyEntry.serializer())
        setEntries(_entries.value + (row.day to row))
    }

    private suspend fun flushPending() = mutex.withLock {
        val waiting = pending()
        if (waiting.isEmpty()) return@withLock
        val left = waiting.filter { a ->
            try { send(a); false }
            catch (e: ApiException) { e.isNetwork } // drop answers the server rejects (e.g. day too old)
            catch (e: java.io.IOException) { true }
        }
        cache.write(PENDING, ListSerializer(PendingAnswer.serializer()), left)
    }

    private suspend fun queue(a: PendingAnswer) = mutex.withLock {
        cache.write(PENDING, ListSerializer(PendingAnswer.serializer()), pending() + a)
    }

    private suspend fun pending() = cache.read(PENDING, ListSerializer(PendingAnswer.serializer())).orEmpty()

    private suspend fun setEntries(map: Map<String, DailyEntry>) {
        _entries.value = map
        cache.write(CACHE, ListSerializer(DailyEntry.serializer()), map.values.sortedByDescending { it.day })
    }

    fun clear() { _entries.value = emptyMap(); loadedFor = null }

    companion object {
        private const val CACHE = "daily_activity"
        private const val PENDING = "daily_activity_pending"
        private const val QUIZ = "quiz"
        private const val EVIDENCE = "evidence"

        private fun applyLocal(e: DailyEntry, a: PendingAnswer) =
            if (a.kind == QUIZ) e.copy(quizChoice = a.value, quizCorrect = a.correct)
            else e.copy(evidenceAnswer = a.value, evidenceCorrect = a.correct)

        private fun merge(server: DailyEntry?, local: DailyEntry) = (server ?: local).copy(
            quizChoice = server?.quizChoice ?: local.quizChoice, quizCorrect = server?.quizCorrect ?: local.quizCorrect,
            evidenceAnswer = server?.evidenceAnswer ?: local.evidenceAnswer, evidenceCorrect = server?.evidenceCorrect ?: local.evidenceCorrect,
        )

        /**
         * Days in a row the quiz of the day was answered correctly. It still counts on the day
         * after the last correct answer (there's all of today to keep it going); a wrong answer
         * or a missed day ends it.
         */
        fun streak(entries: Map<String, DailyEntry>, today: LocalDate): Int {
            var day = if (entries[today.toString()]?.quizChoice != null) today else today.minusDays(1)
            var n = 0
            while (entries[day.toString()]?.quizCorrect == true) { n++; day = day.minusDays(1) }
            return n
        }

        /** Longest run of consecutive correct days ever. */
        fun best(entries: Map<String, DailyEntry>): Int {
            val days = entries.values.filter { it.quizCorrect == true }.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }.sorted()
            var best = 0; var run = 0; var prev: LocalDate? = null
            for (d in days) {
                run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
                best = maxOf(best, run); prev = d
            }
            return best
        }
    }
}
