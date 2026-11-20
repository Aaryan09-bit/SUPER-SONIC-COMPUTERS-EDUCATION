package com.example.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.StudentProfile
import com.example.data.model.TeacherProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InstituteCard
import com.example.ui.components.LoadingSkeletonList
import com.example.ui.components.StatusPill
import com.example.ui.components.rememberDebouncedText
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.BrandSurfaceTint
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPeopleScreen(
    students: List<StudentProfile>,
    teachers: List<TeacherProfile>,
    exams: List<Examination>,
    results: List<ExamResult>,
    includeDemoRecords: Boolean,
    onCreateStudent: (String, String, String, String, String, TeacherProfile?, String) -> Unit,
    onUpdateStudent: (String, String, String, String, String, String, String, TeacherProfile?, String, Boolean) -> Unit,
    onTransferStudent: (StudentProfile, TeacherProfile) -> Unit,
    onDeleteStudent: (StudentProfile) -> Unit,
    onCreateTeacher: (String, String, String, String) -> Unit,
    onUpdateTeacher: (String, String, String, String, String, Boolean, String) -> Unit,
    onDeleteTeacher: (TeacherProfile) -> Unit,
    isLoading: Boolean = false,
    canLoadMoreStudents: Boolean = false,
    onLoadMoreStudents: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var onlyUnassignedStudents by remember { mutableStateOf(false) }
    val debouncedQuery = rememberDebouncedText(searchQuery)

    var editingStudent by remember { mutableStateOf<StudentProfile?>(null) }
    var showCreateStudent by remember { mutableStateOf(false) }
    var viewingStudentProfile by remember { mutableStateOf<StudentProfile?>(null) }
    var transferringStudent by remember { mutableStateOf<StudentProfile?>(null) }

    var editingTeacher by remember { mutableStateOf<TeacherProfile?>(null) }
    var showCreateTeacher by remember { mutableStateOf(false) }

    val baseStudents = remember(students, includeDemoRecords) {
        if (includeDemoRecords) students else students.filterNot { it.demoRecord }
    }
    val unassignedCount = remember(baseStudents) {
        baseStudents.count { it.assignedTeacherId.isBlank() }
    }
    val visibleStudents = remember(baseStudents, debouncedQuery, onlyUnassignedStudents) {
        baseStudents.filter { stu ->
            val matchesAssign = !onlyUnassignedStudents || stu.assignedTeacherId.isBlank()
            val matchesQuery = debouncedQuery.isBlank() ||
                stu.name.contains(debouncedQuery, ignoreCase = true) ||
                stu.studentCode.contains(debouncedQuery, ignoreCase = true) ||
                stu.batch.contains(debouncedQuery, ignoreCase = true) ||
                stu.course.contains(debouncedQuery, ignoreCase = true)
            matchesAssign && matchesQuery
        }
    }
    val visibleTeachers = remember(teachers, includeDemoRecords, debouncedQuery) {
        (if (includeDemoRecords) teachers else teachers.filterNot { it.demoRecord })
            .filter {
                debouncedQuery.isBlank() ||
                    it.name.contains(debouncedQuery, ignoreCase = true) ||
                    it.teacherCode.contains(debouncedQuery, ignoreCase = true) ||
                    it.subjectSpecialty.contains(debouncedQuery, ignoreCase = true)
            }
    }

    // O(1) pre-indexed lookup maps to avoid O(N*M) nested loops during LazyColumn scroll
    val resultsByStudentId = remember(results) {
        results.filter { it.studentId.isNotBlank() }.groupBy { it.studentId }
    }
    val resultsByStudentUid = remember(results) {
        results.filter { it.studentUid.isNotBlank() }.groupBy { it.studentUid }
    }
    val studentsCountByTeacherId = remember(students) {
        students.filter { it.assignedTeacherId.isNotBlank() }.groupingBy { it.assignedTeacherId }.eachCount()
    }
    val resultsByTeacherId = remember(results) {
        results.filter { it.assignedTeacherId.isNotBlank() }.groupBy { it.assignedTeacherId }
    }
    val resultsByTeacherUid = remember(results) {
        results.filter { it.assignedTeacherUid.isNotBlank() }.groupBy { it.assignedTeacherUid }
    }
    val examCountByTeacherUid = remember(exams) {
        exams.filter { it.assignedTeacherUid.isNotBlank() }.groupingBy { it.assignedTeacherUid }.eachCount()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Students (${visibleStudents.size})") },
                modifier = Modifier.testTag("people_students_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Teachers (${visibleTeachers.size})") },
                modifier = Modifier.testTag("people_teachers_tab")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (selectedTab == 0) "Search name, ID, batch..." else "Search teacher or subject...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { if (selectedTab == 0) showCreateStudent = true else showCreateTeacher = true },
                modifier = Modifier
                    .height(52.dp)
                    .testTag(if (selectedTab == 0) "add_student_button" else "add_teacher_button")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        if (selectedTab == 0 && unassignedCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !onlyUnassignedStudents,
                    onClick = { onlyUnassignedStudents = false },
                    label = { Text("All (${baseStudents.size})") }
                )
                FilterChip(
                    selected = onlyUnassignedStudents,
                    onClick = { onlyUnassignedStudents = true },
                    label = { Text("Unassigned ($unassignedCount)") },
                    modifier = Modifier.testTag("filter_unassigned_students")
                )
            }
        }

        if (isLoading && students.isEmpty() && teachers.isEmpty()) {
            LoadingSkeletonList(cardCount = 4)
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedTab == 0) {
                if (visibleStudents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.Groups,
                            title = "No Students Found",
                            subtitle = "Add students with auto-generated Student ID & Passcode, assign batches and teachers."
                        )
                    }
                } else {
                    items(visibleStudents, key = { it.id }) { stu ->
                        val stuResults = remember(stu.id, stu.linkedUid, resultsByStudentId, resultsByStudentUid) {
                            (resultsByStudentId[stu.id].orEmpty() +
                                if (stu.linkedUid.isNotBlank()) resultsByStudentUid[stu.linkedUid].orEmpty() else emptyList())
                                .distinctBy { it.id }
                        }
                        val avgPct = if (stuResults.isNotEmpty()) stuResults.map { it.percentage }.average() else null
                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(BrandSurfaceTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (stu.photoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = stu.photoUrl,
                                                contentDescription = stu.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Outlined.Person, contentDescription = null, tint = BrandDarkBlue)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(stu.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            if (stu.demoRecord) {
                                                StatusPill("DEMO", StatusWarning, StatusWarningContainer)
                                            }
                                        }
                                        Text(
                                            "${stu.studentCode} • Pass: ${stu.passcode}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                            color = BrandDarkBlue
                                        )
                                        Text(
                                            "${stu.course} • ${stu.batch} • Teacher: ${stu.assignedTeacherName.ifBlank { "Unassigned" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(
                                        text = if (stu.active) "Active" else "Inactive",
                                        textColor = if (stu.active) StatusSuccess else StatusError,
                                        backgroundColor = if (stu.active) StatusSuccessContainer else StatusErrorContainer
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (avgPct != null) {
                                            String.format(Locale.getDefault(), "%d exams • Avg %.1f%%", stuResults.size, avgPct)
                                        } else "No exams taken yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { viewingStudentProfile = stu }) { Text("Profile") }
                                        IconButton(onClick = { transferringStudent = stu }) {
                                            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Transfer Teacher", tint = BrandDarkBlue)
                                        }
                                        IconButton(onClick = { editingStudent = stu }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Student")
                                        }
                                        IconButton(onClick = { onDeleteStudent(stu) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Student", tint = StatusError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (canLoadMoreStudents) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMoreStudents,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("load_more_students_button")
                            ) {
                                Text("Load More Students")
                            }
                        }
                    }
                }
            } else {
                if (visibleTeachers.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.School,
                            title = "No Teachers Added",
                            subtitle = "Create faculty accounts with unique Teacher IDs & Passcodes and assign students."
                        )
                    }
                } else {
                    items(visibleTeachers, key = { it.id }) { tch ->
                        val assignedStudentCount = studentsCountByTeacherId[tch.id] ?: 0
                        val teacherResults = remember(tch.id, tch.linkedUid, resultsByTeacherId, resultsByTeacherUid) {
                            (resultsByTeacherId[tch.id].orEmpty() +
                                if (tch.linkedUid.isNotBlank()) resultsByTeacherUid[tch.linkedUid].orEmpty() else emptyList())
                                .distinctBy { it.id }
                        }
                        val teacherExams = if (tch.linkedUid.isNotBlank()) (examCountByTeacherUid[tch.linkedUid] ?: 0) else 0
                        val avg = if (teacherResults.isNotEmpty()) teacherResults.map { it.percentage }.average() else 0.0
                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(tch.name, style = MaterialTheme.typography.titleMedium)
                                            if (tch.demoRecord) StatusPill("DEMO", StatusWarning, StatusWarningContainer)
                                        }
                                        Text(
                                            "${tch.teacherCode} • Pass: ${tch.passcode}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                            color = BrandDarkBlue
                                        )
                                        Text(
                                            "${tch.subjectSpecialty} • ${tch.email}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(
                                        text = if (tch.active) "Active" else "Inactive",
                                        textColor = if (tch.active) StatusSuccess else StatusError,
                                        backgroundColor = if (tch.active) StatusSuccessContainer else StatusErrorContainer
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(
                                            Locale.getDefault(),
                                            "%d Students • %d Tests • Avg %.1f%%",
                                            assignedStudentCount, teacherExams, avg
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusInfo
                                    )
                                    Row {
                                        IconButton(onClick = { editingTeacher = tch }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Teacher")
                                        }
                                        IconButton(onClick = { onDeleteTeacher(tch) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Teacher", tint = StatusError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showCreateStudent || editingStudent != null) {
        StudentFormDialog(
            existing = editingStudent,
            teachers = teachers.filter { it.active },
            onDismiss = { showCreateStudent = false; editingStudent = null },
            onSave = { name, email, phone, course, batch, teacher, photoUrl, active ->
                val cur = editingStudent
                if (cur == null) {
                    onCreateStudent(name, email, phone, course, batch, teacher, photoUrl)
                } else {
                    onUpdateStudent(cur.id, cur.studentCode, name, email, phone, course, batch, teacher, photoUrl, active)
                }
                showCreateStudent = false
                editingStudent = null
            }
        )
    }

    if (showCreateTeacher || editingTeacher != null) {
        TeacherFormDialog(
            existing = editingTeacher,
            onDismiss = { showCreateTeacher = false; editingTeacher = null },
            onSave = { name, email, phone, specialty, active ->
                val cur = editingTeacher
                if (cur == null) {
                    onCreateTeacher(name, email, phone, specialty)
                } else {
                    onUpdateTeacher(cur.id, name, email, phone, specialty, active, cur.teacherCode)
                }
                showCreateTeacher = false
                editingTeacher = null
            }
        )
    }

    viewingStudentProfile?.let { stu ->
        StudentProfileDetailDialog(
            student = stu,
            results = results.filter { it.studentId == stu.id || (stu.linkedUid.isNotBlank() && it.studentUid == stu.linkedUid) },
            onDismiss = { viewingStudentProfile = null }
        )
    }

    transferringStudent?.let { stu ->
        AlertDialog(
            onDismissRequest = { transferringStudent = null },
            title = { Text("Transfer ${stu.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select new assigned teacher:", style = MaterialTheme.typography.bodySmall)
                    teachers.filter { it.active }.forEach { t ->
                        OutlinedButton(
                            onClick = {
                                onTransferStudent(stu, t)
                                transferringStudent = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${t.name} (${t.teacherCode})")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { transferringStudent = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentFormDialog(
    existing: StudentProfile?,
    teachers: List<TeacherProfile>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, TeacherProfile?, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var course by remember { mutableStateOf(existing?.course?.ifBlank { "DCA" } ?: "DCA") }
    var batch by remember { mutableStateOf(existing?.batch?.ifBlank { "Morning-2026" } ?: "Morning-2026") }
    var photoUrl by remember { mutableStateOf(existing?.photoUrl.orEmpty()) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var selectedTeacher by remember {
        mutableStateOf(teachers.firstOrNull { it.id == existing?.assignedTeacherId })
    }
    var teacherMenuOpen by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker (Google Play compliant)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUrl = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add New Student" else "Edit Student") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (photoUrl.isBlank()) "Pick Photo" else "Photo Selected")
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true)
                OutlinedTextField(value = course, onValueChange = { course = it }, label = { Text("Course (e.g. DCA / ADCA / CCC) *") }, singleLine = true)
                OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text("Batch *") }, singleLine = true)

                ExposedDropdownMenuBox(expanded = teacherMenuOpen, onExpandedChange = { teacherMenuOpen = it }) {
                    OutlinedTextField(
                        value = selectedTeacher?.let { "${it.name} (${it.teacherCode})" } ?: "Unassigned",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assign Teacher") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teacherMenuOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = teacherMenuOpen, onDismissRequest = { teacherMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Unassigned") }, onClick = { selectedTeacher = null; teacherMenuOpen = false })
                        teachers.forEach { t ->
                            DropdownMenuItem(
                                text = { Text("${t.name} (${t.teacherCode})") },
                                onClick = { selectedTeacher = t; teacherMenuOpen = false }
                            )
                        }
                    }
                }

                if (existing != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Account Active")
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, email, phone, course, batch, selectedTeacher, photoUrl, active) },
                enabled = name.isNotBlank() && course.isNotBlank() && batch.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TeacherFormDialog(
    existing: TeacherProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var specialty by remember { mutableStateOf(existing?.subjectSpecialty?.ifBlank { "MS Office & Computer Fundamentals" } ?: "MS Office & Computer Fundamentals") }
    var active by remember { mutableStateOf(existing?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create Teacher Account" else "Edit Teacher") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Teacher Name *") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email *") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(value = specialty, onValueChange = { specialty = it }, label = { Text("Subject Specialty *") }, singleLine = true)
                if (existing != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Account Active")
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, email, phone, specialty, active) },
                enabled = name.isNotBlank() && email.contains("@") && specialty.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun StudentProfileDetailDialog(
    student: StudentProfile,
    results: List<ExamResult>,
    onDismiss: () -> Unit
) {
    val totalObtained = results.sumOf { it.obtainedMarks }
    val totalPossible = results.sumOf { it.totalMarks }
    val avgPct = if (results.isNotEmpty()) results.map { it.percentage }.average() else 0.0
    val subjectAverages = results.groupBy { it.subject }
        .mapValues { (_, list) -> list.map { it.percentage }.average() }
    val strongSubjects = subjectAverages.filter { it.value >= 70.0 }.keys.joinToString(", ").ifBlank { "Building baseline" }
    val weakSubjects = subjectAverages.filter { it.value < 55.0 }.keys.joinToString(", ").ifBlank { "None flagged" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(student.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Student ID: ${student.studentCode}  •  Passcode: ${student.passcode}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily), color = BrandDarkBlue)
                Text("Course / Batch: ${student.course} / ${student.batch}", style = MaterialTheme.typography.bodyMedium)
                Text("Assigned Teacher: ${student.assignedTeacherName.ifBlank { "Unassigned" }}", style = MaterialTheme.typography.bodyMedium)
                if (student.email.isNotBlank() || student.phone.isNotBlank()) {
                    Text("Contact: ${student.email} ${student.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(
                    text = String.format(Locale.getDefault(), "Avg: %.1f%% • Marks: %.1f/%.1f", avgPct, totalObtained, totalPossible),
                    textColor = BrandDarkBlue,
                    backgroundColor = StatusInfoContainer
                )
                Text("Strong Subjects: $strongSubjects", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                Text("Weak Subjects: $weakSubjects", style = MaterialTheme.typography.bodySmall, color = StatusError)

                Spacer(Modifier.height(4.dp))
                Text("Examination History (${results.size})", style = MaterialTheme.typography.titleSmall)
                if (results.isEmpty()) {
                    Text("No examinations completed yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    results.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(r.examTitle.take(24), style = MaterialTheme.typography.bodySmall)
                            Text(
                                String.format(Locale.getDefault(), "%.1f%% (%s)", r.percentage, if (r.passed) "PASS" else "FAIL"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (r.passed) StatusSuccess else StatusError
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}
