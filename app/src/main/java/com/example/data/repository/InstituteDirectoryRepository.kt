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

    fun observeAllTeachers(): Flow<List<TeacherProfile>> =
        db.collection("teachers")
            .snapshots()
            .map { snap ->
                snap.toObjects(TeacherProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeMyTeacherProfile(uid: String): Flow<TeacherProfile?> =
        db.collection("teachers")
            .whereEqualTo("linkedUid", uid)
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
        val teacherId = "tch_${shortId()}"
        val code = (customCode?.takeIf { it.isNotBlank() } ?: "TCH-${Random.nextInt(1000, 9999)}").uppercase()
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
        db.collection("teachers").document(teacherId).update(updates).await()
        if (teacherCode.isNotBlank()) {
            db.collection("portal_claims").document(teacherCode).update(
                mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
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

    fun observeAllStudents(): Flow<List<StudentProfile>> =
        db.collection("students")
            .snapshots()
            .map { snap ->
                snap.toObjects(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeTeacherStudents(teacherUid: String): Flow<List<StudentProfile>> =
        db.collection("students")
            .whereEqualTo("assignedTeacherUid", teacherUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name.lowercase() }
            }

    fun observeMyStudentProfile(uid: String): Flow<StudentProfile?> =
        db.collection("students")
            .whereEqualTo("linkedUid", uid)
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
        val studentId = "stu_${shortId()}"
        val code = (customCode?.takeIf { it.isNotBlank() } ?: "STU-${Random.nextInt(1000, 9999)}").uppercase()
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
        db.collection("students").document(studentId).update(updates).await()
        if (studentCode.isNotBlank()) {
            db.collection("portal_claims").document(studentCode).update(
                mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
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

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> =
        db.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeAllNotificationsAdmin(): Flow<List<NotificationItem>> =
        db.collection("notifications")
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    .take(50)
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

    fun observeAuditLogs(): Flow<List<AuditLog>> =
        db.collection("audit_logs")
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    .take(60)
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

    private fun shortId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun generatePasscode(prefix: String): String =
        "$prefix${Random.nextInt(1000, 9999)}"
}
