package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AuditLog
import com.example.data.model.NotificationCategory
import com.example.data.model.NotificationItem
import com.example.data.model.PortalClaim
import com.example.data.model.StudentProfile
import com.example.data.model.TeacherProfile
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

class InstituteDirectoryRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "aghanghas08@gmail.com"
    }

    fun isBootstrappedAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        return user.isEmailVerified &&
            user.email?.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) == true
    }

    fun observeUserAccount(uid: String): Flow<UserAccount?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                else null
            }

    suspend fun ensureUserAccount(user: FirebaseUser): Result<UserAccount> = try {
        val docRef = db.collection("users").document(user.uid)
        val existingSnap = docRef.get().await()
        if (existingSnap.exists()) {
            val acc = existingSnap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserAccount(uid = user.uid, email = user.email.orEmpty(), displayName = user.displayName ?: "Member")
            // Upgrade bootstrapped admin if doc had STUDENT previously
            if (isBootstrappedAdmin(user) && acc.roleEnum() != UserRole.ADMIN) {
                docRef.update(
                    mapOf(
                        "role" to UserRole.ADMIN.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(acc.copy(role = UserRole.ADMIN.name))
            } else {
                Result.success(acc)
            }
        } else {
            val isSuperAdmin = isBootstrappedAdmin(user)
            val initialRole = if (isSuperAdmin) UserRole.ADMIN.name else UserRole.STUDENT.name
            val account = UserAccount(
                uid = user.uid,
                email = user.email ?: "${user.uid}@supersonic.edu",
                displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Institute Member",
                photoUrl = user.photoUrl?.toString(),
                role = initialRole,
                active = true
            )
            docRef.set(account.toCreateMap()).await()
            Result.success(account)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/${user.uid}")
        Result.failure(e)
    }

    suspend fun claimInstitutePortalCode(
        codeInput: String,
        passcodeInput: String
    ): Result<UserAccount> = try {
        val user = auth.currentUser ?: error("Please sign in with Google first.")
        val normalizedCode = codeInput.trim().uppercase()
        val normalizedPass = passcodeInput.trim()
        require(normalizedCode.matches(Regex("^[A-Za-z0-9_\\-]+$"))) {
            "Invalid Institute ID format. Example: TCH-1001 or STU-2001"
        }
        require(normalizedPass.length >= 4) { "Passcode must be at least 4 characters." }

        val claimRef = db.collection("portal_claims").document(normalizedCode)
        val claimSnap = claimRef.get().await()
        if (!claimSnap.exists()) {
            return Result.failure(IllegalArgumentException("No active portal found for ID $normalizedCode."))
        }
        val claim = claimSnap.toObject(PortalClaim::class.java)
            ?: return Result.failure(IllegalStateException("Unable to read portal registration."))

        if (!claim.active) {
            return Result.failure(IllegalStateException("This institute account is currently deactivated."))
        }
        if (claim.passcode != normalizedPass) {
            return Result.failure(IllegalArgumentException("Incorrect passcode for $normalizedCode."))
        }
        if (!claim.claimedByUid.isNullOrBlank() && claim.claimedByUid != user.uid) {
            return Result.failure(IllegalStateException("This Institute ID is already linked to another Google account."))
        }

        // Step 1: Claim the portal_claim doc
        claimRef.update(
            mapOf(
                "claimedByUid" to user.uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Step 2: Bind linkedUid on Teacher or Student profile
        val collectionName = if (claim.role == UserRole.TEACHER.name) "teachers" else "students"
        db.collection(collectionName).document(claim.profileId).update(
            mapOf(
                "linkedUid" to user.uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Step 3: Update user's role and institute binding
        val userRef = db.collection("users").document(user.uid)
        userRef.update(
            mapOf(
                "role" to claim.role,
                "instituteCode" to normalizedCode,
                "linkedProfileId" to claim.profileId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        logAudit(
            actorRole = UserRole.fromRaw(claim.role),
            action = "PORTAL_CLAIMED",
            targetType = collectionName,
            targetId = claim.profileId,
            details = "Bound Google account to $normalizedCode (${claim.role})"
        )

        val updated = userRef.get().await()
            .toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: error("Account updated but could not be reloaded.")
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "portal_claims/$codeInput")
        Result.failure(e)
    }

    // ==================== TEACHERS ====================

    fun observeAllTeachers(limit: Long = 100L): Flow<List<TeacherProfile>> =
        db.collection("teachers")
            .limit(limit.coerceIn(1L, 500L))
            .snapshots()
            .map { snap ->
                snap.toObjects(TeacherProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeMyTeacherProfile(uid: String): Flow<TeacherProfile?> =
        db.collection("teachers")
            .whereEqualTo("linkedUid", uid)
            .limit(1L)
            .snapshots()
            .map { snap ->
                snap.toObjects(TeacherProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .firstOrNull()
            }

    suspend fun createTeacher(
        name: String,
        email: String,
        phone: String,
        subjectSpecialty: String,
        customCode: String? = null,
        customPasscode: String? = null,
        demoRecord: Boolean = false
    ): Result<TeacherProfile> = try {
        require(name.trim().isNotEmpty()) { "Teacher name is required." }
        require(email.trim().contains("@")) { "A valid teacher email is required." }
        val teacherId = if (demoRecord && !customCode.isNullOrBlank()) {
            "tch_${customCode.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")}"
        } else {
            "tch_${shortId()}"
        }
        val code = resolveUniquePortalCode(
            prefix = "TCH",
            customCode = customCode,
            allowOverwriteExisting = demoRecord
        )
        val passcode = customPasscode?.takeIf { it.length >= 4 } ?: generatePasscode("SS")

        val teacherData = buildMap<String, Any> {
            put("teacherCode", code)
            put("name", name.trim().take(120))
            put("email", email.trim().take(160))
            if (phone.isNotBlank()) put("phone", phone.trim().take(32))
            put("subjectSpecialty", subjectSpecialty.trim().ifBlank { "Computer Science" }.take(120))
            put("passcode", passcode)
            put("active", true)
            put("demoRecord", demoRecord)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val claimData = mapOf(
            "code" to code,
            "role" to UserRole.TEACHER.name,
            "profileId" to teacherId,
            "passcode" to passcode,
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(db.collection("teachers").document(teacherId), teacherData)
        batch.set(db.collection("portal_claims").document(code), claimData)
        batch.commit().await()

        logAudit(
            actorRole = UserRole.ADMIN,
            action = "TEACHER_CREATED",
            targetType = "teachers",
            targetId = teacherId,
            details = "Created teacher $name ($code)"
        )

        Result.success(
            TeacherProfile(
                id = teacherId,
                teacherCode = code,
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim(),
                subjectSpecialty = subjectSpecialty.trim(),
                passcode = passcode,
                active = true,
                demoRecord = demoRecord
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "teachers")
        Result.failure(e)
    }

    suspend fun updateTeacher(
        teacherId: String,
        name: String,
        email: String,
        phone: String,
        subjectSpecialty: String,
        active: Boolean,
        teacherCode: String
    ): Result<Unit> = try {
        val updates = buildMap<String, Any> {
            put("name", name.trim().take(120))
            put("email", email.trim().take(160))
            put("phone", phone.trim().take(32))
            put("subjectSpecialty", subjectSpecialty.trim().take(120))
            put("active", active)
            put("updatedAt", FieldValue.serverTimestamp())
        }
        val teacherSnap = db.collection("teachers").document(teacherId).get().await()
        val linkedUid = teacherSnap.getString("linkedUid").orEmpty()
        db.collection("teachers").document(teacherId).update(updates).await()
        if (teacherCode.isNotBlank()) {
            runCatching {
                db.collection("portal_claims").document(teacherCode).update(
                    mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
        }
        if (linkedUid.isNotBlank()) {
            runCatching {
                db.collection("users").document(linkedUid).update(
                    mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
        }
        logAudit(UserRole.ADMIN, "TEACHER_UPDATED", "teachers", teacherId, "Updated teacher $name (active=$active)")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "teachers/$teacherId")
        Result.failure(e)
    }

    suspend fun deleteTeacher(teacher: TeacherProfile): Result<Unit> = try {
        val batch = db.batch()
        batch.delete(db.collection("teachers").document(teacher.id))
        if (teacher.teacherCode.isNotBlank()) {
            batch.delete(db.collection("portal_claims").document(teacher.teacherCode))
        }
        batch.commit().await()
        logAudit(UserRole.ADMIN, "TEACHER_DELETED", "teachers", teacher.id, "Removed teacher ${teacher.name}")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "teachers/${teacher.id}")
        Result.failure(e)
    }

    // ==================== STUDENTS ====================

    fun observeAllStudents(limit: Long = 150L): Flow<List<StudentProfile>> =
        db.collection("students")
            .limit(limit.coerceIn(1L, 500L))
            .snapshots()
            .map { snap ->
                snap.toObjects(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeTeacherStudents(teacherUid: String, limit: Long = 150L): Flow<List<StudentProfile>> =
        db.collection("students")
            .whereEqualTo("assignedTeacherUid", teacherUid)
            .limit(limit.coerceIn(1L, 500L))
            .snapshots()
            .map { snap ->
                snap.toObjects(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name.lowercase() }
            }

    fun observeMyStudentProfile(uid: String): Flow<StudentProfile?> =
        db.collection("students")
            .whereEqualTo("linkedUid", uid)
            .limit(1L)
            .snapshots()
            .map { snap ->
                snap.toObjects(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .firstOrNull()
            }

    suspend fun createStudent(
        name: String,
        email: String,
        phone: String,
        course: String,
        batch: String,
        teacher: TeacherProfile?,
        photoUrl: String = "",
        customCode: String? = null,
        customPasscode: String? = null,
        demoRecord: Boolean = false
    ): Result<StudentProfile> = try {
        require(name.trim().isNotEmpty()) { "Student name is required." }
        val studentId = if (demoRecord && !customCode.isNullOrBlank()) {
            "stu_${customCode.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")}"
        } else {
            "stu_${shortId()}"
        }
        val code = resolveUniquePortalCode(
            prefix = "STU",
            customCode = customCode,
            allowOverwriteExisting = demoRecord
        )
        val passcode = customPasscode?.takeIf { it.length >= 4 } ?: generatePasscode("ST")

        val studentData = buildMap<String, Any> {
            put("studentCode", code)
            put("name", name.trim().take(120))
            if (email.isNotBlank()) put("email", email.trim().take(160))
            if (phone.isNotBlank()) put("phone", phone.trim().take(32))
            if (photoUrl.isNotBlank()) put("photoUrl", photoUrl.trim().take(600))
            put("course", course.trim().ifBlank { "DCA" }.take(120))
            put("batch", batch.trim().ifBlank { "Morning-2026" }.take(80))
            teacher?.let {
                put("assignedTeacherId", it.id)
                if (it.linkedUid.isNotBlank()) put("assignedTeacherUid", it.linkedUid)
                put("assignedTeacherName", it.name)
            }
            put("passcode", passcode)
            put("active", true)
            put("demoRecord", demoRecord)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        val claimData = mapOf(
            "code" to code,
            "role" to UserRole.STUDENT.name,
            "profileId" to studentId,
            "passcode" to passcode,
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batchWrite = db.batch()
        batchWrite.set(db.collection("students").document(studentId), studentData)
        batchWrite.set(db.collection("portal_claims").document(code), claimData)
        batchWrite.commit().await()

        logAudit(
            actorRole = UserRole.ADMIN,
            action = "STUDENT_CREATED",
            targetType = "students",
            targetId = studentId,
            details = "Created student $name ($code) in $course / $batch"
        )

        Result.success(
            StudentProfile(
                id = studentId,
                studentCode = code,
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim(),
                photoUrl = photoUrl,
                course = course.trim(),
                batch = batch.trim(),
                assignedTeacherId = teacher?.id.orEmpty(),
                assignedTeacherUid = teacher?.linkedUid.orEmpty(),
                assignedTeacherName = teacher?.name.orEmpty(),
                passcode = passcode,
                active = true,
                demoRecord = demoRecord
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "students")
        Result.failure(e)
    }

    suspend fun updateStudent(
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
    ): Result<Unit> = try {
        val updates = buildMap<String, Any> {
            put("name", name.trim().take(120))
            put("email", email.trim().take(160))
            put("phone", phone.trim().take(32))
            put("photoUrl", photoUrl.trim().take(600))
            put("course", course.trim().take(120))
            put("batch", batch.trim().take(80))
            put("assignedTeacherId", teacher?.id.orEmpty())
            put("assignedTeacherUid", teacher?.linkedUid.orEmpty())
            put("assignedTeacherName", teacher?.name.orEmpty())
            put("active", active)
            put("updatedAt", FieldValue.serverTimestamp())
        }
        val studentSnap = db.collection("students").document(studentId).get().await()
        val linkedUid = studentSnap.getString("linkedUid").orEmpty()
        val prevTeacherId = studentSnap.getString("assignedTeacherId").orEmpty()
        val prevTeacherUid = studentSnap.getString("assignedTeacherUid").orEmpty()

        db.collection("students").document(studentId).update(updates).await()
        if (studentCode.isNotBlank()) {
            runCatching {
                db.collection("portal_claims").document(studentCode).update(
                    mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
        }
        if (linkedUid.isNotBlank()) {
            runCatching {
                db.collection("users").document(linkedUid).update(
                    mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
        }
        if (prevTeacherId != teacher?.id.orEmpty() || prevTeacherUid != teacher?.linkedUid.orEmpty()) {
            cascadeStudentTeacherAssignment(studentId, teacher)
        }
        logAudit(UserRole.ADMIN, "STUDENT_UPDATED", "students", studentId, "Updated student $name")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "students/$studentId")
        Result.failure(e)
    }

    suspend fun transferStudentToTeacher(student: StudentProfile, newTeacher: TeacherProfile): Result<Unit> = try {
        db.collection("students").document(student.id).update(
            mapOf(
                "assignedTeacherId" to newTeacher.id,
                "assignedTeacherUid" to newTeacher.linkedUid,
                "assignedTeacherName" to newTeacher.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        cascadeStudentTeacherAssignment(student.id, newTeacher)
        logAudit(
            UserRole.ADMIN,
            "STUDENT_TRANSFERRED",
            "students",
            student.id,
            "Transferred ${student.name} to ${newTeacher.name}"
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "students/${student.id}")
        Result.failure(e)
    }

    /**
     * Reconciles students whose assigned Teacher claimed their Google account AFTER the student
     * was created/assigned, backfilling `assignedTeacherUid` on student records, exam attempts,
     * and exam results so the Teacher dashboard immediately reflects all assigned students.
     */
    suspend fun reconcileTeacherAssignments(
        teachers: List<TeacherProfile>,
        students: List<StudentProfile>
    ): Result<Int> = try {
        val teacherById = teachers.filter { it.linkedUid.isNotBlank() }.associateBy { it.id }
        val mismatched = students.filter { stu ->
            val teacher = teacherById[stu.assignedTeacherId] ?: return@filter false
            stu.assignedTeacherUid != teacher.linkedUid || stu.assignedTeacherName != teacher.name
        }
        if (mismatched.isEmpty()) return Result.success(0)

        val ops = mutableListOf<(WriteBatch) -> Unit>()
        mismatched.forEach { stu ->
            val teacher = teacherById[stu.assignedTeacherId] ?: return@forEach
            val ref = db.collection("students").document(stu.id)
            ops += { b ->
                b.update(
                    ref,
                    mapOf(
                        "assignedTeacherUid" to teacher.linkedUid,
                        "assignedTeacherName" to teacher.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
        commitChunkedWrites(ops)
        mismatched.forEach { stu ->
            val teacher = teacherById[stu.assignedTeacherId] ?: return@forEach
            cascadeStudentTeacherAssignment(stu.id, teacher)
        }
        Result.success(mismatched.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "students/reconcile")
        Result.failure(e)
    }

    private suspend fun cascadeStudentTeacherAssignment(studentId: String, teacher: TeacherProfile?) {
        val teacherId = teacher?.id.orEmpty()
        val teacherUid = teacher?.linkedUid.orEmpty()
        val teacherName = teacher?.name.orEmpty()

        val ops = mutableListOf<(WriteBatch) -> Unit>()
        val resultsSnap = runCatching {
            db.collection("exam_results").whereEqualTo("studentId", studentId).get().await()
        }.getOrNull()
        resultsSnap?.documents?.forEach { doc ->
            ops += { b ->
                b.update(
                    doc.reference,
                    mapOf(
                        "assignedTeacherId" to teacherId,
                        "assignedTeacherUid" to teacherUid,
                        "assignedTeacherName" to teacherName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        val attemptsSnap = runCatching {
            db.collection("exam_attempts").whereEqualTo("studentId", studentId).get().await()
        }.getOrNull()
        attemptsSnap?.documents?.forEach { doc ->
            ops += { b ->
                b.update(
                    doc.reference,
                    mapOf(
                        "assignedTeacherUid" to teacherUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        if (ops.isNotEmpty()) {
            commitChunkedWrites(ops)
        }
    }

    suspend fun deleteStudent(student: StudentProfile): Result<Unit> = try {
        val batch = db.batch()
        batch.delete(db.collection("students").document(student.id))
        if (student.studentCode.isNotBlank()) {
            batch.delete(db.collection("portal_claims").document(student.studentCode))
        }
        batch.commit().await()
        logAudit(UserRole.ADMIN, "STUDENT_DELETED", "students", student.id, "Removed student ${student.name}")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "students/${student.id}")
        Result.failure(e)
    }

    // ==================== NOTIFICATIONS & AUDIT LOGS ====================

    fun observeNotifications(uid: String, limit: Long = 60L): Flow<List<NotificationItem>> =
        db.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .limit(limit.coerceIn(1L, 200L))
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeAllNotificationsAdmin(limit: Long = 60L): Flow<List<NotificationItem>> =
        db.collection("notifications")
            .limit(limit.coerceIn(1L, 200L))
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun sendNotification(
        recipientUid: String,
        title: String,
        message: String,
        category: NotificationCategory,
        relatedExamId: String = ""
    ): Result<Unit> = try {
        val senderUid = auth.currentUser?.uid ?: error("Not signed in")
        val notifId = "ntf_${shortId()}"
        val data = buildMap<String, Any> {
            put("recipientUid", recipientUid)
            put("senderUid", senderUid)
            put("title", title.take(160))
            put("message", message.take(800))
            put("category", category.name)
            if (relatedExamId.isNotBlank()) put("relatedExamId", relatedExamId)
            put("read", false)
            put("createdAt", FieldValue.serverTimestamp())
        }
        db.collection("notifications").document(notifId).set(data).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "notifications")
        Result.failure(e)
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> = try {
        db.collection("notifications").document(notificationId)
            .update("read", true)
            .await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "notifications/$notificationId")
        Result.failure(e)
    }

    fun observeAuditLogs(limit: Long = 60L): Flow<List<AuditLog>> =
        db.collection("audit_logs")
            .limit(limit.coerceIn(1L, 200L))
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun logAudit(
        actorRole: UserRole,
        action: String,
        targetType: String,
        targetId: String,
        details: String
    ) {
        try {
            val user = auth.currentUser ?: return
            val logId = "aud_${shortId()}"
            db.collection("audit_logs").document(logId).set(
                mapOf(
                    "actorUid" to user.uid,
                    "actorEmail" to (user.email ?: "member@supersonic.edu"),
                    "actorRole" to actorRole.name,
                    "action" to action.take(100),
                    "targetType" to targetType.take(64),
                    "targetId" to targetId.take(128),
                    "details" to details.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "audit_logs")
        }
    }

    private suspend fun resolveUniquePortalCode(
        prefix: String,
        customCode: String?,
        allowOverwriteExisting: Boolean
    ): String {
        val trimmedCustom = customCode?.trim()?.uppercase()
        if (!trimmedCustom.isNullOrBlank()) {
            require(trimmedCustom.matches(Regex("^[A-Za-z0-9_\\-]{2,64}$"))) {
                "Institute ID must be 2-64 letters, numbers, hyphens, or underscores."
            }
            if (!allowOverwriteExisting) {
                val existing = db.collection("portal_claims").document(trimmedCustom).get().await()
                if (existing.exists()) {
                    throw IllegalArgumentException("Institute ID $trimmedCustom is already in use. Choose a different ID.")
                }
            }
            return trimmedCustom
        }
        repeat(10) {
            val candidate = "$prefix-${Random.nextInt(1000, 9999)}"
            val existing = db.collection("portal_claims").document(candidate).get().await()
            if (!existing.exists()) return candidate
        }
        return "$prefix-${shortId().take(6).uppercase()}"
    }

    private suspend fun commitChunkedWrites(ops: List<(WriteBatch) -> Unit>) {
        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }

    private fun shortId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun generatePasscode(prefix: String): String =
        "$prefix${Random.nextInt(1000, 9999)}"
}
