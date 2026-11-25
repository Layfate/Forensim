package com.forensim.app.feature.professor

import com.forensim.app.core.designsystem.NavMotion
import androidx.navigation.NavDestination.Companion.hierarchy

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import android.content.Intent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined._3dRotation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.forensim.app.AppContainer
import com.forensim.app.core.data.QuizDraftQuestion
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.DashboardStats
import com.forensim.app.core.designsystem.FeaturePanel
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.IconTile
import com.forensim.app.core.designsystem.Load
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.LocalReducedMotion
import com.forensim.app.core.designsystem.Motion
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.ProgressRow
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.WorkspaceDestination
import com.forensim.app.core.designsystem.WorkspaceList
import com.forensim.app.core.designsystem.WorkspaceScaffold
import com.forensim.app.core.designsystem.pct
import com.forensim.app.core.model.Course
import com.forensim.app.core.model.CourseClass
import com.forensim.app.core.model.Enrollment
import com.forensim.app.core.model.LearningModule
import com.forensim.app.core.model.Lesson
import com.forensim.app.core.model.LessonProgress
import com.forensim.app.core.model.Profile
import com.forensim.app.core.model.Quiz
import com.forensim.app.core.model.QuizAttempt
import com.forensim.app.core.model.SimulationAttempt
import com.forensim.app.core.model.UserAccount
import com.forensim.app.core.network.ApiException
import com.forensim.app.feature.ar.ArHubScreen
import com.forensim.app.feature.ar.EvidenceViewerScreen
import com.forensim.app.feature.lms.LessonScreen
import com.forensim.app.feature.settings.ControlSettingsScreen
import androidx.compose.material.icons.filled.ViewInAr
import com.forensim.app.feature.simulation.ScenarioListScreen
import com.forensim.app.feature.simulation.SimulationSessionScreen
import com.forensim.app.feature.student.ArRoute
import com.forensim.app.feature.student.ControlsRoute
import com.forensim.app.feature.student.EvidenceViewerRoute
import com.forensim.app.feature.student.ArCameraRoute
import com.forensim.app.feature.student.LessonRoute
import com.forensim.app.feature.student.SimSessionRoute
import com.forensim.app.feature.student.SimulateRoute
import com.forensim.app.feature.student.SettingsRoute
import com.forensim.app.feature.student.AccountRoute
import com.forensim.app.feature.settings.SettingsScreen
import com.forensim.app.feature.student.TutorRoute
import com.forensim.app.feature.student.navigateTopLevel
import com.forensim.app.feature.tutor.AssistantCard
import com.forensim.app.feature.tutor.TutorScreen
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// ----------------------------------------------------------------------------- data

data class TeachingData(
    val courses: List<Course>,
    val classes: List<CourseClass>,
    val roster: Map<String, List<Enrollment>>,
    val profiles: Map<String, Profile>,
    val modules: List<LearningModule>,
    val lessons: List<Lesson>,
    val quizzes: List<Quiz>,
    val quizAttempts: List<QuizAttempt>,
    val simAttempts: List<SimulationAttempt>,
    val progress: List<LessonProgress>,
    /** The seven subjects, with this professor's class for each (if opened). */
    val subjects: List<com.forensim.app.core.model.Subject> = emptyList(),
    val lessonUnlocks: Set<com.forensim.app.core.model.LessonUnlock> = emptySet(),
    val quizUnlocks: Set<com.forensim.app.core.model.QuizUnlock> = emptySet(),
    /** Built-in quizzes the professor removed from a class (same class/quiz pair shape as unlocks). */
    val hiddenQuizzes: Set<com.forensim.app.core.model.QuizUnlock> = emptySet(),
) {
    /** Approved students only; pending join requests are listed separately. */
    val students: List<String> get() = roster.values.flatten().filter { it.status == "active" }.map { it.studentId }.distinct()
    fun activeRoster(k: CourseClass) = roster[k.id].orEmpty().filter { it.status == "active" }
    fun pendingOf(k: CourseClass) = roster[k.id].orEmpty().filter { it.status == "pending" }
    fun name(id: String) = profiles[id]?.fullName?.ifBlank { null } ?: profiles[id]?.email ?: id.take(8)

    /** Subjects this professor teaches (one class each). */
    val ownModules: List<LearningModule> get() {
        val ids = classes.mapNotNull { it.moduleId }.toSet()
        return modules.filter { it.id in ids }.sortedBy { it.position }
    }

    fun classById(id: String) = classes.firstOrNull { it.id == id }
    fun moduleOf(k: CourseClass) = modules.firstOrNull { it.id == k.moduleId }
    fun lessonsOf(k: CourseClass) = lessons.filter { it.moduleId == k.moduleId && it.published }.sortedBy { it.position }
    fun isUnlocked(k: CourseClass, l: Lesson) = com.forensim.app.core.model.LessonUnlock(k.id, l.id) in lessonUnlocks
    /** Quizzes created in this class. */
    fun classQuizzes(k: CourseClass) = quizzes.filter { it.classId == k.id }
    /** Built-in quizzes of the subject, which the professor can unlock for the class. */
    fun sharedQuizzes(k: CourseClass) = allSharedQuizzes(k).filterNot { isQuizHidden(k, it) }
    /** Built-in quizzes removed from this class, which can be put back. */
    fun removedQuizzes(k: CourseClass) = allSharedQuizzes(k).filter { isQuizHidden(k, it) }
    private fun allSharedQuizzes(k: CourseClass) = quizzes.filter { it.classId == null && it.moduleId == k.moduleId }
    fun isQuizHidden(k: CourseClass, q: Quiz) = com.forensim.app.core.model.QuizUnlock(k.id, q.id) in hiddenQuizzes
    fun isQuizUnlocked(k: CourseClass, q: Quiz) = com.forensim.app.core.model.QuizUnlock(k.id, q.id) in quizUnlocks
    /** Classes sorted by subject number. */
    val sortedClasses: List<CourseClass> get() = classes.sortedBy { k -> moduleOf(k)?.position ?: 99 }
}

class ProfessorViewModel(private val c: AppContainer) : ViewModel() {
    var state by mutableStateOf<Load<TeachingData>>(Load.Loading); private set
    var message by mutableStateOf<String?>(null)
    var publishing by mutableStateOf(false); private set
    var publishError by mutableStateOf<String?>(null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            state = try {
                val classes = c.staff.classes()
                val roster = classes.associate { it.id to c.staff.enrollments(it.id) }
                val ids = roster.values.flatten().map { it.studentId }.toSet()
                val courses = async { c.staff.courses() }
                val profiles = async { c.staff.profiles(ids).associateBy { it.id } }
                val modules = async { c.learning.modules().value }
                val lessons = async { c.learning.lessons().value }
                val quizzes = async { c.learning.quizzes() }
                val qa = async { c.staff.quizAttempts() }
                val sa = async { c.staff.simulationAttempts() }
                val pr = async { c.staff.lessonProgress(ids) }
                val subjects = async { runCatching { c.staff.subjects() }.getOrDefault(emptyList()) }
                val classIds = classes.map { it.id }
                val lu = async { c.staff.lessonUnlocks(classIds).toSet() }
                val qu = async { c.staff.quizUnlocks(classIds).toSet() }
                val hq = async { c.staff.hiddenQuizzes(classIds).toSet() }
                Load.Ready(TeachingData(courses.await(), classes, roster, profiles.await(), modules.await().sortedBy { it.position },
                    lessons.await(), quizzes.await(), qa.await().filter { it.studentId in ids },
                    sa.await().filter { it.studentId in ids }, pr.await(), subjects.await(), lu.await(), qu.await(), hq.await()))
            } catch (e: ApiException) {
                Load.Error(e.message)
            }
        }
    }

    private fun act(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            message = try { block(); load(); success } catch (e: ApiException) { e.message }
        }
    }

    fun setPublished(m: LearningModule, on: Boolean) = act(if (on) "Published ${m.title}" else "Hid ${m.title}") { c.staff.setModulePublished(m.id, on) }
    fun setLessonPublished(l: Lesson, on: Boolean) = act(if (on) "\"${l.title}\" is visible to students" else "\"${l.title}\" is hidden") { c.staff.setLessonPublished(l.id, on) }
    fun setQuizPublished(q: Quiz, on: Boolean) = act(if (on) "\"${q.title}\" is open to students" else "\"${q.title}\" is hidden") { c.staff.setQuizPublished(q.id, on) }
    fun approve(l: Lesson) = act("Approved \"${l.title}\"") { c.staff.approveLesson(l.id) }
    /** Answer visibility switches the moment it is tapped; the server call runs behind and a failure switches it back. */
    fun setPolicy(q: Quiz, p: String) {
        val before = q.reviewPolicy
        if (before == p) return
        fun put(policy: String) = edit { d -> d.copy(quizzes = d.quizzes.map { if (it.id == q.id) it.copy(reviewPolicy = policy) else it }) }
        put(p)
        viewModelScope.launch {
            message = try {
                c.staff.setReviewPolicy(q.id, p)
                "Answer visibility updated"
            } catch (e: ApiException) {
                put(before)
                e.message
            }
        }
    }
    fun release(a: SimulationAttempt, on: Boolean) = act(if (on) "Feedback released" else "Feedback hidden") { c.staff.releaseSimulationFeedback(a.id, on) }
    // Class settings switches flip the moment they are tapped, like the lock/unlock switches below:
    // the class is updated in place, the server call runs behind, and a failure flips it back.
    fun setAnyoneCanJoin(k: CourseClass, on: Boolean) = classChange(k.id, { it.copy(requireApproval = !on) },
        if (on) "Anyone with the code can join" else "New students need your approval") { c.staff.setRequireApproval(k.id, !on) }

    private fun classChange(classId: String, change: (CourseClass) -> CourseClass, success: String, call: suspend () -> Unit) {
        val before = (state as? Load.Ready)?.data?.classes?.firstOrNull { it.id == classId } ?: return
        fun put(f: (CourseClass) -> CourseClass) = edit { d -> d.copy(classes = d.classes.map { if (it.id == classId) f(it) else it }) }
        put(change)
        viewModelScope.launch {
            message = try {
                call()
                success
            } catch (e: ApiException) {
                put { now -> change(before).let { wanted -> if (now == wanted) before else now } }
                e.message
            }
        }
    }
    fun reviewJoin(k: CourseClass, studentId: String?, approve: Boolean) = act(
        when {
            studentId == null && approve -> "All requests approved"
            studentId == null -> "All requests declined"
            approve -> "Student approved"
            else -> "Request declined"
        }) { c.staff.reviewJoin(k.id, studentId, approve) }
    fun removeStudent(k: CourseClass, studentId: String) {
        val name = (state as? Load.Ready)?.data?.name(studentId) ?: "Student"
        viewModelScope.launch {
            message = try {
                c.staff.removeStudent(k.id, studentId)
                load()
                "$name was removed from the class"
            } catch (e: ApiException) {
                if (e.code == "PGRST202" || e.status == 404) "Removing students needs a server update first." else e.message
            }
        }
    }

    // ---------------------------------------------------------------- class color and cover
    // Both live on the class row, so the student's class card shows them too.
    fun setClassColor(k: CourseClass, color: com.forensim.app.feature.classes.ClassColor) =
        classChange(k.id, { it.copy(color = color.key) }, "Class color updated") { c.staff.setClassColor(k.id, color.key) }

    /** Class whose cover photo is uploading or being removed. */
    var coverBusy by mutableStateOf<String?>(null); private set

    fun uploadClassCover(context: android.content.Context, k: CourseClass, uri: android.net.Uri) {
        if (coverBusy != null) return
        coverBusy = k.id
        viewModelScope.launch {
            message = try {
                val jpeg = com.forensim.app.feature.classes.ClassCovers.prepareCover(context, uri)
                val path = c.staff.uploadClassCover(k.id, jpeg, oldPath = currentClass(k.id)?.coverPath)
                com.forensim.app.feature.classes.ClassCovers.prime(context, path, jpeg)
                edit { d -> d.copy(classes = d.classes.map { if (it.id == k.id) it.copy(coverPath = path) else it }) }
                "Cover photo updated"
            } catch (e: ApiException) {
                e.message
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ClassCovers", "Cover upload failed", e)
                "Couldn't use that image. Try another photo."
            } finally {
                coverBusy = null
            }
        }
    }

    fun removeClassCover(k: CourseClass) {
        if (coverBusy != null) return
        coverBusy = k.id
        viewModelScope.launch {
            message = try {
                c.staff.removeClassCover(k.id, currentClass(k.id)?.coverPath)
                edit { d -> d.copy(classes = d.classes.map { if (it.id == k.id) it.copy(coverPath = null) else it }) }
                "Cover photo removed"
            } catch (e: ApiException) {
                e.message
            } finally {
                coverBusy = null
            }
        }
    }

    private fun currentClass(id: String) = (state as? Load.Ready)?.data?.classes?.firstOrNull { it.id == id }

    /**
     * Class colors used to be saved only on the professor's phone. Copies any such color to the
     * server once (when the class has none there yet), so students see it, then forgets it.
     */
    fun migrateLocalClassColors(context: android.content.Context) {
        val classes = (state as? Load.Ready)?.data?.classes ?: return
        viewModelScope.launch {
            val local = runCatching { com.forensim.app.feature.classes.LegacyClassColors.read(context) }.getOrDefault(emptyMap())
            for (k in classes) {
                val color = local[k.id] ?: continue
                try {
                    if (k.color == null && color.key != null) {
                        c.staff.setClassColor(k.id, color.key)
                        edit { d -> d.copy(classes = d.classes.map { if (it.id == k.id) it.copy(color = color.key) else it }) }
                    }
                    com.forensim.app.feature.classes.LegacyClassColors.forget(context, k.id)
                } catch (_: ApiException) {
                    // Offline or not allowed: keep the local color and try again next time.
                }
            }
        }
    }

    fun setJoinOpen(k: CourseClass, on: Boolean) = classChange(k.id, { it.copy(joinOpen = on) },
        if (on) "Students can join with the code" else "Joining is closed") { c.staff.setJoinOpen(k.id, on) }

    // ---------------------------------------------------------------- subject classes
    var creatingClass by mutableStateOf(false); private set
    var createClassError by mutableStateOf<String?>(null)

    fun createClass(subject: com.forensim.app.core.model.Subject, section: String, term: String, onCreated: (CourseClass) -> Unit) {
        if (creatingClass) return
        creatingClass = true; createClassError = null
        viewModelScope.launch {
            try {
                val k = c.staff.createClass(subject.moduleId, section, term)
                load(); onCreated(k)
            } catch (e: ApiException) {
                createClassError = e.message
            } finally {
                creatingClass = false
            }
        }
    }

    fun deleteClass(k: CourseClass, onDone: () -> Unit) = viewModelScope.launch {
        message = try { c.staff.deleteClass(k.id); load(); onDone(); "Deleted ${k.name}" } catch (e: ApiException) { e.message }
    }

    // ---------------------------------------------------------------- lock / unlock (optimistic)
    // Switches flip the moment they are tapped; the server call runs behind, then only the unlock
    // tables are re-read (not the whole dashboard). A failure puts the previous state back.

    /** Class whose "Unlock all / Lock all" is in flight (button shows progress, ignores repeat taps). */
    var bulkUnlocking by mutableStateOf<String?>(null); private set

    private fun edit(f: (TeachingData) -> TeachingData) {
        (state as? Load.Ready)?.let { state = it.copy(data = f(it.data)) }
    }

    private fun unlockChange(optimistic: (TeachingData) -> TeachingData, success: String, call: suspend () -> Unit, after: () -> Unit = {}) {
        val before = (state as? Load.Ready)?.data ?: return
        edit(optimistic)
        viewModelScope.launch {
            message = try {
                call()
                refreshUnlocks()
                success
            } catch (e: ApiException) {
                edit { it.copy(lessonUnlocks = before.lessonUnlocks, quizUnlocks = before.quizUnlocks) }
                e.message
            } finally {
                after()
            }
        }
    }

    private suspend fun refreshUnlocks() {
        val ids = (state as? Load.Ready)?.data?.classes?.map { it.id } ?: return
        val lu = c.staff.lessonUnlocks(ids).toSet()
        val qu = c.staff.quizUnlocks(ids).toSet()
        edit { it.copy(lessonUnlocks = lu, quizUnlocks = qu) }
    }

    fun setLessonUnlocked(k: CourseClass, l: Lesson, on: Boolean) {
        val key = com.forensim.app.core.model.LessonUnlock(k.id, l.id)
        unlockChange({ d -> d.copy(lessonUnlocks = if (on) d.lessonUnlocks + key else d.lessonUnlocks - key) },
            if (on) "\"${l.title}\" unlocked" else "\"${l.title}\" locked",
            { c.staff.setLessonUnlocked(k.id, l.id, on) })
    }

    fun setAllUnlocked(k: CourseClass, on: Boolean) {
        if (bulkUnlocking == k.id) return
        bulkUnlocking = k.id
        unlockChange({ d ->
            val others = d.lessonUnlocks.filterNot { it.classId == k.id }.toSet()
            d.copy(lessonUnlocks = if (on) others + d.lessonsOf(k).map { com.forensim.app.core.model.LessonUnlock(k.id, it.id) } else others)
        }, if (on) "All lessons unlocked" else "All lessons locked",
            { c.staff.setAllLessonsUnlocked(k.id, on) }, after = { bulkUnlocking = null })
    }

    fun setQuizUnlocked(k: CourseClass, q: Quiz, on: Boolean) {
        val key = com.forensim.app.core.model.QuizUnlock(k.id, q.id)
        unlockChange({ d -> d.copy(quizUnlocks = if (on) d.quizUnlocks + key else d.quizUnlocks - key) },
            if (on) "\"${q.title}\" unlocked" else "\"${q.title}\" locked",
            { c.staff.setQuizUnlocked(k.id, q.id, on) })
    }

    // ---------------------------------------------------------------- deleting quizzes (optimistic)
    // The quiz leaves the list at once; a failure (or a server without the delete-quizzes
    // migration) puts it back.

    private fun quizRemoval(optimistic: (TeachingData) -> TeachingData, success: String, call: suspend () -> Unit) {
        val before = (state as? Load.Ready)?.data ?: return
        edit(optimistic)
        viewModelScope.launch {
            message = try {
                call()
                success
            } catch (e: ApiException) {
                edit { it.copy(quizzes = before.quizzes, quizUnlocks = before.quizUnlocks, hiddenQuizzes = before.hiddenQuizzes) }
                if (e.code == "PGRST202" || e.status == 404) "Deleting quizzes needs a server update first." else e.message
            }
        }
    }

    /** Deletes a quiz the professor made in [k], with its questions and results. */
    fun deleteQuiz(q: Quiz) = quizRemoval({ d -> d.copy(quizzes = d.quizzes.filterNot { it.id == q.id }) },
        "Deleted \"${q.title}\"") { c.staff.deleteQuiz(q.id) }

    /** Removes a built-in quiz from [k] only (it stays in other classes), or puts it back. */
    fun setQuizHidden(k: CourseClass, q: Quiz, hidden: Boolean) {
        val key = com.forensim.app.core.model.QuizUnlock(k.id, q.id)
        quizRemoval({ d ->
            d.copy(hiddenQuizzes = if (hidden) d.hiddenQuizzes + key else d.hiddenQuizzes - key,
                quizUnlocks = if (hidden) d.quizUnlocks - key else d.quizUnlocks)
        }, if (hidden) "Removed \"${q.title}\" from ${k.name}" else "\"${q.title}\" is back (locked)") {
            c.staff.setQuizHidden(k.id, q.id, hidden)
        }
    }

    fun uploadPdf(k: CourseClass, title: String, minutes: Int, pdf: ByteArray, onDone: () -> Unit) {
        if (publishing) return
        publishing = true; publishError = null
        viewModelScope.launch {
            try {
                val moduleId = k.moduleId ?: throw ApiException(400, "This class has no subject.")
                c.staff.uploadPdfLesson(moduleId, title.trim(), minutes, pdf, classId = k.id)
                message = "\"${title.trim()}\" is unlocked for ${k.name}"
                load(); onDone()
            } catch (e: ApiException) {
                publishError = e.message
            } finally {
                publishing = false
            }
        }
    }

    // ---------------------------------------------------------------- AI quiz drafts
    var generating by mutableStateOf(false); private set
    var generateError by mutableStateOf<String?>(null)
    /** Questions drafted on the AI page, waiting to be added to the quiz on Create quiz. */
    var aiDraft by mutableStateOf<com.forensim.app.core.data.GeneratedQuiz?>(null)

    /** Drafts questions with AI from a topic and/or extracted PDF text; [onResult] fills the editor. */
    fun generateQuiz(topic: String?, sourceText: String?, count: Int, difficulty: String,
                     onResult: (com.forensim.app.core.data.GeneratedQuiz) -> Unit) {
        if (generating) return
        generating = true; generateError = null
        viewModelScope.launch {
            try {
                val draft = c.staff.generateQuizDraft(topic, sourceText, count, difficulty)
                if (draft.questions.isEmpty()) generateError = "The AI didn't return any usable questions. Try again."
                else onResult(draft)
            } catch (e: ApiException) {
                generateError = e.message
            } finally {
                generating = false
            }
        }
    }

    fun createQuiz(k: CourseClass, title: String, graded: Boolean, minutes: Int?, attempts: Int?, questions: List<QuizDraftQuestion>, onDone: () -> Unit) {
        if (publishing) return
        publishing = true; publishError = null
        viewModelScope.launch {
            try {
                val moduleId = k.moduleId ?: throw ApiException(400, "This class has no subject.")
                c.staff.createQuiz(moduleId, title.trim(), graded, minutes, attempts, questions, classId = k.id)
                message = "Quiz \"${title.trim()}\" is open in ${k.name}"
                load(); onDone()
            } catch (e: ApiException) {
                publishError = e.message
            } finally {
                publishing = false
            }
        }
    }
}

// ----------------------------------------------------------------------------- navigation

@Serializable data object ProfHomeRoute
@Serializable data object ProfClassesRoute
@Serializable data object ProfProfileRoute
@Serializable data class ProfClassRoute(val classId: String)
@Serializable data class ProfQuizRoute(val quizId: String)
@Serializable data class UploadPdfRoute(val classId: String? = null)
@Serializable data class CreateQuizRoute(val classId: String? = null)
@Serializable data class AiQuizRoute(val classId: String? = null)
@Serializable data object CaseBuilderRoute
@Serializable data class CaseDetailRoute(val caseId: String)

private data class ProfTab(
    @androidx.annotation.StringRes val label: Int,
    val route: Any,
    val selected: ImageVector,
    val unselected: ImageVector,
    val prominent: Boolean = false,
    val matches: (NavDestination) -> Boolean,
)

private val profTabs = listOf(
    ProfTab(R.string.nav_home, ProfHomeRoute, Icons.Filled.Home, Icons.Outlined.Home) { it.hasRoute(ProfHomeRoute::class) },
    ProfTab(R.string.nav_classes, ProfClassesRoute, Icons.Filled.Groups, Icons.Outlined.Groups) { it.hasRoute(ProfClassesRoute::class) },
    ProfTab(R.string.nav_simulate, SimulateRoute, Icons.Filled._3dRotation, Icons.Filled._3dRotation, prominent = true) { it.hasRoute(SimulateRoute::class) },
    ProfTab(R.string.nav_ar, ArRoute, Icons.Filled.ViewInAr, Icons.Outlined.ViewInAr) { it.hasRoute(ArRoute::class) },
    ProfTab(R.string.nav_profile, ProfProfileRoute, Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle) { it.hasRoute(ProfProfileRoute::class) },
)

@Composable
fun ProfessorApp(user: UserAccount) {
    // Activity-scoped so Home, Classes and the publishing screens share one data set.
    val vm = appViewModel { ProfessorViewModel(it) }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = destination != null && profTabs.any { tab -> destination.hierarchy.any(tab.matches) }
    val reducedMotion = LocalReducedMotion.current
    WorkspaceScaffold(
        destinations = profTabs.map { tab ->
            val selected = destination?.hierarchy?.any(tab.matches) == true
            val label = stringResource(tab.label)
            WorkspaceDestination(
                label, if (selected) tab.selected else tab.unselected, selected,
                compactLabel = if (tab.route == SimulateRoute) stringResource(R.string.nav_lab) else label,
                prominent = tab.prominent,
            ) { nav.navigateTopLevel(tab.route) }
        },
        showNavigation = showBar,
    ) {
        NavHost(
            nav,
            startDestination = ProfHomeRoute,
            // Shared-axis slide for push/pop, fade-through between tabs (see NavMotion).
            enterTransition = { if (reducedMotion || initialState.destination.hasSurfaceView() || targetState.destination.hasSurfaceView()) EnterTransition.None else NavMotion.enter(profTabs.any { t -> initialState.destination.hierarchy.any(t.matches) } && profTabs.any { t -> targetState.destination.hierarchy.any(t.matches) }) },
            exitTransition = { if (reducedMotion || initialState.destination.hasSurfaceView() || targetState.destination.hasSurfaceView()) ExitTransition.None else NavMotion.exit(profTabs.any { t -> initialState.destination.hierarchy.any(t.matches) } && profTabs.any { t -> targetState.destination.hierarchy.any(t.matches) }) },
            popEnterTransition = { if (reducedMotion || initialState.destination.hasSurfaceView() || targetState.destination.hasSurfaceView()) EnterTransition.None else NavMotion.popEnter(profTabs.any { t -> initialState.destination.hierarchy.any(t.matches) } && profTabs.any { t -> targetState.destination.hierarchy.any(t.matches) }) },
            popExitTransition = { if (reducedMotion || initialState.destination.hasSurfaceView() || targetState.destination.hasSurfaceView()) ExitTransition.None else NavMotion.popExit(profTabs.any { t -> initialState.destination.hierarchy.any(t.matches) } && profTabs.any { t -> targetState.destination.hierarchy.any(t.matches) }) },
        ) {
            composable<ProfHomeRoute> {
                ProfessorHome(
                    user, vm,
                    openTutor = { nav.navigate(TutorRoute()) },
                    openUpload = { vm.publishError = null; nav.navigate(UploadPdfRoute()) },
                    openQuizBuilder = { vm.publishError = null; nav.navigate(CreateQuizRoute()) },
                    openClasses = { nav.navigateTopLevel(ProfClassesRoute) },
                    openProfile = { nav.navigateTopLevel(ProfProfileRoute) },
                    openCaseBuilder = { nav.navigate(CaseBuilderRoute) },
                )
            }
            composable<ProfClassesRoute> { ProfessorClasses(vm, openClass = { nav.navigate(ProfClassRoute(it)) }) }
            composable<ProfClassRoute> { back ->
                ProfessorClassDetail(
                    vm, back.toRoute<ProfClassRoute>().classId,
                    onBack = { nav.popBackStack() },
                    openLesson = { nav.navigate(LessonRoute(it)) },
                    openQuizBuilder = { id -> vm.publishError = null; nav.navigate(CreateQuizRoute(id)) },
                    openQuiz = { nav.navigate(ProfQuizRoute(it)) },
                    openUpload = { id -> vm.publishError = null; nav.navigate(UploadPdfRoute(id)) },
                    openChat = { id, title -> nav.navigate(com.forensim.app.feature.student.ClassChatRoute(id, title)) },
                )
            }
            composable<ProfQuizRoute> { back ->
                ProfessorQuizDetail(vm, back.toRoute<ProfQuizRoute>().quizId, onBack = { nav.popBackStack() })
            }
            composable<com.forensim.app.feature.student.ClassChatRoute> { back ->
                val r = back.toRoute<com.forensim.app.feature.student.ClassChatRoute>()
                com.forensim.app.feature.classes.ClassChatScreen(r.classId, r.title, canModerate = true, onBack = { nav.popBackStack() })
            }
            composable<ProfProfileRoute> {
                ProfessorProfileHub(
                    user = user,
                    vm = vm,
                    openClasses = { nav.navigateTopLevel(ProfClassesRoute) },
                    openAssistant = { nav.navigate(TutorRoute()) },
                    openSettings = { nav.navigate(SettingsRoute) },
                    openControls = { nav.navigate(ControlsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(user, onBack = { nav.popBackStack() }, openAccount = { nav.navigate(AccountRoute) },
                    openControls = { nav.navigate(ControlsRoute) })
            }
            composable<AccountRoute> { com.forensim.app.feature.settings.AccountScreen(user, onBack = { nav.popBackStack() }) }

            composable<UploadPdfRoute> { back -> UploadPdfScreen(vm, back.toRoute<UploadPdfRoute>().classId, onBack = { nav.popBackStack() }) }
            composable<CreateQuizRoute> { back ->
                CreateQuizScreen(vm, back.toRoute<CreateQuizRoute>().classId, onBack = { nav.popBackStack() },
                    openAiGenerator = { id -> vm.generateError = null; nav.navigate(AiQuizRoute(id)) })
            }
            composable<AiQuizRoute> { back -> AiQuizScreen(vm, back.toRoute<AiQuizRoute>().classId, onBack = { nav.popBackStack() }) }
            composable<TutorRoute> { back ->
                TutorScreen(back.toRoute<TutorRoute>().moduleTitle, openLesson = { nav.navigate(LessonRoute(it)) }, onBack = { nav.popBackStack() })
            }
            composable<LessonRoute> { back ->
                LessonScreen(back.toRoute<LessonRoute>().lessonId, onBack = { nav.popBackStack() },
                    openTutor = { title -> nav.navigate(TutorRoute(title)) })
            }
            composable<SimulateRoute> {
                ScenarioListScreen(openScenario = { nav.navigate(SimSessionRoute(it)) }, openControls = { nav.navigate(ControlsRoute) },
                    professor = true, openCaseBuilder = { nav.navigate(CaseBuilderRoute) }, openCaseDetail = { nav.navigate(CaseDetailRoute(it)) })
            }
            composable<SimSessionRoute> { back ->
                SimulationSessionScreen(back.toRoute<SimSessionRoute>().scenarioId, user, onExit = { nav.popBackStack() },
                    onOpen = { next -> nav.navigate(SimSessionRoute(next)) { popUpTo<SimSessionRoute> { inclusive = true } } })
            }
            composable<CaseBuilderRoute> {
                CaseBuilderScreen(vm, onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate(CaseDetailRoute(id)) { popUpTo<CaseBuilderRoute> { inclusive = true } } })
            }
            composable<CaseDetailRoute> { back -> CaseDetailScreen(vm, back.toRoute<CaseDetailRoute>().caseId, onBack = { nav.popBackStack() }) }
            composable<ArRoute> { ArHubScreen(openViewer = { nav.navigate(EvidenceViewerRoute(it)) }, openCamera = { nav.navigate(ArCameraRoute(it)) }) }
            composable<EvidenceViewerRoute> { back ->
                val type = back.toRoute<EvidenceViewerRoute>().type
                EvidenceViewerScreen(type, onBack = { nav.popBackStack() }, openAr = { nav.navigate(ArCameraRoute(type)) })
            }
            composable<ArCameraRoute> { back ->
                com.forensim.app.feature.ar.ArCameraScreen(back.toRoute<ArCameraRoute>().type, onBack = { nav.popBackStack() })
            }
            composable<ControlsRoute> { ControlSettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavDestination.hasSurfaceView() = hasRoute(SimSessionRoute::class) || hasRoute(EvidenceViewerRoute::class) || hasRoute(ArCameraRoute::class)

// ----------------------------------------------------------------------------- Home

@Composable
private fun ProfessorHome(
    user: UserAccount,
    vm: ProfessorViewModel,
    openTutor: () -> Unit,
    openUpload: () -> Unit,
    openQuizBuilder: () -> Unit,
    openClasses: () -> Unit,
    openProfile: () -> Unit,
    openCaseBuilder: () -> Unit,
) {
    ScreenScaffold(title = "Forensim", header = {
        val photo by com.forensim.app.ui.LocalAppContainer.current.profile.avatar.collectAsStateWithLifecycle()
        com.forensim.app.core.designsystem.HomeHeader(
            greeting = com.forensim.app.core.designsystem.headerGreeting(user.fullName),
            title = stringResource(R.string.welcome_back),
            profileName = user.fullName.ifBlank { user.email },
            onProfile = openProfile,
            photo = photo,
            actions = { ExportButton(vm) },
        )
    }) { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
            WorkspaceList(Modifier.padding(inner)) {
                vm.message?.let { m -> item("feedback") { Banner(m, Tone.PRIMARY) } }
                item("assistant") { AssistantCard(onClick = openTutor, subtitle = stringResource(R.string.assistant_prof_sub)) }
                item("publish") {
                    SectionHeader(stringResource(R.string.publish))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PublishTile(stringResource(R.string.upload_pdf), Icons.Outlined.PictureAsPdf, openUpload, Modifier.weight(1f))
                        PublishTile(stringResource(R.string.new_quiz), Icons.Outlined.Quiz, openQuizBuilder, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    // Full width: a third tile in the row above would squeeze every label.
                    PublishTile("Generate a case", Icons.Outlined.QrCode2, openCaseBuilder, Modifier.fillMaxWidth(),
                        subtitle = "A crime scene students open with a code or QR")
                    if (d.classes.isEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        ForensimCard(onClick = openClasses) {
                            Text("Open your first subject class", style = MaterialTheme.typography.titleMedium)
                            Text("Choose a subject in Classes. You get a code to give your students, then you unlock lessons and add quizzes.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item("stats") {
                    val submitted = d.quizAttempts.filter { it.status == "submitted" }
                    val scored = d.simAttempts.filter { it.status == "scored" }
                    SectionHeader(stringResource(R.string.at_a_glance))
                    DashboardStats(
                        stringResource(R.string.students) to d.students.size.toString(),
                        stringResource(R.string.quizzes) to avgPct(submitted.map { it.score to it.maxScore }),
                        stringResource(R.string.cases) to avgPct(scored.map { it.score to it.maxScore }),
                    )
                }
                // Subject classes live in the Classes tab only, not on the home page.
                insights(d)
            }
        }
    }
}

@Composable
private fun PublishTile(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, subtitle: String? = null) {
    ForensimCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconTile(icon, size = 36.dp)
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


private fun LazyListScope.insights(d: TeachingData) {
    val scored = d.simAttempts.filter { it.status == "scored" }
    if (d.students.isEmpty()) return
    item("completion-header") { SectionHeader("Lesson completion") }
    items(d.ownModules, key = { "completion-" + it.id }) { m ->
        val ls = d.lessons.filter { it.moduleId == m.id }.map { it.id }.toSet()
        val possible = ls.size * d.students.size
        val done = d.progress.count { it.lessonId in ls }
        ProgressRow("${m.position}. ${m.title}", if (possible == 0) 0f else done / possible.toFloat(),
            if (possible == 0) "-" else "${done * 100 / possible}%")
    }
    val issues = scored.flatMap { a -> a.feedback?.let { it.globalIssues + it.evidence.flatMap { e -> e.issues } }.orEmpty() }
        .map { it.replace(Regex("\\(.*?\\)"), "").trim() }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    if (issues.isNotEmpty()) {
        item("issues-header") { SectionHeader("Most common procedure mistakes") }
        items(issues, key = { "issue-" + it.key }) { (text, count) ->
            ForensimCard { Row { Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text("$count×", style = MaterialTheme.typography.titleSmall) } }
        }
    }
}

// ----------------------------------------------------------------------------- Classes

@Composable
internal fun SimFeedbackCard(a: SimulationAttempt, d: TeachingData, vm: ProfessorViewModel) {
    ForensimCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name(a.studentId ?: ""), style = MaterialTheme.typography.titleSmall)
                Text("${a.scenarioId.replace('_', ' ')} · ${a.score?.pct(a.maxScore) ?: "-"}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(if (a.feedbackReleased) "Released" else "Held", if (a.feedbackReleased) Tone.SUCCESS else Tone.NEUTRAL,
                Modifier.padding(end = Spacing.sm))
            Switch(checked = a.feedbackReleased, onCheckedChange = { vm.release(a, it) },
                modifier = Modifier.semantics { contentDescription = "Release case feedback" })
        }
        a.feedback?.let { fb ->
            (fb.globalIssues + fb.evidence.flatMap { e -> e.issues.map { "${e.type}: $it" } }).take(4).forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ----------------------------------------------------------------------------- Lab

// ----------------------------------------------------------------------------- shared

internal fun avgPct(list: List<Pair<Double?, Double?>>): String {
    val valid = list.filter { it.first != null && (it.second ?: 0.0) > 0 }
    if (valid.isEmpty()) return "-"
    return valid.sumOf { it.first!! }.pct(valid.sumOf { it.second!! })
}

@Composable
private fun ExportButton(vm: ProfessorViewModel) {
    val context = LocalContext.current
    val data = (vm.state as? Load.Ready)?.data ?: return
    IconButton(onClick = {
        val csv = buildString {
            appendLine("student,lessons_completed,graded_quiz_score,graded_quiz_max,simulation_score,simulation_max")
            data.students.forEach { s ->
                val q = data.quizAttempts.filter { it.studentId == s && it.status == "submitted" }
                val sim = data.simAttempts.filter { it.studentId == s && it.status == "scored" }
                appendLine(listOf(
                    "\"" + data.name(s).replace("\"", "\"\"") + "\"",
                    data.progress.count { it.studentId == s },
                    q.sumOf { it.score ?: 0.0 }, q.sumOf { it.maxScore ?: 0.0 },
                    sim.sumOf { it.score ?: 0.0 }, sim.sumOf { it.maxScore ?: 0.0 },
                ).joinToString(","))
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Forensim class report")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        context.startActivity(Intent.createChooser(send, "Export class report"))
    }) { Icon(Icons.Outlined.IosShare, contentDescription = "Export class report as CSV") }
}
