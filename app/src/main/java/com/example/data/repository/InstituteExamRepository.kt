package com.example.data.repository

import com.example.core.EvaluationEngine
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AnswerStatus
import com.example.data.model.AssignedScope
import com.example.data.model.AttemptStatus
import com.example.data.model.BankQuestion
import com.example.data.model.BundledQuestionSet
import com.example.data.model.DifficultyLevel
import com.example.data.model.ExamAnswerKey
import com.example.data.model.ExamAttempt
import com.example.data.model.ExamResult
import com.example.data.model.ExamStatus
import com.example.data.model.Examination
import com.example.data.model.ExtractedDraftQuestion
import com.example.data.model.GradingStatus
import com.example.data.model.NotificationCategory
import com.example.data.model.PdfPaper
import com.example.data.model.QuestionAnalysis
import com.example.data.model.QuestionType
import com.example.data.model.SanitizedExamQuestion
import com.example.data.model.StudentProfile
import com.example.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

class InstituteExamRepository(
    private val db: FirebaseFirestore,
    private val directoryRepo: InstituteDirectoryRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    // ==================== PDF PAPERS & QUESTION BANK ====================

    fun observePdfPapers(): Flow<List<PdfPaper>> =
        db.collection("pdf_papers")
            .snapshots()
            .map { snap ->
                snap.toObjects(PdfPaper::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeQuestionBank(): Flow<List<BankQuestion>> =
        db.collection("questions")
            .snapshots()
            .map { snap ->
                snap.toObjects(BankQuestion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun importBundledSetToQuestionBank(set: BundledQuestionSet): Result<Int> = try {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val paperId = "paper_${set.setId}"
        val approvedCount = set.questions.count { it.correctOption.isNotBlank() }

        val paperData = mapOf(
            "title" to set.title,
            "fileName" to set.sourceFileName,
            "subject" to set.primarySubject,
            "chapter" to set.rangeLabel,
            "topic" to "Institute Question Paper Set ${set.setNumber}",
            "totalExtracted" to set.questionCount,
            "approvedCount" to approvedCount,
            "sourceType" to "BUNDLED_SET",
            "uploadedByUid" to uid,
            "status" to if (approvedCount == set.questionCount) "APPROVED" else "PENDING_REVIEW",
            "demoRecord" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(db.collection("pdf_papers").document(paperId), paperData)

        set.questions.forEach { q ->
            val qId = "q_${set.setId}_${q.number}"
            val correctList = if (q.correctOption.isNotBlank()) listOf(q.correctOption.uppercase()) else emptyList()
            val qData = mapOf(
                "questionText" to q.text.take(2000),
                "questionType" to QuestionType.MCQ.name,
                "options" to q.options.take(10),
                "correctAnswers" to correctList,
                "explanation" to "Imported from ${set.sourceFileName} (Q${q.number})",
                "subject" to q.subject.take(100),
                "chapter" to set.rangeLabel.take(120),
                "topic" to "Set ${set.setNumber}",
                "difficulty" to DifficultyLevel.fromRaw(q.difficulty).name,
                "marks" to 2.0,
                "sourcePaperId" to paperId,
                "answerApproved" to correctList.isNotEmpty(),
                "createdByUid" to uid,
                "demoRecord" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(db.collection("questions").document(qId), qData)
        }
        batch.commit().await()

        directoryRepo.logAudit(
            UserRole.ADMIN,
            "PDF_SET_IMPORTED",
            "pdf_papers",
            paperId,
            "Imported ${set.questionCount} MCQs from ${set.sourceFileName}"
        )
        Result.success(set.questionCount)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "pdf_papers/${set.setId}")
        Result.failure(e)
    }

    suspend fun saveExtractedPdfDrafts(
        title: String,
        fileName: String,
        subject: String,
        chapter: String,
        topic: String,
        drafts: List<ExtractedDraftQuestion>
    ): Result<Int> = try {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        require(drafts.isNotEmpty()) { "No questions to save." }
        val paperId = "paper_${shortId()}"
        val approvedCount = drafts.count { it.approved && it.correctAnswer.isNotBlank() }

        val paperData = buildMap<String, Any> {
            put("title", title.trim().ifBlank { "Uploaded Question Paper" }.take(200))
            put("fileName", fileName.trim().ifBlank { "question_paper.pdf" }.take(200))
            put("subject", subject.trim().ifBlank { "Computer Fundamentals" }.take(100))
            if (chapter.isNotBlank()) put("chapter", chapter.trim().take(120))
            if (topic.isNotBlank()) put("topic", topic.trim().take(120))
            put("totalExtracted", drafts.size)
            put("approvedCount", approvedCount)
            put("sourceType", "UPLOADED_PDF")
            put("uploadedByUid", uid)
            put("status", if (approvedCount == drafts.size) "APPROVED" else "PENDING_REVIEW")
            put("demoRecord", false)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val batch = db.batch()
        batch.set(db.collection("pdf_papers").document(paperId), paperData)

        drafts.forEach { d ->
            val qId = "q_${shortId()}"
            val ansList = d.correctAnswer
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(10)
            val qData = buildMap<String, Any> {
                put("questionText", d.questionText.trim().take(2000))
                put("questionType", d.questionType.name)
                put("options", d.options.take(10))
                put("correctAnswers", ansList)
                put("subject", d.subject.trim().ifBlank { subject }.take(100))
                if (d.chapter.isNotBlank()) put("chapter", d.chapter.trim().take(120))
                if (d.topic.isNotBlank()) put("topic", d.topic.trim().take(120))
                put("difficulty", d.difficulty.name)
                put("marks", d.marks.coerceIn(0.5, 100.0))
                put("sourcePaperId", paperId)
                put("answerApproved", d.approved && (ansList.isNotEmpty() || d.questionType == QuestionType.SHORT_ANSWER))
                put("createdByUid", uid)
                put("demoRecord", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.set(db.collection("questions").document(qId), qData)
        }
        batch.commit().await()

        directoryRepo.logAudit(
            UserRole.ADMIN,
            "PDF_EXTRACTED_SAVED",
            "pdf_papers",
            paperId,
            "Saved ${drafts.size} AI-extracted questions from $fileName"
        )
        Result.success(drafts.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "pdf_papers")
        Result.failure(e)
    }

    suspend fun saveBankQuestion(
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
        answerApproved: Boolean,
        demoRecord: Boolean = false
    ): Result<String> = try {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val qId = existingId?.takeIf { it.isNotBlank() } ?: "q_${shortId()}"
        if (existingId.isNullOrBlank()) {
            val payload = buildMap<String, Any> {
                put("questionText", questionText.trim().take(2000))
                put("questionType", questionType.name)
                put("options", options.filter { it.isNotBlank() }.take(10))
                put("correctAnswers", correctAnswers.filter { it.isNotBlank() }.take(10))
                if (explanation.isNotBlank()) put("explanation", explanation.trim().take(2000))
                put("subject", subject.trim().ifBlank { "Computer Fundamentals" }.take(100))
                if (chapter.isNotBlank()) put("chapter", chapter.trim().take(120))
                if (topic.isNotBlank()) put("topic", topic.trim().take(120))
                put("difficulty", difficulty.name)
                put("marks", marks.coerceIn(0.5, 100.0))
                put("answerApproved", answerApproved)
                put("createdByUid", uid)
                put("demoRecord", demoRecord)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("questions").document(qId).set(payload).await()
        } else {
            val updates = mapOf(
                "questionText" to questionText.trim().take(2000),
                "questionType" to questionType.name,
                "options" to options.filter { it.isNotBlank() }.take(10),
                "correctAnswers" to correctAnswers.filter { it.isNotBlank() }.take(10),
                "explanation" to explanation.trim().take(2000),
                "subject" to subject.trim().ifBlank { "Computer Fundamentals" }.take(100),
                "chapter" to chapter.trim().take(120),
                "topic" to topic.trim().take(120),
                "difficulty" to difficulty.name,
                "marks" to marks.coerceIn(0.5, 100.0),
                "answerApproved" to answerApproved,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("questions").document(qId).update(updates).await()
        }
        Result.success(qId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "questions")
        Result.failure(e)
    }

    suspend fun deleteBankQuestion(questionId: String): Result<Unit> = try {
        db.collection("questions").document(questionId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "questions/$questionId")
        Result.failure(e)
    }

    // ==================== EXAMINATIONS ====================

    fun observeAllExaminationsAdmin(): Flow<List<Examination>> =
        db.collection("examinations")
            .snapshots()
            .map { snap ->
                snap.toObjects(Examination::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeTeacherExaminations(teacherUid: String): Flow<List<Examination>> {
        val assignedFlow = db.collection("examinations")
            .whereEqualTo("assignedTeacherUid", teacherUid)
            .snapshots()
            .map { it.toObjects(Examination::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val publicFlow = db.collection("examinations")
            .whereEqualTo("status", ExamStatus.PUBLISHED.name)
            .whereEqualTo("assignedScope", AssignedScope.ALL.name)
            .snapshots()
            .map { it.toObjects(Examination::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        return combine(assignedFlow, publicFlow) { a, b ->
            (a + b).distinctBy { it.id }.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
    }

    fun observeStudentExaminations(studentUid: String): Flow<List<Examination>> {
        val allScopeFlow = db.collection("examinations")
            .whereEqualTo("status", ExamStatus.PUBLISHED.name)
            .whereEqualTo("assignedScope", AssignedScope.ALL.name)
            .snapshots()
            .map { it.toObjects(Examination::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val targetedFlow = db.collection("examinations")
            .whereEqualTo("status", ExamStatus.PUBLISHED.name)
            .whereArrayContains("assignedStudentUids", studentUid)
            .snapshots()
            .map { it.toObjects(Examination::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        return combine(allScopeFlow, targetedFlow) { a, b ->
            (a + b).distinctBy { it.id }.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
    }

    suspend fun createExamination(
        title: String,
        description: String,
        subject: String,
        batch: String,
        status: ExamStatus,
        durationMinutes: Int,
        marksPerQuestion: Double,
        negativeMarks: Double,
        passingPercentage: Double,
        scheduledAtMillis: Long,
        deadlineAtMillis: Long,
        randomizeQuestions: Boolean,
        randomizeOptions: Boolean,
        showResultImmediately: Boolean,
        allowAnswerReview: Boolean,
        assignedScope: AssignedScope,
        selectedStudents: List<StudentProfile>,
        assignedTeacherUid: String,
        selectedQuestions: List<BankQuestion>,
        demoRecord: Boolean = false
    ): Result<String> = try {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        require(selectedQuestions.isNotEmpty()) { "Select at least 1 question for the examination." }
        val examId = "exam_${shortId()}"

        val sanitizedQuestions = selectedQuestions.mapIndexed { index, q ->
            SanitizedExamQuestion(
                questionId = q.id.ifBlank { "q_$index" },
                text = q.questionText,
                questionType = q.questionType,
                options = q.options,
                marks = marksPerQuestion,
                orderIndex = index
            ).toMap()
        }

        val answerMap = selectedQuestions.associateIndexedAnswerMap()
        val explanationMap = selectedQuestions.mapIndexedNotNull { idx, q ->
            val key = q.id.ifBlank { "q_$idx" }
            q.explanation.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()

        val totalMarks = round((selectedQuestions.size * marksPerQuestion) * 100.0) / 100.0
        val studentIds = selectedStudents.map { it.id }.distinct().take(500)
        val studentUids = selectedStudents.mapNotNull { it.linkedUid.takeIf { u -> u.isNotBlank() } }.distinct().take(500)

        val examData = buildMap<String, Any> {
            put("title", title.trim().take(200))
            if (description.isNotBlank()) put("description", description.trim().take(1000))
            put("subject", subject.trim().ifBlank { "Computer Science" }.take(100))
            if (batch.isNotBlank()) put("batch", batch.trim().take(80))
            put("status", status.name)
            put("durationMinutes", durationMinutes.coerceIn(1, 480))
            put("totalMarks", totalMarks.coerceAtLeast(1.0))
            put("marksPerQuestion", marksPerQuestion.coerceIn(0.5, 100.0))
            put("negativeMarks", negativeMarks.coerceIn(0.0, 50.0))
            put("passingPercentage", passingPercentage.coerceIn(0.0, 100.0))
            put("scheduledAtMillis", scheduledAtMillis)
            put("deadlineAtMillis", deadlineAtMillis)
            put("randomizeQuestions", randomizeQuestions)
            put("randomizeOptions", randomizeOptions)
            put("showResultImmediately", showResultImmediately)
            put("allowAnswerReview", allowAnswerReview)
            put("assignedScope", assignedScope.name)
            put("assignedStudentIds", studentIds)
            put("assignedStudentUids", studentUids)
            if (assignedTeacherUid.isNotBlank()) put("assignedTeacherUid", assignedTeacherUid)
            put("createdByUid", uid)
            put("questionCount", selectedQuestions.size)
            put("questions", sanitizedQuestions)
            put("demoRecord", demoRecord)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val answerKeyData = buildMap<String, Any> {
            put("examId", examId)
            put("answers", answerMap)
            if (explanationMap.isNotEmpty()) put("explanations", explanationMap)
            put("marksPerQuestion", marksPerQuestion.coerceIn(0.5, 100.0))
            put("negativeMarks", negativeMarks.coerceIn(0.0, 50.0))
            put("passingPercentage", passingPercentage.coerceIn(0.0, 100.0))
            put("createdByUid", uid)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val batchWrite = db.batch()
        batchWrite.set(db.collection("examinations").document(examId), examData)
        batchWrite.set(db.collection("exam_answer_keys").document(examId), answerKeyData)
        batchWrite.commit().await()

        if (status == ExamStatus.PUBLISHED) {
            studentUids.take(25).forEach { sUid ->
                directoryRepo.sendNotification(
                    recipientUid = sUid,
                    title = "New Examination Assigned",
                    message = "$title ($subject • ${durationMinutes} min • ${selectedQuestions.size} Qs) is now available.",
                    category = NotificationCategory.EXAM_ASSIGNED,
                    relatedExamId = examId
                )
            }
        }

        directoryRepo.logAudit(
            UserRole.ADMIN,
            "EXAM_CREATED",
            "examinations",
            examId,
            "Created exam '$title' (${selectedQuestions.size} questions, status=${status.name})"
        )
        Result.success(examId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "examinations")
        Result.failure(e)
    }

    suspend fun updateExaminationStatus(examId: String, newStatus: ExamStatus): Result<Unit> = try {
        db.collection("examinations").document(examId).update(
            mapOf(
                "status" to newStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        directoryRepo.logAudit(UserRole.ADMIN, "EXAM_STATUS_CHANGED", "examinations", examId, "Status -> ${newStatus.name}")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "examinations/$examId")
        Result.failure(e)
    }

    suspend fun assignExaminationToStudents(
        exam: Examination,
        students: List<StudentProfile>,
        deadlineMillis: Long
    ): Result<Unit> = try {
        val mergedIds = (exam.assignedStudentIds + students.map { it.id }).distinct().take(500)
        val mergedUids = (exam.assignedStudentUids + students.mapNotNull { it.linkedUid.takeIf { u -> u.isNotBlank() } })
            .distinct().take(500)
        db.collection("examinations").document(exam.id).update(
            mapOf(
                "assignedStudentIds" to mergedIds,
                "assignedStudentUids" to mergedUids,
                "deadlineAtMillis" to deadlineMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        students.mapNotNull { it.linkedUid.takeIf { u -> u.isNotBlank() } }.take(20).forEach { sUid ->
            directoryRepo.sendNotification(
                recipientUid = sUid,
                title = "Examination Assigned by Teacher",
                message = "${exam.title} (${exam.subject}) has been assigned to you.",
                category = NotificationCategory.EXAM_ASSIGNED,
                relatedExamId = exam.id
            )
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "examinations/${exam.id}")
        Result.failure(e)
    }

    // ==================== STUDENT ATTEMPTS & AUTO EVALUATION ====================

    fun observeStudentAttempts(studentUid: String): Flow<List<ExamAttempt>> =
        db.collection("exam_attempts")
            .whereEqualTo("studentUid", studentUid)
            .snapshots()
            .map { it.toObjects(ExamAttempt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun startOrResumeAttempt(
        exam: Examination,
        studentProfile: StudentProfile?,
        fallbackName: String
    ): Result<ExamAttempt> = try {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val attemptId = "${exam.id}_$uid"
        val docRef = db.collection("exam_attempts").document(attemptId)
        val existingSnap = docRef.get().await()
        if (existingSnap.exists()) {
            val existing = existingSnap.toObject(ExamAttempt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: error("Could not resume attempt.")
            if (existing.statusEnum() == AttemptStatus.SUBMITTED) {
                return Result.failure(IllegalStateException("You have already submitted this examination."))
            }
            Result.success(existing)
        } else {
            val nowMillis = System.currentTimeMillis()
            val payload = buildMap<String, Any> {
                put("examId", exam.id)
                put("examTitle", exam.title.take(200))
                put("studentUid", uid)
                if (!studentProfile?.id.isNullOrBlank()) put("studentId", studentProfile!!.id)
                put("studentName", (studentProfile?.name ?: fallbackName).ifBlank { "Student" }.take(120))
                val tUid = studentProfile?.assignedTeacherUid?.takeIf { it.isNotBlank() } ?: exam.assignedTeacherUid
                if (tUid.isNotBlank()) put("assignedTeacherUid", tUid)
                put("status", AttemptStatus.IN_PROGRESS.name)
                put("responses", emptyMap<String, String>())
                put("flaggedQuestionIds", emptyList<String>())
                put("currentQuestionIndex", 0)
                put("startedAtMillis", nowMillis)
                put("timeTakenSeconds", 0)
                put("demoRecord", exam.demoRecord)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            val created = ExamAttempt(
                id = attemptId,
                examId = exam.id,
                examTitle = exam.title,
                studentUid = uid,
                studentId = studentProfile?.id.orEmpty(),
                studentName = studentProfile?.name ?: fallbackName,
                assignedTeacherUid = studentProfile?.assignedTeacherUid.orEmpty(),
                status = AttemptStatus.IN_PROGRESS.name,
                startedAtMillis = nowMillis,
                demoRecord = exam.demoRecord
            )
            Result.success(created)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "exam_attempts/${exam.id}")
        Result.failure(e)
    }

    suspend fun autoSaveAttemptProgress(
        attemptId: String,
        responses: Map<String, String>,
        flaggedQuestionIds: List<String>,
        currentQuestionIndex: Int,
        timeTakenSeconds: Int
    ): Result<Unit> = try {
        db.collection("exam_attempts").document(attemptId).update(
            mapOf(
                "responses" to responses,
                "flaggedQuestionIds" to flaggedQuestionIds.take(200),
                "currentQuestionIndex" to currentQuestionIndex.coerceIn(0, 200),
                "timeTakenSeconds" to timeTakenSeconds.coerceIn(0, 86400),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "exam_attempts/$attemptId")
        Result.failure(e)
    }

    suspend fun submitAndEvaluateExam(
        exam: Examination,
        attempt: ExamAttempt,
        studentProfile: StudentProfile?,
        responses: Map<String, String>,
        flaggedQuestionIds: List<String>,
        currentQuestionIndex: Int,
        timeTakenSeconds: Int
    ): Result<ExamResult> = try {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val attemptId = "${exam.id}_$uid"
        val submittedMillis = System.currentTimeMillis()

        // Step 1: Lock attempt to terminal SUBMITTED state (irreversible for student)
        db.collection("exam_attempts").document(attemptId).update(
            mapOf(
                "responses" to responses,
                "flaggedQuestionIds" to flaggedQuestionIds.take(200),
                "currentQuestionIndex" to currentQuestionIndex.coerceIn(0, 200),
                "status" to AttemptStatus.SUBMITTED.name,
                "submittedAtMillis" to submittedMillis,
                "timeTakenSeconds" to timeTakenSeconds.coerceIn(0, 86400),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Step 2: Now authorized to read protected exam_answer_keys/{examId}
        val keySnap = db.collection("exam_answer_keys").document(exam.id).get().await()
        val answerKey = keySnap.toObject(ExamAnswerKey::class.java)
            ?: ExamAnswerKey(
                examId = exam.id,
                marksPerQuestion = exam.marksPerQuestion,
                negativeMarks = exam.negativeMarks,
                passingPercentage = exam.passingPercentage
            )

        // Step 3: Deterministic Auto-Evaluation Engine
        val outcome = EvaluationEngine.evaluate(exam, answerKey, responses)
        val tUid = studentProfile?.assignedTeacherUid?.takeIf { it.isNotBlank() } ?: exam.assignedTeacherUid

        val resultData = buildMap<String, Any> {
            put("examId", exam.id)
            put("examTitle", exam.title.take(200))
            put("subject", exam.subject.take(100))
            val batchVal = studentProfile?.batch?.takeIf { it.isNotBlank() } ?: exam.batch
            if (batchVal.isNotBlank()) put("batch", batchVal.take(80))
            put("studentUid", uid)
            if (!studentProfile?.id.isNullOrBlank()) put("studentId", studentProfile!!.id)
            if (!studentProfile?.studentCode.isNullOrBlank()) put("studentCode", studentProfile!!.studentCode)
            put("studentName", attempt.studentName.ifBlank { "Student" }.take(120))
            if (!studentProfile?.assignedTeacherId.isNullOrBlank()) put("assignedTeacherId", studentProfile!!.assignedTeacherId)
            if (tUid.isNotBlank()) put("assignedTeacherUid", tUid)
            if (!studentProfile?.assignedTeacherName.isNullOrBlank()) put("assignedTeacherName", studentProfile!!.assignedTeacherName)
            put("totalQuestions", outcome.totalQuestions)
            put("correctCount", outcome.correctCount)
            put("wrongCount", outcome.wrongCount)
            put("unattemptedCount", outcome.unattemptedCount)
            put("pendingManualCount", outcome.pendingManualCount)
            put("totalMarks", outcome.totalMarks)
            put("obtainedMarks", outcome.obtainedMarks)
            put("percentage", outcome.percentage)
            put("passed", outcome.passed)
            put("timeTakenSeconds", timeTakenSeconds.coerceIn(0, 86400))
            put("resultsVisibleToStudent", exam.showResultImmediately)
            put("allowAnswerReview", exam.allowAnswerReview)
            put("gradingStatus", outcome.gradingStatus.name)
            put("questionAnalyses", outcome.questionAnalyses.map { it.toMap() })
            put("demoRecord", exam.demoRecord)
            put("submittedAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        db.collection("exam_results").document(attemptId).set(resultData).await()

        if (tUid.isNotBlank() && tUid != uid) {
            directoryRepo.sendNotification(
                recipientUid = tUid,
                title = "Examination Submitted",
                message = "${attempt.studentName} submitted ${exam.title} (${outcome.percentage}%).",
                category = NotificationCategory.EXAM_SUBMITTED,
                relatedExamId = exam.id
            )
        }

        val saved = db.collection("exam_results").document(attemptId).get().await()
            .toObject(ExamResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: ExamResult(
                id = attemptId,
                examId = exam.id,
                examTitle = exam.title,
                subject = exam.subject,
                studentUid = uid,
                studentName = attempt.studentName,
                totalQuestions = outcome.totalQuestions,
                correctCount = outcome.correctCount,
                wrongCount = outcome.wrongCount,
                unattemptedCount = outcome.unattemptedCount,
                pendingManualCount = outcome.pendingManualCount,
                totalMarks = outcome.totalMarks,
                obtainedMarks = outcome.obtainedMarks,
                percentage = outcome.percentage,
                passed = outcome.passed,
                timeTakenSeconds = timeTakenSeconds,
                resultsVisibleToStudent = exam.showResultImmediately,
                allowAnswerReview = exam.allowAnswerReview,
                gradingStatus = outcome.gradingStatus.name,
                questionAnalyses = outcome.questionAnalyses
            )
        Result.success(saved)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "exam_results/${exam.id}")
        Result.failure(e)
    }

    // ==================== RESULTS & SUBJECTIVE GRADING ====================

    fun observeAllResultsAdmin(): Flow<List<ExamResult>> =
        db.collection("exam_results")
            .snapshots()
            .map { snap ->
                snap.toObjects(ExamResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.submittedAt?.seconds ?: 0L }
            }

    fun observeTeacherResults(teacherUid: String): Flow<List<ExamResult>> =
        db.collection("exam_results")
            .whereEqualTo("assignedTeacherUid", teacherUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ExamResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.submittedAt?.seconds ?: 0L }
            }

    fun observeStudentResults(studentUid: String): Flow<List<ExamResult>> =
        db.collection("exam_results")
            .whereEqualTo("studentUid", studentUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ExamResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.submittedAt?.seconds ?: 0L }
            }

    suspend fun gradeSubjectiveQuestion(
        result: ExamResult,
        questionId: String,
        awardedMarks: Double,
        passingPercentage: Double = 40.0
    ): Result<Unit> = try {
        val updatedAnalyses = result.questionAnalyses.map { qa ->
            if (qa.questionId == questionId) {
                val safeMarks = awardedMarks.coerceIn(0.0, qa.maxMarks)
                qa.copy(
                    marksObtained = safeMarks,
                    status = if (safeMarks > 0.0) AnswerStatus.CORRECT.name else AnswerStatus.WRONG.name,
                    needsManualReview = false
                )
            } else qa
        }

        val correct = updatedAnalyses.count { it.statusEnum() == AnswerStatus.CORRECT }
        val wrong = updatedAnalyses.count { it.statusEnum() == AnswerStatus.WRONG }
        val pending = updatedAnalyses.count { it.needsManualReview }
        val totalObtained = round(updatedAnalyses.sumOf { it.marksObtained } * 100.0) / 100.0
        val pct = if (result.totalMarks > 0.0) {
            round(min(100.0, max(0.0, (max(0.0, totalObtained) / result.totalMarks) * 100.0)) * 10.0) / 10.0
        } else 0.0
        val newStatus = if (pending == 0) GradingStatus.FINALIZED.name else GradingStatus.PENDING_REVIEW.name

        db.collection("exam_results").document(result.id).update(
            mapOf(
                "correctCount" to correct,
                "wrongCount" to wrong,
                "pendingManualCount" to pending,
                "obtainedMarks" to totalObtained,
                "percentage" to pct,
                "passed" to (pct >= passingPercentage),
                "gradingStatus" to newStatus,
                "questionAnalyses" to updatedAnalyses.map { it.toMap() },
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "exam_results/${result.id}")
        Result.failure(e)
    }

    suspend fun updateResultVisibility(resultId: String, visibleToStudent: Boolean, allowReview: Boolean): Result<Unit> = try {
        db.collection("exam_results").document(resultId).update(
            mapOf(
                "resultsVisibleToStudent" to visibleToStudent,
                "allowAnswerReview" to allowReview,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "exam_results/$resultId")
        Result.failure(e)
    }

    // ==================== OPTIONAL DEMO SEED / CLEAR (STRICTLY SEPARATED VIA demoRecord=true) ====================

    suspend fun seedDemoInstituteData(bundledSets: List<BundledQuestionSet>): Result<Unit> = try {
        val adminUid = auth.currentUser?.uid ?: error("Not signed in")
        // 1. Demo Teachers
        val t1 = directoryRepo.createTeacher(
            name = "Prof. Rajesh Sharma",
            email = "rajesh.sharma@supersonic.edu",
            phone = "+91 98765 41001",
            subjectSpecialty = "MS Office & Computer Fundamentals",
            customCode = "TCH-DEMO1",
            customPasscode = "DEMO1234",
            demoRecord = true
        ).getOrThrow()

        val t2 = directoryRepo.createTeacher(
            name = "Ms. Priya Nair",
            email = "priya.nair@supersonic.edu",
            phone = "+91 98765 41002",
            subjectSpecialty = "DBMS, Networking & Programming",
            customCode = "TCH-DEMO2",
            customPasscode = "DEMO5678",
            demoRecord = true
        ).getOrThrow()

        // 2. Demo Students
        val s1 = directoryRepo.createStudent(
            name = "Aarav Verma",
            email = "aarav.verma@student.edu",
            phone = "+91 98111 22001",
            course = "ADCA (Advanced Diploma)",
            batch = "Morning-2026",
            teacher = t1,
            customCode = "STU-DEMO1",
            customPasscode = "STU1111",
            demoRecord = true
        ).getOrThrow()

        val s2 = directoryRepo.createStudent(
            name = "Diya Patel",
            email = "diya.patel@student.edu",
            phone = "+91 98111 22002",
            course = "DCA (Computer Applications)",
            batch = "Morning-2026",
            teacher = t1,
            customCode = "STU-DEMO2",
            customPasscode = "STU2222",
            demoRecord = true
        ).getOrThrow()

        val s3 = directoryRepo.createStudent(
            name = "Kabir Malhotra",
            email = "kabir.m@student.edu",
            phone = "+91 98111 22003",
            course = "CCC & Office Automation",
            batch = "Evening-2026",
            teacher = t2,
            customCode = "STU-DEMO3",
            customPasscode = "STU3333",
            demoRecord = true
        ).getOrThrow()

        // 3. Import Set 01 real questions & build a ready-to-take Published Demo Exam
        val set1 = bundledSets.firstOrNull()
        val demoQuestions = if (set1 != null) {
            importBundledSetToQuestionBank(set1)
            set1.questions.take(10).mapIndexed { idx, q ->
                BankQuestion(
                    id = "q_${set1.setId}_${q.number}",
                    questionText = q.text,
                    questionType = QuestionType.MCQ.name,
                    options = q.options,
                    correctAnswers = listOf(q.correctOption.ifBlank { "A" }),
                    subject = q.subject,
                    difficulty = DifficultyLevel.fromRaw(q.difficulty).name,
                    marks = 2.0,
                    answerApproved = true,
                    createdByUid = adminUid,
                    demoRecord = true
                )
            }
        } else emptyList()

        if (demoQuestions.isNotEmpty()) {
            val examId = createExamination(
                title = "MS Office & Word Fundamentals Assessment (Set 01)",
                description = "Official 10-question timed practice examination from Super Sonic Set 01.",
                subject = "MS Word",
                batch = "Morning-2026",
                status = ExamStatus.PUBLISHED,
                durationMinutes = 15,
                marksPerQuestion = 2.0,
                negativeMarks = 0.5,
                passingPercentage = 40.0,
                scheduledAtMillis = System.currentTimeMillis(),
                deadlineAtMillis = System.currentTimeMillis() + 7L * 86_400_000L,
                randomizeQuestions = false,
                randomizeOptions = false,
                showResultImmediately = true,
                allowAnswerReview = true,
                assignedScope = AssignedScope.ALL,
                selectedStudents = listOf(s1, s2, s3),
                assignedTeacherUid = adminUid,
                selectedQuestions = demoQuestions,
                demoRecord = true
            ).getOrThrow()

            // Seed 3 realistic demo exam results (strictly tagged demoRecord = true) for dashboard charts
            val demoScores = listOf(
                Triple(s1, 9, 1),
                Triple(s2, 7, 2),
                Triple(s3, 4, 5)
            )
            val batch = db.batch()
            demoScores.forEachIndexed { idx, (stu, correct, wrong) ->
                val unatt = 10 - correct - wrong
                val obtained = max(0.0, correct * 2.0 - wrong * 0.5)
                val pct = round((obtained / 20.0) * 1000.0) / 10.0
                val resId = "${examId}_demo_${idx + 1}"
                val analyses = demoQuestions.mapIndexed { qIdx, q ->
                    val status = when {
                        qIdx < correct -> AnswerStatus.CORRECT
                        qIdx < correct + wrong -> AnswerStatus.WRONG
                        else -> AnswerStatus.UNATTEMPTED
                    }
                    val corr = q.correctAnswers.firstOrNull().orEmpty()
                    val picked = when (status) {
                        AnswerStatus.CORRECT -> corr
                        AnswerStatus.WRONG -> if (corr == "A") "B" else "A"
                        else -> ""
                    }
                    QuestionAnalysis(
                        questionId = q.id,
                        questionText = q.questionText,
                        questionType = q.questionType,
                        options = q.options,
                        studentAnswer = picked,
                        correctAnswer = corr,
                        status = status.name,
                        marksObtained = when (status) {
                            AnswerStatus.CORRECT -> 2.0
                            AnswerStatus.WRONG -> -0.5
                            else -> 0.0
                        },
                        maxMarks = 2.0,
                        needsManualReview = false
                    ).toMap()
                }
                batch.set(
                    db.collection("exam_results").document(resId),
                    mapOf(
                        "examId" to examId,
                        "examTitle" to "MS Office & Word Fundamentals Assessment (Set 01)",
                        "subject" to if (idx == 2) "MS Excel" else "MS Word",
                        "batch" to stu.batch,
                        "studentUid" to "demo_stu_uid_${idx + 1}",
                        "studentId" to stu.id,
                        "studentCode" to stu.studentCode,
                        "studentName" to stu.name,
                        "assignedTeacherId" to stu.assignedTeacherId,
                        "assignedTeacherUid" to adminUid,
                        "assignedTeacherName" to stu.assignedTeacherName,
                        "totalQuestions" to 10,
                        "correctCount" to correct,
                        "wrongCount" to wrong,
                        "unattemptedCount" to unatt,
                        "pendingManualCount" to 0,
                        "totalMarks" to 20.0,
                        "obtainedMarks" to obtained,
                        "percentage" to pct,
                        "passed" to (pct >= 40.0),
                        "timeTakenSeconds" to (420 + idx * 95),
                        "resultsVisibleToStudent" to true,
                        "allowAnswerReview" to true,
                        "gradingStatus" to GradingStatus.FINALIZED.name,
                        "questionAnalyses" to analyses,
                        "demoRecord" to true,
                        "submittedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "demo_seed")
        Result.failure(e)
    }

    suspend fun clearDemoInstituteData(): Result<Int> = try {
        var deleted = 0
        val collections = listOf("teachers", "students", "examinations", "exam_results", "exam_attempts")
        for (col in collections) {
            val snap = db.collection(col).whereEqualTo("demoRecord", true).get().await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                snap.documents.forEach { doc ->
                    batch.delete(doc.reference)
                    deleted++
                }
                batch.commit().await()
            }
        }
        Result.success(deleted)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "demo_clear")
        Result.failure(e)
    }

    private fun List<BankQuestion>.associateIndexedAnswerMap(): Map<String, String> =
        mapIndexed { idx, q ->
            val key = q.id.ifBlank { "q_$idx" }
            val value = q.correctAnswers.joinToString(",")
            key to value
        }.toMap()

    private fun shortId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
}
