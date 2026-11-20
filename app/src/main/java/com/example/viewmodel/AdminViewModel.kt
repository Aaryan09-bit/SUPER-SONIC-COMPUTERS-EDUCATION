package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.OperationType
import com.example.core.friendlyFirestoreMessage
import com.example.core.handleFirestoreError
import com.example.data.model.AssignedScope
import com.example.data.model.AuditLog
import com.example.data.model.BankQuestion
import com.example.data.model.BundledQuestionSet
import com.example.data.model.DifficultyLevel
import com.example.data.model.ExamResult
import com.example.data.model.ExamStatus
import com.example.data.model.Examination
import com.example.data.model.ExtractedDraftQuestion
import com.example.data.model.NotificationItem
import com.example.data.model.PdfPaper
import com.example.data.model.QuestionType
import com.example.data.model.StudentProfile
import com.example.data.model.TeacherProfile
import com.example.data.model.UiState
import com.example.data.repository.BundledSetsRepository
import com.example.data.repository.InstituteDirectoryRepository
import com.example.data.repository.InstituteExamRepository
import com.example.data.repository.PdfAiExtractorRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfExtractionUiState(
    val extracting: Boolean = false,
    val progressLabel: String = "",
    val progressFraction: Float = 0f,
    val paperTitle: String = "",
    val fileName: String = "",
    val subject: String = "Computer Fundamentals",
    val chapter: String = "General",
    val topic: String = "Core Concepts",
    val pagesProcessed: Int = 0,
    val totalPages: Int = 0,
    val answerKeyFoundInPdf: Boolean = false,
    val drafts: List<ExtractedDraftQuestion> = emptyList(),
    val error: String? = null
)

data class AdminBannerState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val includeDemoRecords: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val directoryRepo: InstituteDirectoryRepository,
    private val examRepo: InstituteExamRepository,
    private val bundledSetsRepo: BundledSetsRepository,
    private val pdfAiRepo: PdfAiExtractorRepository
) : ViewModel() {

    private val _studentsLimit = MutableStateFlow(50L)
    val studentsPageLimit: StateFlow<Long> = _studentsLimit.asStateFlow()

    private val _questionsLimit = MutableStateFlow(60L)
    val questionsPageLimit: StateFlow<Long> = _questionsLimit.asStateFlow()

    private val _resultsLimit = MutableStateFlow(50L)
    val resultsPageLimit: StateFlow<Long> = _resultsLimit.asStateFlow()

    val teachersState: StateFlow<UiState<List<TeacherProfile>>> =
        directoryRepo.observeAllTeachers(100L).asUiState("teachers")

    val studentsState: StateFlow<UiState<List<StudentProfile>>> =
        _studentsLimit.flatMapLatest { lim -> directoryRepo.observeAllStudents(lim) }.asUiState("students")

    val pdfPapersState: StateFlow<UiState<List<PdfPaper>>> =
        examRepo.observePdfPapers(100L).asUiState("pdf_papers")

    val questionsState: StateFlow<UiState<List<BankQuestion>>> =
        _questionsLimit.flatMapLatest { lim -> examRepo.observeQuestionBank(lim) }.asUiState("questions")

    val examinationsState: StateFlow<UiState<List<Examination>>> =
        examRepo.observeAllExaminationsAdmin(100L).asUiState("examinations")

    val resultsState: StateFlow<UiState<List<ExamResult>>> =
        _resultsLimit.flatMapLatest { lim -> examRepo.observeAllResultsAdmin(lim) }.asUiState("exam_results")

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> =
        directoryRepo.observeAllNotificationsAdmin(60L).asUiState("notifications")

    val auditLogsState: StateFlow<UiState<List<AuditLog>>> =
        directoryRepo.observeAuditLogs(60L).asUiState("audit_logs")

    private val _bundledSets = MutableStateFlow<List<BundledQuestionSet>>(emptyList())
    val bundledSets: StateFlow<List<BundledQuestionSet>> = _bundledSets.asStateFlow()

    private val _extractionState = MutableStateFlow(PdfExtractionUiState())
    val extractionState: StateFlow<PdfExtractionUiState> = _extractionState.asStateFlow()

    private val _bannerState = MutableStateFlow(AdminBannerState())
    val bannerState: StateFlow<AdminBannerState> = _bannerState.asStateFlow()

    private var lastReconcileSignature: String = ""

    init {
        viewModelScope.launch {
            runCatching { bundledSetsRepo.loadBundledSets() }
                .onSuccess { _bundledSets.value = it }
        }
        // Automatically backfill assignedTeacherUid on students if their assigned Teacher recently
        // linked their Google account via TCH-xxxx code.
        viewModelScope.launch {
            combine(teachersState, studentsState) { tState, sState -> tState to sState }
                .collect { (tState, sState) ->
                    if (tState is UiState.Success && sState is UiState.Success) {
                        val linkedTeachers = tState.data.filter { it.linkedUid.isNotBlank() }
                        val byId = linkedTeachers.associateBy { it.id }
                        val needsSync = sState.data.filter { stu ->
                            val teacher = byId[stu.assignedTeacherId] ?: return@filter false
                            stu.assignedTeacherUid != teacher.linkedUid
                        }
                        if (needsSync.isNotEmpty()) {
                            val sig = needsSync.joinToString("|") { "${it.id}:${byId[it.assignedTeacherId]?.linkedUid}" }
                            if (sig != lastReconcileSignature) {
                                lastReconcileSignature = sig
                                directoryRepo.reconcileTeacherAssignments(tState.data, sState.data)
                            }
                        }
                    }
                }
        }
    }

    fun loadMoreStudents() {
        _studentsLimit.update { (it + 50L).coerceAtMost(500L) }
    }

    fun loadMoreQuestions() {
        _questionsLimit.update { (it + 60L).coerceAtMost(1500L) }
    }

    fun loadMoreResults() {
        _resultsLimit.update { (it + 50L).coerceAtMost(1000L) }
    }

    fun toggleDemoVisibility(include: Boolean) {
        _bannerState.update { it.copy(includeDemoRecords = include) }
    }

    fun clearBanner() {
        _bannerState.update { it.copy(message = null, error = null) }
    }

    // ==================== STUDENTS & TEACHERS ====================

    fun createStudent(
        name: String,
        email: String,
        phone: String,
        course: String,
        batch: String,
        teacher: TeacherProfile?,
        photoUrl: String = ""
    ) = runAction("Student account created") {
        directoryRepo.createStudent(name, email, phone, course, batch, teacher, photoUrl).map {
            "Student ${it.name} created • ID: ${it.studentCode} • Passcode: ${it.passcode}"
        }
    }

    fun updateStudent(
        studentId: String,
        studentCode: String,
        name: String,
        email: String,
        phone: String,
        course: String,
        batch: String,
        teacher: TeacherProfile?,
        photoUrl: String,
        active: Boolean
    ) = runAction("Student updated") {
        directoryRepo.updateStudent(studentId, studentCode, name, email, phone, course, batch, teacher, photoUrl, active)
            .map { "Updated student $name" }
    }

    fun transferStudent(student: StudentProfile, newTeacher: TeacherProfile) = runAction("Student transferred") {
        directoryRepo.transferStudentToTeacher(student, newTeacher)
            .map { "Transferred ${student.name} to ${newTeacher.name}" }
    }

    fun deleteStudent(student: StudentProfile) = runAction("Student removed") {
        directoryRepo.deleteStudent(student).map { "Removed ${student.name}" }
    }

    fun createTeacher(name: String, email: String, phone: String, specialty: String) =
        runAction("Teacher created") {
            directoryRepo.createTeacher(name, email, phone, specialty).map {
                "Teacher ${it.name} created • ID: ${it.teacherCode} • Passcode: ${it.passcode}"
            }
        }

    fun updateTeacher(
        teacherId: String,
        name: String,
        email: String,
        phone: String,
        specialty: String,
        active: Boolean,
        teacherCode: String
    ) = runAction("Teacher updated") {
        directoryRepo.updateTeacher(teacherId, name, email, phone, specialty, active, teacherCode)
            .map { "Updated teacher $name" }
    }

    fun deleteTeacher(teacher: TeacherProfile) = runAction("Teacher removed") {
        directoryRepo.deleteTeacher(teacher).map { "Removed ${teacher.name}" }
    }

    // ==================== BUNDLED 42 PDF SETS & AI PDF EXTRACTOR ====================

    fun importBundledSet(set: BundledQuestionSet) = runAction("Set imported") {
        examRepo.importBundledSetToQuestionBank(set).map { count ->
            "Imported $count MCQs from ${set.sourceFileName} into Question Bank"
        }
    }

    fun extractPdfWithAi(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _extractionState.value = PdfExtractionUiState(
                extracting = true,
                progressLabel = "Validating PDF file…",
                progressFraction = 0.05f,
                fileName = fileName
            )
            val result = pdfAiRepo.extractQuestionsFromPdfUri(uri, fileName) { prog ->
                _extractionState.update {
                    it.copy(
                        progressLabel = prog.stageLabel,
                        progressFraction = prog.progressFraction,
                        pagesProcessed = prog.pagesProcessed,
                        totalPages = prog.totalPages
                    )
                }
            }
            result.fold(
                onSuccess = { extracted ->
                    _extractionState.value = PdfExtractionUiState(
                        extracting = false,
                        progressLabel = "Extraction complete",
                        progressFraction = 1f,
                        paperTitle = extracted.detectedPaperTitle,
                        fileName = fileName,
                        subject = extracted.detectedSubject,
                        pagesProcessed = extracted.pagesProcessed,
                        totalPages = extracted.totalPagesInPdf,
                        answerKeyFoundInPdf = extracted.answerKeyFoundInPdf,
                        drafts = extracted.questions
                    )
                },
                onFailure = { err ->
                    _extractionState.update {
                        it.copy(
                            extracting = false,
                            progressLabel = "",
                            progressFraction = 0f,
                            error = err.localizedMessage ?: "AI extraction failed. You can retry or add questions manually."
                        )
                    }
                }
            )
        }
    }

    fun updateExtractionMeta(title: String, subject: String, chapter: String, topic: String) {
        _extractionState.update {
            it.copy(paperTitle = title, subject = subject, chapter = chapter, topic = topic)
        }
    }

    fun updateDraftQuestion(updated: ExtractedDraftQuestion) {
        _extractionState.update { state ->
            state.copy(drafts = state.drafts.map { if (it.tempId == updated.tempId) updated else it })
        }
    }

    fun removeDraftQuestion(tempId: String) {
        _extractionState.update { state ->
            state.copy(drafts = state.drafts.filterNot { it.tempId == tempId })
        }
    }

    fun dismissExtractionDrafts() {
        _extractionState.value = PdfExtractionUiState()
    }

    fun saveReviewedExtractionToBank() {
        val state = _extractionState.value
        if (state.drafts.isEmpty()) return
        runAction("Extracted questions saved") {
            examRepo.saveExtractedPdfDrafts(
                title = state.paperTitle,
                fileName = state.fileName,
                subject = state.subject,
                chapter = state.chapter,
                topic = state.topic,
                drafts = state.drafts
            ).map { count ->
                _extractionState.value = PdfExtractionUiState()
                "Saved $count reviewed questions into Question Bank"
            }
        }
    }

    fun saveManualQuestion(
        existingId: String?,
        questionText: String,
        questionType: QuestionType,
        options: List<String>,
        correctAnswers: List<String>,
        explanation: String,
        subject: String,
        chapter: String,
        topic: String,
        difficulty: DifficultyLevel,
        marks: Double,
        answerApproved: Boolean
    ) = runAction("Question saved") {
        examRepo.saveBankQuestion(
            existingId = existingId,
            questionText = questionText,
            questionType = questionType,
            options = options,
            correctAnswers = correctAnswers,
            explanation = explanation,
            subject = subject,
            chapter = chapter,
            topic = topic,
            difficulty = difficulty,
            marks = marks,
            answerApproved = answerApproved
        ).map { "Saved question to Question Bank" }
    }

    fun deleteQuestion(questionId: String) = runAction("Question deleted") {
        examRepo.deleteBankQuestion(questionId).map { "Question removed from bank" }
    }

    // ==================== EXAMINATIONS & GRADING ====================

    fun createExamination(
        title: String,
        description: String,
        subject: String,
        batch: String,
        status: ExamStatus,
        durationMinutes: Int,
        marksPerQuestion: Double,
        negativeMarks: Double,
        passingPercentage: Double,
        randomizeQuestions: Boolean,
        randomizeOptions: Boolean,
        showResultImmediately: Boolean,
        allowAnswerReview: Boolean,
        assignedScope: AssignedScope,
        selectedStudents: List<StudentProfile>,
        assignedTeacherUid: String,
        selectedQuestions: List<BankQuestion>
    ) = runAction("Examination created") {
        val now = System.currentTimeMillis()
        examRepo.createExamination(
            title = title,
            description = description,
            subject = subject,
            batch = batch,
            status = status,
            durationMinutes = durationMinutes,
            marksPerQuestion = marksPerQuestion,
            negativeMarks = negativeMarks,
            passingPercentage = passingPercentage,
            scheduledAtMillis = now,
            deadlineAtMillis = now + 7L * 86_400_000L,
            randomizeQuestions = randomizeQuestions,
            randomizeOptions = randomizeOptions,
            showResultImmediately = showResultImmediately,
            allowAnswerReview = allowAnswerReview,
            assignedScope = assignedScope,
            selectedStudents = selectedStudents,
            assignedTeacherUid = assignedTeacherUid,
            selectedQuestions = selectedQuestions
        ).map { "Examination '$title' created (${selectedQuestions.size} questions)" }
    }

    fun updateExamStatus(examId: String, status: ExamStatus) = runAction("Status updated") {
        examRepo.updateExaminationStatus(examId, status).map { "Exam marked as ${status.label}" }
    }

    fun gradeSubjectiveAnswer(result: ExamResult, questionId: String, marks: Double) = runAction("Graded") {
        examRepo.gradeSubjectiveQuestion(result, questionId, marks).map { "Subjective answer graded" }
    }

    fun updateResultVisibility(resultId: String, visible: Boolean, allowReview: Boolean) = runAction("Updated") {
        examRepo.updateResultVisibility(resultId, visible, allowReview).map { "Student result visibility updated" }
    }

    // ==================== DEMO DATA CONTROLS ====================

    fun seedDemoData() = runAction("Demo workspace seeded") {
        val sets = _bundledSets.value.ifEmpty { bundledSetsRepo.loadBundledSets() }
        examRepo.seedDemoInstituteData(sets).map {
            "Loaded sample demo teachers, students, Set 01 test & sample results (tagged Demo)"
        }
    }

    fun clearDemoData() = runAction("Demo records cleared") {
        examRepo.clearDemoInstituteData().map { count ->
            "Removed $count demo records; all real institute records remain intact"
        }
    }

    private fun runAction(defaultSuccess: String, block: suspend () -> Result<String>) {
        viewModelScope.launch {
            _bannerState.update { it.copy(busy = true, error = null, message = null) }
            val result = block()
            result.fold(
                onSuccess = { msg ->
                    _bannerState.update { it.copy(busy = false, message = msg.ifBlank { defaultSuccess }) }
                },
                onFailure = { err ->
                    _bannerState.update {
                        it.copy(busy = false, error = friendlyFirestoreMessage(err, "Operation failed."))
                    }
                }
            )
        }
    }

    private fun <T> Flow<T>.asUiState(path: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                emit(UiState.Error(friendlyFirestoreMessage(fsErr ?: error, "Unable to load $path.")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
}
