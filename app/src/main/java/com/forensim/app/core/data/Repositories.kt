package com.forensim.app.core.data

import android.content.Context
import com.forensim.app.core.model.AdminUser
import com.forensim.app.core.model.AuditEntry
import com.forensim.app.core.model.Course
import com.forensim.app.core.model.CourseClass
import com.forensim.app.core.model.Enrollment
import com.forensim.app.core.model.LearningModule
import com.forensim.app.core.model.Lesson
import com.forensim.app.core.model.LessonProgress
import com.forensim.app.core.model.PracticeFeedback
import com.forensim.app.core.model.Profile
import com.forensim.app.core.model.Question
import com.forensim.app.core.model.Quiz
import com.forensim.app.core.model.QuizAttempt
import com.forensim.app.core.model.QuizResponse
import com.forensim.app.core.model.ReviewItem
import com.forensim.app.core.model.Role
import com.forensim.app.core.model.SimEventDto
import com.forensim.app.core.model.SimSubmitResult
import com.forensim.app.core.model.SimulationAttempt
import com.forensim.app.core.model.SubmitResult
import com.forensim.app.core.model.Subject
import com.forensim.app.core.model.SubjectClass
import com.forensim.app.core.model.LessonUnlock
import com.forensim.app.core.model.QuizUnlock
import com.forensim.app.core.model.SystemSetting
import com.forensim.app.core.model.TutorResponse
import com.forensim.app.core.model.UserAccount
import com.forensim.app.core.network.ApiException
import com.forensim.app.core.network.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

// --------------------------------------------------------------------------- auth

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserAccount) : AuthState
}

/** Session + role resolution. The role always comes from the server (public.my_role()). */
class AuthRepository(
    private val client: SupabaseClient,
    private val cache: JsonCache,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        appScope.launch {
            val restored = client.restoreSession()
            _state.value = if (restored == null) AuthState.SignedOut else runCatching<AuthState> { AuthState.SignedIn(loadAccount()) }
                .getOrElse { e ->
                    // Offline restore: keep the last known account so cached study content works.
                    if (e is ApiException && e.isNetwork) cache.read("account_${restored.userId}", AccountCache.serializer())
                        ?.toAccount()?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut
                    else AuthState.SignedOut
                }
        }
    }

    suspend fun signIn(email: String, password: String) {
        client.signIn(email, password)
        try {
            _state.value = AuthState.SignedIn(loadAccount())
        } catch (e: Exception) {
            client.signOut()
            throw e
        }
    }

    /** @return true when signed in immediately, false when e-mail confirmation is required. */
    suspend fun signUp(email: String, password: String, fullName: String): Boolean {
        val session = client.signUp(email, password, fullName, OAUTH_REDIRECT)
        if (session == null) {
            // No session came back. If the project auto-confirms e-mails, signing in works right
            // away; otherwise the account really is waiting for its confirmation e-mail.
            return runCatching { signIn(email, password) }.isSuccess
        }
        _state.value = AuthState.SignedIn(loadAccount())
        return session.accessToken.isNotEmpty()
    }

    /** When the signed-in account was created, or null if it can't be read right now. */
    suspend fun accountCreatedAt(): java.time.Instant? = runCatching {
        client.currentUserCreatedAt()?.let { java.time.OffsetDateTime.parse(it).toInstant() }
    }.getOrNull()

    /** Error from a social sign-in redirect, shown on the sign-in screen. */
    private val _oauthError = MutableStateFlow<String?>(null)
    val oauthError: StateFlow<String?> = _oauthError.asStateFlow()
    private val _oauthBusy = MutableStateFlow(false)
    val oauthBusy: StateFlow<Boolean> = _oauthBusy.asStateFlow()

    suspend fun enabledProviders(): Set<String> = runCatching { client.enabledProviders() }.getOrDefault(emptySet())

    suspend fun oauthUrl(provider: String): String {
        _oauthError.value = null
        return client.oauthAuthorizeUrl(provider, OAUTH_REDIRECT)
    }

    fun clearOAuthError() { _oauthError.value = null }

    /** Shown on the sign-in screen after signing out for a reason (e.g. the account was deleted). */
    private val _signedOutMessage = MutableStateFlow<String?>(null)
    val signedOutMessage: StateFlow<String?> = _signedOutMessage.asStateFlow()
    fun clearSignedOutMessage() { _signedOutMessage.value = null }

    /**
     * Permanently deletes the signed-in user's own account and data (delete-account Edge Function),
     * then signs out on this device. Required by Google Play for apps with account creation.
     */
    suspend fun deleteMyAccount() {
        client.invokeFunction("delete-account", buildJsonObject { put("confirm", "DELETE") }, JsonElement.serializer())
        _signedOutMessage.value = "Your account and its data were deleted."
        signOut()
    }

    /** Message for a signed-in user after an account link (e-mail change confirmed, errors). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** True after a password-reset link signed the user in: the app asks for a new password. */
    private val _passwordRecovery = MutableStateFlow(false)
    val passwordRecovery: StateFlow<Boolean> = _passwordRecovery.asStateFlow()

    fun dismissNotice() { _notice.value = null }
    fun dismissPasswordRecovery() { _passwordRecovery.value = false }

    /**
     * Handles com.forensim.app://login-callback for every e-mail and social link:
     * ?code=... (Google/Facebook sign-in or password reset), #message=... / #type=email_change
     * (e-mail change confirmations) and ?error_description=....
     */
    suspend fun completeOAuth(uri: android.net.Uri) {
        val signedIn = client.session.value != null
        val error = uri.getQueryParameter("error_description") ?: uri.fragmentParam("error_description")
        if (error != null) {
            val raw = error.replace('+', ' ')
            val text = if (raw.contains("invalid or has expired", ignoreCase = true))
                "That link has expired or was already used. Tap \"Forgot password?\" to get a new one, and open only the newest e-mail."
            else raw
            if (signedIn) _notice.value = text else _oauthError.value = text
            return
        }
        val code = uri.getQueryParameter("code")
        if (code == null) {
            // E-mail change links carry no code. Pick up the new address with a token refresh.
            val message = uri.fragmentParam("message")
            if (signedIn) {
                runCatching { client.refreshSession(); refreshAccount() }
                _notice.value = message ?: "Your e-mail address has been updated."
            } else {
                _oauthError.value = message ?: "Sign-in was cancelled."
            }
            return
        }
        _oauthBusy.value = true
        try {
            val flow = client.takePendingFlow()
            client.exchangeOAuthCode(code)
            _state.value = AuthState.SignedIn(loadAccount())
            if (flow == "recovery") _passwordRecovery.value = true
        } catch (e: Exception) {
            _oauthError.value = e.message ?: "Sign-in failed."
            runCatching { client.signOut() }
        } finally {
            _oauthBusy.value = false
        }
    }

    /** E-mails a reset link; opening it on this phone signs in and asks for a new password. */
    suspend fun requestPasswordReset(email: String) = client.requestPasswordReset(email, OAUTH_REDIRECT)

    /** E-mails a confirmation link for the new address; the change applies once it is opened. */
    suspend fun requestEmailChange(newEmail: String) = client.requestEmailChange(newEmail, OAUTH_REDIRECT)

    suspend fun updatePassword(password: String) {
        client.updatePassword(password)
        // The reset page clears passwordRecovery itself after showing "Password updated".
    }

    private fun android.net.Uri.fragmentParam(name: String): String? =
        encodedFragment?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it.first() == name }
            ?.getOrNull(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    companion object {
        const val OAUTH_REDIRECT = "com.forensim.app://login-callback"
    }

    /**
     * Signs out on this device at once, then revokes the session on the server in the app scope.
     * The local part cannot be cancelled: callers often launch this from a dialog that closes
     * (and cancels its scope) in the same tap.
     */
    suspend fun signOut() {
        val token = withContext(NonCancellable) {
            val uid = (state.value as? AuthState.SignedIn)?.user?.id
            val token = client.signOutLocally()
            _state.value = AuthState.SignedOut
            if (uid != null) runCatching { cache.clearUser(uid) } // isolate local data per user (plan section 9)
            token
        }
        if (token != null) appScope.launch { client.revokeSession(token) }
    }

    /** Advanced > Sign out of all devices: revoke every session server-side, then sign out here. */
    suspend fun signOutEverywhere() {
        client.revokeAllSessions()
        signOut()
    }

    /** Advanced > Test connection. */
    suspend fun pingServer(): Long = client.ping()

    suspend fun refreshAccount() {
        _state.value = AuthState.SignedIn(loadAccount())
    }

    private suspend fun loadAccount(): UserAccount {
        val session = client.session.value ?: throw ApiException(401, "Not signed in")
        val roleRaw = client.rpcRaw("my_role")
        val role = Role.from((roleRaw as? JsonPrimitive)?.contentOrNull)
            ?: throw ApiException(403, "This account is suspended. Contact your administrator.")
        val profile = client.select("profiles?id=eq.${session.userId}&select=*", ListSerializer(Profile.serializer())).firstOrNull()
        val account = UserAccount(session.userId, session.email, profile?.fullName?.ifBlank { null } ?: session.email.substringBefore('@'), role)
        cache.write("account_${account.id}", AccountCache.serializer(), AccountCache.from(account))
        return account
    }

    @kotlinx.serialization.Serializable
    private data class AccountCache(val id: String, val email: String, val name: String, val role: String) {
        fun toAccount() = Role.from(role)?.let { UserAccount(id, email, name, it) }
        companion object {
            fun from(a: UserAccount) = AccountCache(a.id, a.email, a.fullName, a.role.name)
        }
    }
}

// --------------------------------------------------------------------------- cache

/** Small per-user JSON file cache for offline study (plan section 9). */
class JsonCache(private val context: Context, private val client: SupabaseClient) {
    private val root = File(context.filesDir, "cache").apply { mkdirs() }

    private fun userDir(uid: String? = client.session.value?.userId) =
        File(root, uid ?: "anonymous").apply { mkdirs() }

    // Disk I/O and JSON work run on the IO pool so a cache hit never blocks a frame.
    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) = withContext(Dispatchers.IO) {
        runCatching { File(userDir(), "$key.json").writeText(client.json.encodeToString(serializer, value)) }
        Unit
    }

    suspend fun <T> read(key: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        runCatching {
            File(userDir(), "$key.json").takeIf { it.exists() }?.readText()?.let { client.json.decodeFromString(serializer, it) }
        }.getOrNull()
    }

    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        runCatching { File(userDir(), "$key.json").delete() }
        Unit
    }

    /** Saved lesson data plus downloaded lesson PDFs for the signed-in user. */
    private fun offlineDirs(): List<File> {
        val uid = client.session.value?.userId ?: "anonymous"
        return listOf(File(root, uid), File(context.cacheDir, "lesson-pdfs/$uid"))
    }

    suspend fun offlineSizeBytes(): Long = withContext(Dispatchers.IO) {
        offlineDirs().sumOf { dir -> dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
    }

    suspend fun clearOffline() = withContext(Dispatchers.IO) {
        offlineDirs().forEach { it.deleteRecursively() }
    }

    suspend fun clearUser(uid: String) = withContext(Dispatchers.IO) {
        File(root, uid).deleteRecursively()
        Unit
    }
}

/** Network first; on connectivity failure fall back to the last cached copy. */
private suspend fun <T> cached(cache: JsonCache, key: String, serializer: KSerializer<T>, fetch: suspend () -> T): CachedResult<T> =
    try {
        val fresh = fetch()
        cache.write(key, serializer, fresh)
        CachedResult(fresh, fromCache = false)
    } catch (e: ApiException) {
        if (!e.isNetwork) throw e
        val old = cache.read(key, serializer) ?: throw e
        CachedResult(old, fromCache = true)
    }

data class CachedResult<T>(val value: T, val fromCache: Boolean)

// --------------------------------------------------------------------------- learning

class LearningRepository(private val client: SupabaseClient, private val cache: JsonCache) {

    // Lessons marked complete while the app is open, per user. Every screen showing lessons
    // listens to this, so a tick made on the lesson page shows on the page you go back to.
    private val completedNow = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** Lessons the signed-in user has marked complete since the app started. */
    val completedLessons: Flow<Set<String>> = combine(completedNow, client.session) { byUser, s -> byUser[s?.userId].orEmpty() }

    // Instant reads of the last saved copy so screens can paint before the network answers
    // (stale-while-revalidate). Null when nothing has been saved yet.
    suspend fun peekModules() = cache.read("modules", ListSerializer(LearningModule.serializer()))
    suspend fun peekLessons() = cache.read("lessons", ListSerializer(Lesson.serializer()))
    suspend fun peekProgress() = cache.read("progress", ListSerializer(LessonProgress.serializer()))
    suspend fun peekQuizzes() = cache.read("quizzes", ListSerializer(Quiz.serializer()))
    suspend fun peekAttempts() = cache.read("quiz_attempts", ListSerializer(QuizAttempt.serializer()))
    suspend fun peekClasses() = cache.read("classes", ListSerializer(CourseClass.serializer()))

    suspend fun modules(): CachedResult<List<LearningModule>> =
        cached(cache, "modules", ListSerializer(LearningModule.serializer())) {
            client.select("modules?select=*&order=position", ListSerializer(LearningModule.serializer()))
        }

    suspend fun lessons(): CachedResult<List<Lesson>> =
        cached(cache, "lessons", ListSerializer(Lesson.serializer())) {
            client.select("lessons?select=*&order=position", ListSerializer(Lesson.serializer()))
        }

    suspend fun progress(): CachedResult<List<LessonProgress>> {
        val uid = client.session.value?.userId ?: return CachedResult(emptyList(), true)
        return cached(cache, "progress", ListSerializer(LessonProgress.serializer())) {
            client.select("lesson_progress?select=lesson_id,completed_at&student_id=eq.$uid", ListSerializer(LessonProgress.serializer()))
        }
    }

    /**
     * Advanced > Download all lessons now: refreshes the saved copy of modules, lessons, quizzes and
     * progress so everything opens offline. Returns the number of lessons saved.
     */
    suspend fun downloadForOffline(): Int {
        val lessons = lessons()
        if (lessons.fromCache) throw ApiException(0, "You appear to be offline. Check your connection and try again.")
        modules()
        runCatching { progress() }
        runCatching { quizzes() }
        return lessons.value.size
    }

    /** A professor-uploaded lesson PDF, kept in the per-user cache so it opens offline afterwards. */
    suspend fun lessonPdf(path: String, cacheDir: java.io.File): java.io.File {
        val dir = java.io.File(cacheDir, "lesson-pdfs/${client.session.value?.userId ?: "anon"}").apply { mkdirs() }
        val file = java.io.File(dir, path.replace('/', '_'))
        if (file.length() > 0) return file
        val bytes = client.downloadObject(StaffRepository.PDF_BUCKET, path)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val tmp = java.io.File(dir, file.name + ".part")
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
        }
        return file
    }

    suspend fun markLessonComplete(lessonId: String) {
        client.insert("lesson_progress", buildJsonObject { put("lesson_id", lessonId) }, upsert = true)
        client.session.value?.userId?.let { uid -> completedNow.update { it + (uid to (it[uid].orEmpty() + lessonId)) } }
        // Keep the saved copy in step, so the tick survives going back, reopening and offline.
        val saved = peekProgress().orEmpty()
        if (saved.none { it.lessonId == lessonId }) {
            cache.write("progress", ListSerializer(LessonProgress.serializer()),
                saved + LessonProgress(lessonId, java.time.OffsetDateTime.now().toString()))
        }
    }

    suspend fun quizzes(): List<Quiz> =
        client.select("quizzes?select=*&order=created_at", ListSerializer(Quiz.serializer()))
            .also { cache.write("quizzes", ListSerializer(Quiz.serializer()), it) }

    suspend fun quiz(id: String): Quiz =
        client.select("quizzes?select=*&id=eq.$id", ListSerializer(Quiz.serializer())).firstOrNull()
            ?: throw ApiException(404, "Quiz not found")

    /** Graded questions are only returned by RLS while the student holds an attempt. */
    suspend fun questions(quizId: String): List<Question> =
        client.select(
            "questions?select=id,quiz_id,position,prompt,objective,question_options(id,position,body)&quiz_id=eq.$quizId&order=position&question_options.order=position",
            ListSerializer(Question.serializer()),
        )

    suspend fun checkPracticeAnswer(questionId: String, optionId: String): PracticeFeedback =
        client.rpc("check_practice_answer", buildJsonObject { put("p_question_id", questionId); put("p_option_id", optionId) }, PracticeFeedback.serializer())

    suspend fun myAttempts(): List<QuizAttempt> {
        val uid = client.session.value?.userId ?: return emptyList()
        return client.select("quiz_attempts?select=*&student_id=eq.$uid&order=started_at.desc", ListSerializer(QuizAttempt.serializer()))
            .also { cache.write("quiz_attempts", ListSerializer(QuizAttempt.serializer()), it) }
    }

    suspend fun createAttempt(quizId: String): QuizAttempt =
        client.rpc("create_quiz_attempt", buildJsonObject { put("p_quiz_id", quizId) }, QuizAttempt.serializer())

    suspend fun responses(attemptId: String): List<QuizResponse> =
        client.select("quiz_responses?select=question_id,option_id&attempt_id=eq.$attemptId", ListSerializer(QuizResponse.serializer()))

    suspend fun saveResponse(attemptId: String, questionId: String, optionId: String) {
        client.rpcRaw("save_quiz_response", buildJsonObject {
            put("p_attempt_id", attemptId); put("p_question_id", questionId); put("p_option_id", optionId)
        })
    }

    suspend fun submitAttempt(attemptId: String): SubmitResult =
        client.rpc("submit_quiz_attempt", buildJsonObject { put("p_attempt_id", attemptId) }, SubmitResult.serializer())

    suspend fun review(attemptId: String): List<ReviewItem> =
        client.rpc("get_attempt_review", buildJsonObject { put("p_attempt_id", attemptId) }, ListSerializer(ReviewItem.serializer()))

    suspend fun myClasses(): List<CourseClass> =
        client.select("classes?select=*&order=created_at", ListSerializer(CourseClass.serializer()))
            .also { cache.write("classes", ListSerializer(CourseClass.serializer()), it) }

    /** Joins with a class code. Returns "active", or "pending" when the professor must approve. */
    suspend fun joinClass(code: String): String =
        client.rpc("join_class", buildJsonObject { put("p_code", code) }, Enrollment.serializer()).status

    suspend fun peekSubjectClasses() = cache.read("subject_classes", ListSerializer(SubjectClass.serializer()))

    /** Joined subject classes with professor name and unlocked lesson counts. */
    suspend fun mySubjectClasses(): CachedResult<List<SubjectClass>> =
        cached(cache, "subject_classes", ListSerializer(SubjectClass.serializer())) {
            client.rpc("my_subject_classes", JsonObject(emptyMap()), ListSerializer(SubjectClass.serializer()))
        }

    suspend fun hasActiveAssessment(): Boolean =
        (client.rpcRaw("has_active_assessment") as? JsonPrimitive)?.contentOrNull == "true"

    suspend fun systemNotice(): String? = runCatching {
        client.select("system_settings?select=key,value&key=eq.system_notice", ListSerializer(SystemSetting.serializer()))
            .firstOrNull()?.value?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

// --------------------------------------------------------------------------- simulation

class SimulationRepository(private val client: SupabaseClient, private val cache: JsonCache) {

    suspend fun peekAttempts() = cache.read("sim_attempts", ListSerializer(SimulationAttempt.serializer()))
    /** Training parts last known to be done, so finished parts stay hidden while offline or loading. */
    suspend fun peekTraining(): Set<Int>? = cache.read("training_parts", ListSerializer(Int.serializer()))?.toSet()

    suspend fun submit(attemptId: String, events: List<SimEventDto>): SimSubmitResult =
        client.invokeFunction(
            "submit-simulation",
            buildJsonObject {
                put("attempt_id", attemptId)
                put("events", client.json.encodeToJsonElement(ListSerializer(SimEventDto.serializer()), events))
            },
            SimSubmitResult.serializer(),
        )

    /** Training parts finished on this phone that the server hasn't received yet (set by [PracticeSync]). */
    var pendingTraining: () -> Set<Int> = { emptySet() }

    /** Training parts (1-3) this student has completed, including parts finished offline and not yet synced. */
    suspend fun trainingParts(): Set<Int> {
        val uid = client.session.value?.userId ?: return emptySet()
        return (client.select("training_progress?select=part&student_id=eq.$uid", ListSerializer(com.forensim.app.core.model.TrainingRow.serializer()))
            .map { it.part }.toSet() + pendingTraining())
            .also { saveTraining(it) }
    }

    /** Marks a part done on this phone (finished offline; [PracticeSync] sends it later). */
    suspend fun rememberTrainingDone(part: Int) = saveTraining(peekTraining().orEmpty() + part)

    /** Records a finished training part; the server enforces the 1 -> 2 -> 3 order. */
    suspend fun recordTraining(part: Int): Set<Int> =
        client.rpc("record_training_part", buildJsonObject { put("p_part", part) }, ListSerializer(Int.serializer())).toSet()
            .also { saveTraining((peekTraining().orEmpty() + it + part)) }

    private suspend fun saveTraining(parts: Set<Int>) =
        cache.write("training_parts", ListSerializer(Int.serializer()), parts.sorted())

    /** Opens (or resumes) a professor case by its 6-character code. */
    suspend fun startCase(code: String): com.forensim.app.core.model.CaseStart =
        client.rpc("start_case", buildJsonObject { put("p_code", code) }, com.forensim.app.core.model.CaseStart.serializer())

    private fun caseStartKey(code: String) = "case_start_" + code.filter { it.isLetterOrDigit() }.uppercase()

    /**
     * Keeps an opened practice case (its attempt, seed and manifest) on this phone, so it can be
     * played again offline. Graded cases are never kept: they always need a connection.
     */
    suspend fun cacheCaseStart(code: String, start: com.forensim.app.core.model.CaseStart) {
        if (start.attempt.mode == "assessment") forgetCaseStart(code)
        else cache.write(caseStartKey(code), com.forensim.app.core.model.CaseStart.serializer(), start)
    }

    /** The practice case last opened online with [code], for playing it offline. */
    suspend fun cachedCaseStart(code: String) =
        cache.read(caseStartKey(code), com.forensim.app.core.model.CaseStart.serializer())?.takeIf { it.attempt.mode != "assessment" }

    suspend fun forgetCaseStart(code: String) = cache.remove(caseStartKey(code))

    suspend fun myAttempts(): List<SimulationAttempt> {
        val uid = client.session.value?.userId ?: return emptyList()
        // Listed columns: the saved checkpoint (an event log) is only needed when resuming.
        val columns = "id,scenario_id,scenario_version,student_id,mode,seed,manifest,status,started_at,submitted_at,score,max_score,feedback,feedback_released,case_session_id"
        return client.select("simulation_attempts?select=$columns&student_id=eq.$uid&order=started_at.desc&limit=30", ListSerializer(SimulationAttempt.serializer()))
            .also { cache.write("sim_attempts", ListSerializer(SimulationAttempt.serializer()), it) }
    }

    private val myCaseList = ListSerializer(com.forensim.app.core.model.MyCase.serializer())

    suspend fun peekMyCases() = cache.read("my_cases", myCaseList)

    /** Every professor case this student has opened, with their latest attempt (unfinished ones first). */
    suspend fun myCases(): List<com.forensim.app.core.model.MyCase> =
        client.rpc("my_cases", JsonObject(emptyMap()), myCaseList).also { cache.write("my_cases", myCaseList, it) }

    private fun checkpointKey(attemptId: String) = "case_checkpoint_$attemptId"

    /** Work saved on this phone for an unfinished case (may be newer than the server's copy when offline). */
    suspend fun localCheckpoint(attemptId: String) =
        cache.read(checkpointKey(attemptId), com.forensim.app.core.model.CaseCheckpoint.serializer())

    /** Saves an unfinished case's work on this phone. */
    suspend fun saveLocalCheckpoint(attemptId: String, checkpoint: com.forensim.app.core.model.CaseCheckpoint) =
        cache.write(checkpointKey(attemptId), com.forensim.app.core.model.CaseCheckpoint.serializer(), checkpoint)

    /** Saves an unfinished case's work on the server, so it can be resumed on any phone. */
    suspend fun uploadCheckpoint(attemptId: String, checkpoint: com.forensim.app.core.model.CaseCheckpoint) {
        client.rpcRaw("save_case_progress", buildJsonObject {
            put("p_attempt_id", attemptId)
            put("p_progress", checkpoint.progress)
            put("p_events", client.json.encodeToJsonElement(ListSerializer(SimEventDto.serializer()), checkpoint.events))
        })
    }

    suspend fun clearCheckpoint(attemptId: String) = cache.remove(checkpointKey(attemptId))
}

// --------------------------------------------------------------------------- class chat

class ChatRepository(private val client: SupabaseClient, private val cache: JsonCache, private val photoDir: java.io.File) {
    private val list = ListSerializer(com.forensim.app.core.model.ClassMessage.serializer())
    private val memberList = ListSerializer(com.forensim.app.core.model.ChatMember.serializer())

    // Last-known messages, members and photos, so reopening a chat shows it instantly while
    // fresh data loads in the background. Memory first, then the on-device cache.
    private val messageMemo = java.util.concurrent.ConcurrentHashMap<String, List<com.forensim.app.core.model.ClassMessage>>()
    private val memberMemo = java.util.concurrent.ConcurrentHashMap<String, List<com.forensim.app.core.model.ChatMember>>()
    private val photoMemo = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /** Last-known messages for [classId] (empty if never loaded on this device). */
    suspend fun cached(classId: String): List<com.forensim.app.core.model.ClassMessage> =
        messageMemo[classId] ?: cache.read("chat_$classId", list)?.also { messageMemo[classId] = it }.orEmpty()

    /** Instant, memory-only peek (no disk), for the first frame. */
    fun peek(classId: String): List<com.forensim.app.core.model.ClassMessage>? = messageMemo[classId]

    fun peekPhoto(path: String): ByteArray? = photoMemo[path]

    /** Warms the cache from the class page, so the chat is ready before it is opened. */
    suspend fun prefetch(classId: String) {
        runCatching { messages(classId) }
        runCatching { members(classId).mapNotNull { it.avatarPath }.forEach { runCatching { photo(it) } } }
    }

    val myId: String? get() = client.session.value?.userId

    /** The latest [limit] messages (oldest first), or only those after [afterId]. */
    suspend fun messages(classId: String, afterId: Long? = null, limit: Int = 100): List<com.forensim.app.core.model.ClassMessage> =
        if (afterId == null) client.select("class_messages?select=*&class_id=eq.$classId&order=id.desc&limit=$limit", list).reversed()
            .also { messageMemo[classId] = it; cache.write("chat_$classId", list, it) }
        else client.select("class_messages?select=*&class_id=eq.$classId&id=gt.$afterId&order=id.asc", list)

    /** Sends and returns the stored message (with the server's id, name and time). */
    suspend fun send(classId: String, body: String): com.forensim.app.core.model.ClassMessage =
        client.insertReturning("class_messages", buildJsonObject { put("class_id", classId); put("body", body.trim().take(2000)) },
            com.forensim.app.core.model.ClassMessage.serializer())

    /** Keeps the memory cache in step with what the screen shows (sends, deletes, live updates). */
    fun remember(classId: String, messages: List<com.forensim.app.core.model.ClassMessage>) { messageMemo[classId] = messages }

    suspend fun delete(messageId: Long) = client.delete("class_messages", "id=eq.$messageId")

    /** Everyone in the class chat (professor + approved students) with their photo path. */
    suspend fun members(classId: String): List<com.forensim.app.core.model.ChatMember> =
        memberMemo[classId] ?: client.rpc("class_chat_members", buildJsonObject { put("p_class", classId) }, memberList)
            .also { memberMemo[classId] = it }

    /** A chat member's profile photo, cached on disk by its (unique, immutable) storage path. */
    suspend fun photo(path: String): ByteArray = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        photoMemo[path]?.let { return@withContext it }
        val file = java.io.File(photoDir, path.replace('/', '_'))
        val bytes = if (file.exists()) file.readBytes()
            else client.downloadObject(ProfileRepository.AVATARS, path).also { photoDir.mkdirs(); file.writeBytes(it) }
        bytes.also { photoMemo[path] = it }
    }

    /** Emits whenever this class's messages change (Supabase Realtime). */
    fun changes(classId: String) = client.tableChanges("class_messages", "class_id=eq.$classId")
}

// --------------------------------------------------------------------------- tutor

data class ChatTurn(val role: String, val content: String)

class TutorRepository(private val client: SupabaseClient) {
    suspend fun ask(question: String, moduleTitle: String?, history: List<ChatTurn>): TutorResponse =
        client.invokeFunction(
            "ai-tutor",
            buildJsonObject {
                put("question", question)
                moduleTitle?.let { put("module_title", it) }
                put("history", buildJsonArray {
                    history.takeLast(8).forEach { t -> add(buildJsonObject { put("role", t.role); put("content", t.content) }) }
                })
            },
            TutorResponse.serializer(),
        )
}

// --------------------------------------------------------------------------- reports

/**
 * In-app reports of class chat messages, users and AI-generated content, as Google Play's User
 * Generated Content and AI-Generated Content policies require. Administrators (and the class's
 * professor, for chat) review them; see migration 20261204000001_content_reports.sql.
 */
class ReportRepository(private val client: SupabaseClient) {
    suspend fun report(
        kind: String, reason: String, details: String? = null, classId: String? = null,
        messageId: Long? = null, reportedUserId: String? = null, excerpt: String? = null,
    ) = client.insert("content_reports", buildJsonObject {
        put("kind", kind)
        put("reason", reason)
        details?.trim()?.takeIf { it.isNotEmpty() }?.let { put("details", it.take(500)) }
        classId?.let { put("class_id", it) }
        messageId?.let { put("message_id", it) }
        reportedUserId?.takeIf { it.isNotBlank() }?.let { put("reported_user_id", it) }
        excerpt?.takeIf { it.isNotBlank() }?.let { put("excerpt", it.take(4000)) }
    })

    /** Open reports the caller may review (all for administrators). */
    suspend fun open(): List<com.forensim.app.core.model.ContentReport> =
        client.select("content_reports?select=*&status=eq.open&order=created_at.desc&limit=200",
            ListSerializer(com.forensim.app.core.model.ContentReport.serializer()))

    suspend fun resolve(id: Long) = client.update("content_reports", "id=eq.$id", buildJsonObject {
        put("status", "resolved")
        put("resolved_at", java.time.Instant.now().toString())
        client.session.value?.userId?.let { put("resolved_by", it) }
    })
}

// --------------------------------------------------------------------------- staff

/** Private bucket for class cover photos, objects at <class_id>/<random>.jpg. */
const val CLASS_COVERS = "class-covers"

class StaffRepository(private val client: SupabaseClient) {

    suspend fun classes(): List<CourseClass> =
        client.select("classes?select=*&order=created_at", ListSerializer(CourseClass.serializer()))

    suspend fun courses(): List<Course> =
        client.select("courses?select=*&order=created_at", ListSerializer(Course.serializer()))

    suspend fun enrollments(classId: String): List<Enrollment> =
        client.select("enrollments?select=*&class_id=eq.$classId&order=enrolled_at", ListSerializer(Enrollment.serializer()))

    suspend fun profiles(ids: Collection<String>): List<Profile> {
        if (ids.isEmpty()) return emptyList()
        return client.select("profiles?select=*&id=in.(${ids.joinToString(",")})", ListSerializer(Profile.serializer()))
    }

    suspend fun setJoinOpen(classId: String, open: Boolean) =
        client.update("classes", "id=eq.$classId", buildJsonObject { put("join_open", open) })

    // Subject classes -------------------------------------------------------------

    suspend fun setRequireApproval(classId: String, on: Boolean) =
        client.update("classes", "id=eq.$classId", buildJsonObject { put("require_approval", on) })

    /** [key] is a ClassColor key, or null for the theme default. Students see it on their class card. */
    suspend fun setClassColor(classId: String, key: String?) =
        client.update("classes", "id=eq.$classId", buildJsonObject { put("color", key) })

    /** Uploads an already downscaled JPEG as the class cover, deletes the old one, and returns the new path. */
    suspend fun uploadClassCover(classId: String, jpeg: ByteArray, oldPath: String?): String {
        val path = "$classId/${java.util.UUID.randomUUID()}.jpg"
        client.uploadObject(CLASS_COVERS, path, jpeg, "image/jpeg")
        client.update("classes", "id=eq.$classId", buildJsonObject { put("cover_path", path) })
        if (oldPath != null && oldPath != path) runCatching { client.deleteObject(CLASS_COVERS, oldPath) }
        return path
    }

    suspend fun removeClassCover(classId: String, oldPath: String?) {
        client.update("classes", "id=eq.$classId", buildJsonObject { put("cover_path", null as String?) })
        if (oldPath != null) runCatching { client.deleteObject(CLASS_COVERS, oldPath) }
    }

    /** Approves or declines one pending student, or all of them when [studentId] is null. */
    suspend fun reviewJoin(classId: String, studentId: String?, approve: Boolean) {
        client.rpcRaw("professor_review_join", buildJsonObject {
            put("p_class_id", classId); put("p_student_id", studentId?.let { JsonPrimitive(it) } ?: JsonNull); put("p_approve", approve)
        })
    }

    suspend fun subjects(): List<Subject> =
        client.rpc("list_subjects", JsonObject(emptyMap()), ListSerializer(Subject.serializer()))

    suspend fun createClass(moduleId: String, section: String, term: String): CourseClass =
        client.rpc("professor_create_class", buildJsonObject {
            put("p_module_id", moduleId); put("p_section", section.trim()); put("p_term", term.trim())
        }, CourseClass.serializer())

    suspend fun deleteClass(classId: String) {
        client.rpcRaw("professor_delete_class", buildJsonObject { put("p_class_id", classId) })
    }

    suspend fun lessonUnlocks(classIds: Collection<String>): List<LessonUnlock> {
        if (classIds.isEmpty()) return emptyList()
        return client.select("class_lesson_unlocks?select=class_id,lesson_id&class_id=in.(${classIds.joinToString(",")})",
            ListSerializer(LessonUnlock.serializer()))
    }

    suspend fun quizUnlocks(classIds: Collection<String>): List<QuizUnlock> {
        if (classIds.isEmpty()) return emptyList()
        return client.select("class_quiz_unlocks?select=class_id,quiz_id&class_id=in.(${classIds.joinToString(",")})",
            ListSerializer(QuizUnlock.serializer()))
    }

    suspend fun setLessonUnlocked(classId: String, lessonId: String, unlocked: Boolean) {
        client.rpcRaw("professor_set_lesson_unlocked", buildJsonObject {
            put("p_class_id", classId); put("p_lesson_id", lessonId); put("p_unlocked", unlocked)
        })
    }

    /** Removes an approved student from the class (server: professor_remove_student). */
    suspend fun removeStudent(classId: String, studentId: String) {
        client.rpcRaw("professor_remove_student", buildJsonObject { put("p_class_id", classId); put("p_student_id", studentId) })
    }

    suspend fun setAllLessonsUnlocked(classId: String, unlocked: Boolean) {
        client.rpcRaw("professor_set_all_lessons_unlocked", buildJsonObject { put("p_class_id", classId); put("p_unlocked", unlocked) })
    }

    suspend fun setQuizUnlocked(classId: String, quizId: String, unlocked: Boolean) {
        client.rpcRaw("professor_set_quiz_unlocked", buildJsonObject {
            put("p_class_id", classId); put("p_quiz_id", quizId); put("p_unlocked", unlocked)
        })
    }

    /** Deletes a quiz the professor made in a class (server: professor_delete_quiz). */
    suspend fun deleteQuiz(quizId: String) {
        client.rpcRaw("professor_delete_quiz", buildJsonObject { put("p_quiz_id", quizId) })
    }

    /** Removes a built-in quiz from one class, or puts it back (server: professor_set_quiz_hidden). */
    suspend fun setQuizHidden(classId: String, quizId: String, hidden: Boolean) {
        client.rpcRaw("professor_set_quiz_hidden", buildJsonObject {
            put("p_class_id", classId); put("p_quiz_id", quizId); put("p_hidden", hidden)
        })
    }

    /** Built-in quizzes removed from these classes. Empty until the delete-quizzes migration has run. */
    suspend fun hiddenQuizzes(classIds: Collection<String>): List<QuizUnlock> {
        if (classIds.isEmpty()) return emptyList()
        return try {
            client.select("class_hidden_quizzes?select=class_id,quiz_id&class_id=in.(${classIds.joinToString(",")})",
                ListSerializer(QuizUnlock.serializer()))
        } catch (e: com.forensim.app.core.network.ApiException) {
            emptyList()
        }
    }

    suspend fun lessonProgress(studentIds: Collection<String>): List<LessonProgress> {
        if (studentIds.isEmpty()) return emptyList()
        return client.select("lesson_progress?select=lesson_id,completed_at,student_id&student_id=in.(${studentIds.joinToString(",")})", ListSerializer(LessonProgress.serializer()))
    }

    suspend fun quizAttempts(): List<QuizAttempt> =
        client.select("quiz_attempts?select=*&order=started_at.desc&limit=500", ListSerializer(QuizAttempt.serializer()))

    suspend fun simulationAttempts(): List<SimulationAttempt> =
        client.select("simulation_attempts?select=*&order=started_at.desc&limit=200", ListSerializer(SimulationAttempt.serializer()))

    suspend fun setModulePublished(moduleId: String, published: Boolean) {
        client.rpcRaw("set_module_published", buildJsonObject { put("p_module_id", moduleId); put("p_published", published) })
    }

    suspend fun approveLesson(lessonId: String) =
        client.update("lessons", "id=eq.$lessonId", buildJsonObject { put("review_status", "approved") })

    suspend fun setReviewPolicy(quizId: String, policy: String) {
        client.rpcRaw("set_quiz_review_policy", buildJsonObject { put("p_quiz_id", quizId); put("p_policy", policy) })
    }

    suspend fun releaseSimulationFeedback(attemptId: String, released: Boolean) {
        client.rpcRaw("release_simulation_feedback", buildJsonObject { put("p_attempt_id", attemptId); put("p_released", released) })
    }

    // Professor publishing (goes live immediately, no admin approval) -------------

    suspend fun createModule(courseId: String, title: String, summary: String): LearningModule =
        client.rpc("professor_create_module", buildJsonObject {
            put("p_course_id", courseId); put("p_title", title); put("p_summary", summary)
        }, LearningModule.serializer())

    /** Uploads the PDF into the module's private folder, then registers it as a published lesson. */
    suspend fun uploadPdfLesson(moduleId: String, title: String, readingMinutes: Int, pdf: ByteArray, classId: String? = null): Lesson {
        val path = "$moduleId/${java.util.UUID.randomUUID()}.pdf"
        client.uploadObject(PDF_BUCKET, path, pdf, "application/pdf")
        return try {
            client.rpc("professor_add_pdf_lesson", buildJsonObject {
                put("p_module_id", moduleId); put("p_title", title); put("p_pdf_path", path); put("p_reading_minutes", readingMinutes)
                classId?.let { put("p_class_id", it) }
            }, Lesson.serializer())
        } catch (e: ApiException) {
            runCatching { client.deleteObject(PDF_BUCKET, path) }
            throw e
        }
    }

    suspend fun createQuiz(moduleId: String, title: String, graded: Boolean, timeLimitMinutes: Int?, maxAttempts: Int?, questions: List<QuizDraftQuestion>, classId: String? = null): Quiz =
        client.rpc("professor_create_quiz", buildJsonObject {
            put("p_module_id", moduleId); put("p_title", title); put("p_kind", if (graded) "graded" else "practice")
            classId?.let { put("p_class_id", it) }
            put("p_time_limit_seconds", timeLimitMinutes?.let { it * 60 } ?: 0)
            put("p_max_attempts", maxAttempts ?: 0)
            put("p_questions", buildJsonArray {
                questions.forEach { q ->
                    add(buildJsonObject {
                        put("prompt", q.prompt.trim())
                        put("options", buildJsonArray { q.options.forEach { add(JsonPrimitive(it.trim())) } })
                        put("correct", q.correct)
                        put("explanation", q.explanation.trim())
                        put("points", q.points)
                    })
                }
            })
        }, Quiz.serializer())

    // Professor-generated cases --------------------------------------------------

    suspend fun caseSessions(): List<com.forensim.app.core.model.CaseSession> =
        client.select("case_sessions?select=*&order=created_at.desc", ListSerializer(com.forensim.app.core.model.CaseSession.serializer()))

    suspend fun createCase(
        classId: String, title: String, caseType: String, location: String, locationVersion: Int,
        briefing: String, seed: Int, manifest: List<com.forensim.app.core.model.ManifestItem>, graded: Boolean,
        victim: com.forensim.app.engine.scene.Victim? = null,
    ): com.forensim.app.core.model.CaseSession =
        client.rpc("professor_create_case", buildJsonObject {
            put("p_class_id", classId); put("p_title", title); put("p_case_type", caseType); put("p_location", location)
            put("p_location_version", locationVersion); put("p_briefing", briefing); put("p_seed", seed); put("p_graded", graded)
            // Only sent with a victim, so cases without one still work against an older database.
            victim?.let { put("p_victim_sex", it.sex.key); put("p_victim_pose", it.pose.key) }
            put("p_manifest", buildJsonArray {
                manifest.forEach { m -> add(buildJsonObject { put("id", m.id); put("type", m.type) }) }
            })
        }, com.forensim.app.core.model.CaseSession.serializer())

    suspend fun setCaseActive(caseId: String, active: Boolean): com.forensim.app.core.model.CaseSession =
        client.rpc("professor_set_case_active", buildJsonObject { put("p_case_id", caseId); put("p_active", active) },
            com.forensim.app.core.model.CaseSession.serializer())

    /**
     * Asks the ai-quiz Edge Function for a draft quiz from a [topic] and/or text extracted from a PDF.
     * Nothing is saved: the draft goes into the quiz editor for the professor to review and publish.
     */
    suspend fun generateQuizDraft(topic: String?, sourceText: String?, count: Int, difficulty: String): GeneratedQuiz =
        client.invokeFunction("ai-quiz", buildJsonObject {
            topic?.takeIf { it.isNotBlank() }?.let { put("topic", it.trim()) }
            sourceText?.takeIf { it.isNotBlank() }?.let { put("source_text", it) }
            put("count", count); put("difficulty", difficulty)
        }, GeneratedQuiz.serializer())

    suspend fun setLessonPublished(lessonId: String, published: Boolean) {
        client.rpcRaw("professor_set_lesson_published", buildJsonObject { put("p_lesson_id", lessonId); put("p_published", published) })
    }

    suspend fun setQuizPublished(quizId: String, published: Boolean) {
        client.rpcRaw("professor_set_quiz_published", buildJsonObject { put("p_quiz_id", quizId); put("p_published", published) })
    }

    // Admin --------------------------------------------------------------------

    /** Creates a confirmed account with the chosen role (admin-users Edge Function, service role). */
    suspend fun adminCreateUser(email: String, password: String, fullName: String, role: Role) {
        client.invokeFunction("admin-users", buildJsonObject {
            put("action", "create"); put("email", email.trim()); put("password", password)
            put("full_name", fullName.trim()); put("role", role.name.lowercase())
        }, JsonElement.serializer())
    }

    suspend fun adminDeleteUser(userId: String) {
        client.invokeFunction("admin-users", buildJsonObject { put("action", "delete"); put("user_id", userId) }, JsonElement.serializer())
    }

    suspend fun adminUsers(): List<AdminUser> =
        client.rpc("admin_list_users", JsonObject(emptyMap()), ListSerializer(AdminUser.serializer()))

    suspend fun adminSetRole(userId: String, role: Role, status: String) {
        client.rpcRaw("admin_set_role", buildJsonObject {
            put("p_user_id", userId); put("p_role", role.name.lowercase()); put("p_status", status)
        })
    }

    suspend fun platformStats(): Map<String, Long> {
        val obj = client.rpc("admin_platform_stats", JsonObject(emptyMap()), JsonObject.serializer())
        return obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L }
    }

    suspend fun auditLog(): List<AuditEntry> =
        client.select("audit_logs?select=*&order=created_at.desc&limit=200", ListSerializer(AuditEntry.serializer()))

    suspend fun settings(): List<SystemSetting> =
        client.select("system_settings?select=key,value", ListSerializer(SystemSetting.serializer()))

    suspend fun updateSetting(key: String, value: JsonElement) {
        client.rpcRaw("admin_update_setting", buildJsonObject { put("p_key", key); put("p_value", value) })
    }

    companion object {
        val stringList = ListSerializer(String.serializer())
        const val PDF_BUCKET = "lesson-pdfs"
    }
}

/** Draft returned by the ai-quiz Edge Function. */
@kotlinx.serialization.Serializable
data class GeneratedQuiz(
    val title: String = "",
    val questions: List<GeneratedQuestion> = emptyList(),
    val model: String? = null,
)

@kotlinx.serialization.Serializable
data class GeneratedQuestion(
    val prompt: String,
    val options: List<String>,
    val correct: Int,
    val explanation: String = "",
)

/** One multiple-choice question in a professor's new quiz. [correct] is the 0-based option index. */
data class QuizDraftQuestion(
    val prompt: String,
    val options: List<String>,
    val correct: Int,
    val explanation: String = "",
    val points: Int = 1,
)
