package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UserRole {
    ADMIN, TEACHER, STUDENT;

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STUDENT
    }
}

enum class QuestionType(val label: String) {
    MCQ("Multiple Choice (Single)"),
    MULTI_SELECT("Multiple Select"),
    TRUE_FALSE("True / False"),
    FILL_BLANK("Fill in the Blank"),
    SHORT_ANSWER("Short Answer");

    companion object {
        fun fromRaw(raw: String?): QuestionType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MCQ
    }
}

enum class DifficultyLevel(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard");

    companion object {
        fun fromRaw(raw: String?): DifficultyLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

enum class ExamStatus(val label: String) {
    DRAFT("Draft"),
    PUBLISHED("Published"),
    ARCHIVED("Archived");

    companion object {
        fun fromRaw(raw: String?): ExamStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DRAFT
    }
}

enum class AssignedScope(val label: String) {
    ALL("All Students"),
    BATCH("Specific Batch"),
    STUDENTS("Selected Students");

    companion object {
        fun fromRaw(raw: String?): AssignedScope =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ALL
    }
}

enum class AttemptStatus {
    IN_PROGRESS, SUBMITTED;

    companion object {
        fun fromRaw(raw: String?): AttemptStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: IN_PROGRESS
    }
}

enum class GradingStatus(val label: String) {
    AUTO_GRADED("Auto-Graded"),
    PENDING_REVIEW("Pending Teacher Review"),
    FINALIZED("Finalized");

    companion object {
        fun fromRaw(raw: String?): GradingStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FINALIZED
    }
}

enum class AnswerStatus(val label: String) {
    CORRECT("Correct"),
    WRONG("Wrong"),
    UNATTEMPTED("Unattempted"),
    PENDING("Pending Review");

    companion object {
        fun fromRaw(raw: String?): AnswerStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNATTEMPTED
    }
}

enum class NotificationCategory(val label: String) {
    EXAM_ASSIGNED("Exam Assigned"),
    RESULT_PUBLISHED("Result Published"),
    EXAM_SUBMITTED("Exam Submitted"),
    SYSTEM("Announcement");

    companion object {
        fun fromRaw(raw: String?): NotificationCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = UserRole.STUDENT.name,
    val instituteCode: String? = null,
    val linkedProfileId: String? = null,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): UserRole = UserRole.fromRaw(role)

    fun toCreateMap(): Map<String, Any> = buildMap {
        put("uid", uid)
        put("email", email)
        put("displayName", displayName.ifBlank { "Member" }.take(120))
        photoUrl?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.take(600)) }
        put("role", role)
        instituteCode?.takeIf { it.isNotBlank() }?.let { put("instituteCode", it.take(64)) }
        linkedProfileId?.takeIf { it.isNotBlank() }?.let { put("linkedProfileId", it.take(128)) }
        put("active", active)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class PortalClaim(
    @DocumentId val id: String = "",
    val code: String = "",
    val role: String = UserRole.STUDENT.name,
    val profileId: String = "",
    val passcode: String = "",
    val claimedByUid: String? = null,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TeacherProfile(
    @DocumentId val id: String = "",
    val teacherCode: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val subjectSpecialty: String = "",
    val passcode: String = "",
    val linkedUid: String = "",
    val active: Boolean = true,
    val demoRecord: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudentProfile(
    @DocumentId val id: String = "",
    val studentCode: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val course: String = "",
    val batch: String = "",
    val assignedTeacherId: String = "",
    val assignedTeacherUid: String = "",
    val assignedTeacherName: String = "",
    val passcode: String = "",
    val linkedUid: String = "",
    val active: Boolean = true,
    val demoRecord: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PdfPaper(
    @DocumentId val id: String = "",
    val title: String = "",
    val fileName: String = "",
    val subject: String = "",
    val chapter: String = "",
    val topic: String = "",
    val totalExtracted: Int = 0,
    val approvedCount: Int = 0,
    val sourceType: String = "UPLOADED_PDF",
    val uploadedByUid: String = "",
    val status: String = "APPROVED",
    val demoRecord: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BankQuestion(
    @DocumentId val id: String = "",
    val questionText: String = "",
    val questionType: String = QuestionType.MCQ.name,
    val options: List<String> = emptyList(),
    val correctAnswers: List<String> = emptyList(),
    val explanation: String = "",
    val subject: String = "Computer Fundamentals",
    val chapter: String = "",
    val topic: String = "",
    val difficulty: String = DifficultyLevel.MEDIUM.name,
    val marks: Double = 2.0,
    val sourcePaperId: String = "",
    val answerApproved: Boolean = true,
    val createdByUid: String = "",
    val demoRecord: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun typeEnum(): QuestionType = QuestionType.fromRaw(questionType)
    fun difficultyEnum(): DifficultyLevel = DifficultyLevel.fromRaw(difficulty)
}

@Keep
@IgnoreExtraProperties
data class SanitizedExamQuestion(
    val questionId: String = "",
    val text: String = "",
    val questionType: String = QuestionType.MCQ.name,
    val options: List<String> = emptyList(),
    val marks: Double = 2.0,
    val orderIndex: Int = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "questionId" to questionId,
        "text" to text,
        "questionType" to questionType,
        "options" to options,
        "marks" to marks,
        "orderIndex" to orderIndex
    )
}

@Keep
@IgnoreExtraProperties
data class Examination(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val subject: String = "",
    val batch: String = "",
    val status: String = ExamStatus.PUBLISHED.name,
    val durationMinutes: Int = 30,
    val totalMarks: Double = 0.0,
    val marksPerQuestion: Double = 2.0,
    val negativeMarks: Double = 0.0,
    val passingPercentage: Double = 40.0,
    val scheduledAtMillis: Long = 0L,
    val deadlineAtMillis: Long = 0L,
    val randomizeQuestions: Boolean = false,
    val randomizeOptions: Boolean = false,
    val showResultImmediately: Boolean = true,
    val allowAnswerReview: Boolean = true,
    val assignedScope: String = AssignedScope.ALL.name,
    val assignedStudentIds: List<String> = emptyList(),
    val assignedStudentUids: List<String> = emptyList(),
    val assignedTeacherUid: String = "",
    val createdByUid: String = "",
    val questionCount: Int = 0,
    val questions: List<SanitizedExamQuestion> = emptyList(),
    val demoRecord: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): ExamStatus = ExamStatus.fromRaw(status)
    fun scopeEnum(): AssignedScope = AssignedScope.fromRaw(assignedScope)
}

@Keep
@IgnoreExtraProperties
data class ExamAnswerKey(
    @DocumentId val id: String = "",
    val examId: String = "",
    val answers: Map<String, String> = emptyMap(),
    val explanations: Map<String, String> = emptyMap(),
    val marksPerQuestion: Double = 2.0,
    val negativeMarks: Double = 0.0,
    val passingPercentage: Double = 40.0,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExamAttempt(
    @DocumentId val id: String = "",
    val examId: String = "",
    val examTitle: String = "",
    val studentUid: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val assignedTeacherUid: String = "",
    val status: String = AttemptStatus.IN_PROGRESS.name,
    val responses: Map<String, String> = emptyMap(),
    val flaggedQuestionIds: List<String> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val startedAtMillis: Long = 0L,
    val submittedAtMillis: Long? = null,
    val timeTakenSeconds: Int = 0,
    val demoRecord: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): AttemptStatus = AttemptStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class QuestionAnalysis(
    val questionId: String = "",
    val questionText: String = "",
    val questionType: String = QuestionType.MCQ.name,
    val options: List<String> = emptyList(),
    val studentAnswer: String = "",
    val correctAnswer: String = "",
    val status: String = AnswerStatus.UNATTEMPTED.name,
    val marksObtained: Double = 0.0,
    val maxMarks: Double = 2.0,
    val needsManualReview: Boolean = false
) {
    fun statusEnum(): AnswerStatus = AnswerStatus.fromRaw(status)

    fun toMap(): Map<String, Any> = mapOf(
        "questionId" to questionId,
        "questionText" to questionText,
        "questionType" to questionType,
        "options" to options,
        "studentAnswer" to studentAnswer,
        "correctAnswer" to correctAnswer,
        "status" to status,
        "marksObtained" to marksObtained,
        "maxMarks" to maxMarks,
        "needsManualReview" to needsManualReview
    )
}

@Keep
@IgnoreExtraProperties
data class ExamResult(
    @DocumentId val id: String = "",
    val examId: String = "",
    val examTitle: String = "",
    val subject: String = "",
    val batch: String = "",
    val studentUid: String = "",
    val studentId: String = "",
    val studentCode: String = "",
    val studentName: String = "",
    val assignedTeacherId: String = "",
    val assignedTeacherUid: String = "",
    val assignedTeacherName: String = "",
    val totalQuestions: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val unattemptedCount: Int = 0,
    val pendingManualCount: Int = 0,
    val totalMarks: Double = 0.0,
    val obtainedMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val passed: Boolean = false,
    val timeTakenSeconds: Int = 0,
    val resultsVisibleToStudent: Boolean = true,
    val allowAnswerReview: Boolean = true,
    val gradingStatus: String = GradingStatus.FINALIZED.name,
    val questionAnalyses: List<QuestionAnalysis> = emptyList(),
    val demoRecord: Boolean = false,
    @ServerTimestamp val submittedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun gradingStatusEnum(): GradingStatus = GradingStatus.fromRaw(gradingStatus)
}

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val senderUid: String = "",
    val title: String = "",
    val message: String = "",
    val category: String = NotificationCategory.SYSTEM.name,
    val relatedExamId: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun categoryEnum(): NotificationCategory = NotificationCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val actorRole: String = UserRole.ADMIN.name,
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

// Local Bundled PDF Question Sets (42 institute PDFs = 1,237 MCQs)
data class BundledQuestionItem(
    val number: Int,
    val text: String,
    val options: List<String>,
    val correctOption: String,
    val subject: String,
    val difficulty: String
)

data class BundledQuestionSet(
    val setId: String,
    val setNumber: Int,
    val title: String,
    val rangeLabel: String,
    val sourceFileName: String,
    val primarySubject: String,
    val questionCount: Int,
    val questions: List<BundledQuestionItem>
)

// AI-Extracted PDF Question Draft (awaiting Admin Answer Key verification/approval)
data class ExtractedDraftQuestion(
    val tempId: String,
    val questionText: String,
    val questionType: QuestionType,
    val options: List<String>,
    val correctAnswer: String,
    val subject: String,
    val chapter: String,
    val topic: String,
    val difficulty: DifficultyLevel,
    val marks: Double = 2.0,
    val answerDetectedInPdf: Boolean,
    val approved: Boolean
)
