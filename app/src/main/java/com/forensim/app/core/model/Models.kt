package com.forensim.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Wire models mirror the Supabase schema (supabase/migrations). Answer keys and rubrics have no
// client model at all: they never leave the server.

enum class Role { STUDENT, PROFESSOR, ADMIN;
    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
    companion object {
        fun from(raw: String?): Role? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

data class UserAccount(val id: String, val email: String, val fullName: String, val role: Role)

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val institution: String? = null,
    val gender: String? = null,
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
)

/** Values stored in profiles.gender, with their labels. */
enum class Gender(val key: String, val label: String) {
    FEMALE("female", "Female"), MALE("male", "Male"),
    PREFER_NOT_TO_SAY("prefer_not_to_say", "Prefer not to say");
    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } }
}

@Serializable
data class CourseClass(
    val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("professor_id") val professorId: String? = null,
    val name: String,
    val term: String? = null,
    @SerialName("join_code") val joinCode: String,
    @SerialName("join_open") val joinOpen: Boolean = true,
    /** The forensic subject (module) this class teaches. */
    @SerialName("module_id") val moduleId: String? = null,
    /** False: the code enrolls students at once ("anyone can join"). True: each join waits for approval. */
    @SerialName("require_approval") val requireApproval: Boolean = false,
    /** ClassColor key chosen by the professor (null = theme default); students see it too. */
    val color: String? = null,
    /** Cover photo in the "class-covers" bucket, or null for none. */
    @SerialName("cover_path") val coverPath: String? = null,
)

/** One of the seven subjects a professor can open a class for (public.list_subjects). */
@Serializable
data class Subject(
    @SerialName("module_id") val moduleId: String,
    val position: Int,
    val slug: String,
    val title: String,
    val summary: String = "",
    @SerialName("lesson_count") val lessonCount: Int = 0,
    /** The caller's class for this subject, if they already opened one. */
    @SerialName("class_id") val classId: String? = null,
)

/** A subject class the student joined (public.my_subject_classes). */
@Serializable
data class SubjectClass(
    @SerialName("class_id") val classId: String,
    @SerialName("module_id") val moduleId: String,
    val name: String,
    val term: String? = null,
    @SerialName("professor_name") val professorName: String = "",
    val position: Int = 0,
    val slug: String = "",
    val title: String = "",
    @SerialName("total_lessons") val totalLessons: Int = 0,
    @SerialName("unlocked_lessons") val unlockedLessons: Int = 0,
    @SerialName("quiz_count") val quizCount: Int = 0,
    /** "active", or "pending" while the professor has not approved the join request. */
    val status: String = "active",
    /** The professor's class color key and cover photo (see CourseClass). */
    val color: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    /** The class's join code (null until the server returns it, see 20261203000001). */
    @SerialName("join_code") val joinCode: String? = null,
) {
    val pending: Boolean get() = status == "pending"
}

@Serializable
data class LessonUnlock(@SerialName("class_id") val classId: String, @SerialName("lesson_id") val lessonId: String)

@Serializable
data class QuizUnlock(@SerialName("class_id") val classId: String, @SerialName("quiz_id") val quizId: String)

@Serializable
data class Course(
    val id: String,
    val title: String,
    val description: String = "",
    val institution: String? = null,
    val state: String = "active",
)

@Serializable
data class Enrollment(
    @SerialName("class_id") val classId: String,
    @SerialName("student_id") val studentId: String,
    val status: String,
    @SerialName("enrolled_at") val enrolledAt: String,
)

@Serializable
data class LearningModule(
    val id: String,
    @SerialName("course_id") val courseId: String,
    val position: Int,
    val slug: String,
    val title: String,
    val summary: String = "",
    val companion: String = "",
    val published: Boolean = false,
)

@Serializable
data class LessonSection(
    val heading: String,
    val body: String,
    /** Optional picture bundled in assets/lesson-media (e.g. "pid_patterns.webp"). */
    val image: String? = null,
    val caption: String? = null,
    val credit: String? = null,
    /** Optional YouTube video id, played inside the app. */
    val video: String? = null,
    @SerialName("video_title") val videoTitle: String? = null,
)

@Serializable
data class KeyTerm(val term: String, val definition: String)

@Serializable
data class Lesson(
    val id: String,
    @SerialName("module_id") val moduleId: String,
    val position: Int,
    val title: String,
    @SerialName("reading_minutes") val readingMinutes: Int = 5,
    val sections: List<LessonSection> = emptyList(),
    @SerialName("key_terms") val keyTerms: List<KeyTerm> = emptyList(),
    val citations: List<String> = emptyList(),
    val version: Int = 1,
    @SerialName("review_status") val reviewStatus: String = "pending_review",
    val published: Boolean = false,
    @SerialName("pdf_path") val pdfPath: String? = null,
) {
    val pendingReview: Boolean get() = reviewStatus != "approved"
    val isPdf: Boolean get() = pdfPath != null
}

@Serializable
data class LessonProgress(
    @SerialName("lesson_id") val lessonId: String,
    @SerialName("completed_at") val completedAt: String,
    @SerialName("student_id") val studentId: String? = null,
)

@Serializable
data class Quiz(
    val id: String,
    @SerialName("module_id") val moduleId: String,
    val title: String,
    val kind: String,
    @SerialName("time_limit_seconds") val timeLimitSeconds: Int? = null,
    @SerialName("max_attempts") val maxAttempts: Int? = null,
    @SerialName("available_from") val availableFrom: String? = null,
    @SerialName("available_until") val availableUntil: String? = null,
    @SerialName("review_policy") val reviewPolicy: String = "after_submit",
    val published: Boolean = false,
    /** Set when a professor created the quiz inside a class; null for shared subject quizzes. */
    @SerialName("class_id") val classId: String? = null,
) {
    val isGraded: Boolean get() = kind == "graded"
}

@Serializable
data class QuestionOption(val id: String, val position: Int, val body: String)

@Serializable
data class Question(
    val id: String,
    @SerialName("quiz_id") val quizId: String,
    val position: Int,
    val prompt: String,
    val objective: String = "",
    @SerialName("question_options") val options: List<QuestionOption> = emptyList(),
)

@Serializable
data class QuizAttempt(
    val id: String,
    @SerialName("quiz_id") val quizId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("attempt_number") val attemptNumber: Int,
    val status: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    val late: Boolean = false,
    val score: Double? = null,
    @SerialName("max_score") val maxScore: Double? = null,
)

@Serializable
data class QuizResponse(
    @SerialName("question_id") val questionId: String,
    @SerialName("option_id") val optionId: String,
)

@Serializable
data class SubmitResult(
    @SerialName("attempt_id") val attemptId: String,
    val status: String,
    val score: Double? = null,
    @SerialName("max_score") val maxScore: Double? = null,
    val late: Boolean = false,
    @SerialName("review_available") val reviewAvailable: Boolean = false,
)

@Serializable
data class ReviewItem(
    @SerialName("question_id") val questionId: String,
    val prompt: String,
    @SerialName("chosen_option_id") val chosenOptionId: String? = null,
    @SerialName("correct_option_id") val correctOptionId: String,
    val explanation: String = "",
)

@Serializable
data class PracticeFeedback(
    val correct: Boolean,
    @SerialName("correct_option_id") val correctOptionId: String,
    val explanation: String = "",
)

// ------------------------------------------------------------------- simulation

@Serializable
data class SimulationScenario(
    val id: String,
    val title: String,
    val summary: String = "",
    val kind: String,
    @SerialName("module_slug") val moduleSlug: String? = null,
    @SerialName("est_minutes") val estMinutes: Int = 10,
    val template: JsonObject,
    val version: Int = 1,
)

@Serializable
data class ManifestItem(val id: String, val type: String)

@Serializable
data class SimulationAttempt(
    val id: String,
    @SerialName("scenario_id") val scenarioId: String,
    @SerialName("scenario_version") val scenarioVersion: Int,
    @SerialName("student_id") val studentId: String? = null,
    val mode: String,
    val seed: Int,
    val manifest: List<ManifestItem>,
    val status: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    val score: Double? = null,
    @SerialName("max_score") val maxScore: Double? = null,
    val feedback: SimFeedback? = null,
    @SerialName("feedback_released") val feedbackReleased: Boolean = false,
    /** Set when the attempt is on a professor-generated case opened by code. */
    @SerialName("case_session_id") val caseSessionId: String? = null,
    /** 0-100: how far an unfinished case has got (saved when the student leaves the scene). */
    val progress: Int = 0,
    /** Saved event log of an unfinished case, so it can be resumed on any phone. */
    val checkpoint: List<SimEventDto>? = null,
)

/** An unfinished case's saved work, kept on the phone as well as on the server. */
@Serializable
data class CaseCheckpoint(val progress: Int, val events: List<SimEventDto>)

/** A professor case the student has opened (public.my_cases): their latest attempt on it. */
@Serializable
data class MyCase(
    @SerialName("case_id") val caseId: String,
    val code: String,
    val title: String,
    val location: String,
    val graded: Boolean = false,
    /** False once the professor closes the case: an unfinished attempt can no longer be resumed. */
    val active: Boolean = true,
    @SerialName("attempt_id") val attemptId: String,
    val status: String,
    val progress: Int = 0,
    val score: Double? = null,
    @SerialName("max_score") val maxScore: Double? = null,
    @SerialName("feedback_released") val feedbackReleased: Boolean = false,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
) {
    val finished: Boolean get() = status == "scored"
}

/** Scored answer to "what happened, and which evidence shows it" on a professor case. */
@Serializable
data class ConclusionFeedback(
    @SerialName("chosen_type") val chosenType: String? = null,
    @SerialName("correct_type") val correctType: String? = null,
    @SerialName("type_correct") val typeCorrect: Boolean = false,
    @SerialName("supporting_correct") val supportingCorrect: Int = 0,
    @SerialName("supporting_wrong") val supportingWrong: Int = 0,
    @SerialName("supporting_possible") val supportingPossible: Int = 0,
    /** Red herrings the student cited as proof (each costs a point). */
    @SerialName("red_herrings_cited") val redHerringsCited: Int = 0,
    val earned: Double = 0.0,
    val possible: Double = 0.0,
    val notes: List<String> = emptyList(),
)

/** A professor-generated case (professors only; students never receive case_type). */
@Serializable
data class CaseSession(
    val id: String,
    val code: String,
    @SerialName("class_id") val classId: String,
    val title: String,
    @SerialName("case_type") val caseType: String,
    val location: String,
    @SerialName("location_version") val locationVersion: Int = 1,
    val briefing: String = "",
    val seed: Int,
    val manifest: List<ManifestItem> = emptyList(),
    val graded: Boolean = false,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    /** "male" / "female", or null when the case has no victim's body in the scene. */
    @SerialName("victim_sex") val victimSex: String? = null,
    /** "face_up" / "face_down". */
    @SerialName("victim_pose") val victimPose: String? = null,
)

/** What a student learns when opening a case by code. */
@Serializable
data class CaseInfo(
    val id: String,
    val code: String,
    val title: String,
    val location: String,
    @SerialName("location_version") val locationVersion: Int = 1,
    val briefing: String,
    val graded: Boolean = false,
    @SerialName("victim_sex") val victimSex: String? = null,
    @SerialName("victim_pose") val victimPose: String? = null,
)

@Serializable
data class CaseStart(val attempt: SimulationAttempt, val case: CaseInfo)

@Serializable
data class TrainingRow(val part: Int)

/** A class chat member (public.class_chat_members): for showing their photo next to messages. */
@Serializable
data class ChatMember(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
)

/** One message in a class chat (public.class_messages). Name and role are stamped by the server. */
@Serializable
data class ClassMessage(
    val id: Long,
    @SerialName("class_id") val classId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String = "",
    @SerialName("sender_role") val senderRole: String = "student",
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class EvidenceFeedback(
    @SerialName("evidence_id") val evidenceId: String,
    val type: String,
    val earned: Double = 0.0,
    val possible: Double = 0.0,
    @SerialName("steps_completed") val stepsCompleted: List<String> = emptyList(),
    val issues: List<String> = emptyList(),
    val contaminated: Boolean = false,
    /** Not part of the case: processing it earns and costs nothing. */
    @SerialName("red_herring") val redHerring: Boolean = false,
)

@Serializable
data class SimFeedback(
    val score: Double = 0.0,
    @SerialName("max_score") val maxScore: Double = 0.0,
    val passed: Boolean = false,
    @SerialName("global_issues") val globalIssues: List<String> = emptyList(),
    val evidence: List<EvidenceFeedback> = emptyList(),
    val conclusion: ConclusionFeedback? = null,
)

@Serializable
data class SimSubmitResult(
    @SerialName("attempt_id") val attemptId: String,
    val status: String,
    val score: Double? = null,
    @SerialName("max_score") val maxScore: Double? = null,
    val passed: Boolean? = null,
    @SerialName("feedback_released") val feedbackReleased: Boolean = false,
    val feedback: SimFeedback? = null,
)

@Serializable
data class SimEventDto(
    val seq: Int,
    val action: String,
    @SerialName("evidence_id") val evidenceId: String? = null,
    val tool: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
    @SerialName("client_ts") val clientTs: String? = null,
)

// ------------------------------------------------------------------- tutor

@Serializable
data class TutorReference(
    @SerialName("lesson_id") val lessonId: String,
    val title: String,
    @SerialName("module_title") val moduleTitle: String = "",
)

@Serializable
data class TutorResponse(
    val blocked: Boolean = false,
    val reason: String? = null,
    val answer: String = "",
    val references: List<TutorReference> = emptyList(),
    @SerialName("retrieval_only") val retrievalOnly: Boolean = false,
)

// ------------------------------------------------------------------- staff

@Serializable
data class AdminUser(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val role: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AuditEntry(
    val id: Long,
    @SerialName("actor_id") val actorId: String? = null,
    val action: String,
    @SerialName("object_type") val objectType: String,
    @SerialName("object_id") val objectId: String? = null,
    val details: JsonElement? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SystemSetting(val key: String, val value: JsonElement)
