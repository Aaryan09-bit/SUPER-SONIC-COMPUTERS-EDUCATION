package com.example.ui.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unpublished
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AssignedScope
import com.example.data.model.BankQuestion
import com.example.data.model.ExamStatus
import com.example.data.model.Examination
import com.example.data.model.StudentProfile
import com.example.data.model.TeacherProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InstituteCard
import com.example.ui.components.LoadingSkeletonList
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.rememberDebouncedText
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.BrandSurfaceTint
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer

@Composable
fun AdminExamsScreen(
    exams: List<Examination>,
    questions: List<BankQuestion>,
    students: List<StudentProfile>,
    teachers: List<TeacherProfile>,
    includeDemoRecords: Boolean,
    busy: Boolean,
    onCreateExam: (
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
    ) -> Unit,
    onUpdateExamStatus: (String, ExamStatus) -> Unit,
    isLoading: Boolean = false,
    canLoadMoreQuestions: Boolean = false,
    onLoadMoreQuestions: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<ExamStatus?>(null) }

    val visibleExams = remember(exams, includeDemoRecords, statusFilter) {
        (if (includeDemoRecords) exams else exams.filterNot { it.demoRecord })
            .filter { statusFilter == null || it.statusEnum() == statusFilter }
    }

    if (isLoading && exams.isEmpty()) {
        LoadingSkeletonList(cardCount = 4)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Examination Management (${visibleExams.size})",
                subtitle = "Build tests from Question Bank, configure marking & schedules, and publish",
                trailing = {
                    Button(
                        onClick = { showCreateDialog = true },
                        enabled = !busy,
                        modifier = Modifier.testTag("create_examination_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Exam")
                    }
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("All") })
                ExamStatus.entries.forEach { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                        label = { Text(st.label) }
                    )
                }
            }
        }

        if (visibleExams.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    title = "No Examinations Yet",
                    subtitle = if (questions.isEmpty()) {
                        "First import one of the 42 PDF Sets in Papers & Bank, then tap New Exam to schedule a test."
                    } else {
                        "You have ${questions.size} questions ready in the bank. Tap New Exam to create and publish a test."
                    }
                )
            }
        } else {
            items(visibleExams, key = { it.id }) { exam ->
                val status = exam.statusEnum()
                val (sBg, sFg) = when (status) {
                    ExamStatus.PUBLISHED -> StatusSuccessContainer to StatusSuccess
                    ExamStatus.DRAFT -> StatusWarningContainer to StatusWarning
                    ExamStatus.ARCHIVED -> StatusInfoContainer to StatusInfo
                }
                InstituteCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(exam.title, style = MaterialTheme.typography.titleMedium)
                                    if (exam.demoRecord) StatusPill("DEMO", StatusWarning, StatusWarningContainer)
                                }
                                Text(
                                    "${exam.subject} • ${exam.questionCount} Qs • ${exam.durationMinutes} min • ${exam.totalMarks} Marks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(status.label, sFg, sBg)
                        }
                        Text(
                            "Pass: ${exam.passingPercentage}% • Neg: -${exam.negativeMarks} • Scope: ${exam.scopeEnum().label}" +
                                (if (exam.randomizeQuestions) " • Shuffled" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandDarkBlue
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (status != ExamStatus.PUBLISHED) {
                                Button(onClick = { onUpdateExamStatus(exam.id, ExamStatus.PUBLISHED) }) {
                                    Icon(Icons.Outlined.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Publish")
                                }
                            } else {
                                OutlinedButton(onClick = { onUpdateExamStatus(exam.id, ExamStatus.DRAFT) }) {
                                    Icon(Icons.Outlined.Unpublished, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Unpublish")
                                }
                            }
                            if (status != ExamStatus.ARCHIVED) {
                                OutlinedButton(onClick = { onUpdateExamStatus(exam.id, ExamStatus.ARCHIVED) }) {
                                    Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Archive")
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCreateDialog) {
        CreateExamDialog(
            questions = questions,
            students = students.filter { it.active },
            teachers = teachers.filter { it.active },
            canLoadMoreQuestions = canLoadMoreQuestions,
            onLoadMoreQuestions = onLoadMoreQuestions,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, desc, subj, batch, status, dur, marksQ, neg, pass, randQ, randO, showRes, allowRev, scope, selStus, tUid, selQs ->
                onCreateExam(
                    title, desc, subj, batch, status, dur, marksQ, neg, pass,
                    randQ, randO, showRes, allowRev, scope, selStus, tUid, selQs
                )
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateExamDialog(
    questions: List<BankQuestion>,
    students: List<StudentProfile>,
    teachers: List<TeacherProfile>,
    canLoadMoreQuestions: Boolean,
    onLoadMoreQuestions: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (
        String, String, String, String, ExamStatus, Int, Double, Double, Double,
        Boolean, Boolean, Boolean, Boolean, AssignedScope, List<StudentProfile>, String, List<BankQuestion>
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("MS Office") }
    var batch by remember { mutableStateOf("Morning-2026") }
    var durationStr by remember { mutableStateOf("30") }
    var marksStr by remember { mutableStateOf("2") }
    var negStr by remember { mutableStateOf("0.5") }
    var passStr by remember { mutableStateOf("40") }
    var publishNow by remember { mutableStateOf(true) }
    var randomizeQ by remember { mutableStateOf(true) }
    var randomizeO by remember { mutableStateOf(false) }
    var showImmediate by remember { mutableStateOf(true) }
    var allowReview by remember { mutableStateOf(true) }
    var scope by remember { mutableStateOf(AssignedScope.ALL) }
    var selectedTeacher by remember { mutableStateOf(teachers.firstOrNull { it.linkedUid.isNotBlank() } ?: teachers.firstOrNull()) }
    var teacherMenuOpen by remember { mutableStateOf(false) }

    var questionSearch by remember { mutableStateOf("") }
    val debouncedQuestionSearch = rememberDebouncedText(questionSearch, 200L)
    var visibleQuestionLimit by remember { mutableIntStateOf(40) }
    var studentSearch by remember { mutableStateOf("") }

    val selectedQuestionIds = remember { mutableStateListOf<String>() }
    val selectedStudentIds = remember { mutableStateListOf<String>() }

    val filteredQuestions = remember(questions, debouncedQuestionSearch) {
        if (debouncedQuestionSearch.isBlank()) questions
        else questions.filter {
            it.questionText.contains(debouncedQuestionSearch, ignoreCase = true) ||
                it.subject.contains(debouncedQuestionSearch, ignoreCase = true) ||
                it.chapter.contains(debouncedQuestionSearch, ignoreCase = true)
        }
    }
    val filteredStudents = remember(students, studentSearch) {
        if (studentSearch.isBlank()) students
        else students.filter {
            it.name.contains(studentSearch, ignoreCase = true) ||
                it.studentCode.contains(studentSearch, ignoreCase = true) ||
                it.batch.contains(studentSearch, ignoreCase = true)
        }
    }

    val durationVal = durationStr.toIntOrNull()
    val marksVal = marksStr.toDoubleOrNull()
    val negVal = negStr.toDoubleOrNull()
    val passVal = passStr.toDoubleOrNull()
    val isValidForm = title.isNotBlank() &&
        selectedQuestionIds.isNotEmpty() &&
        durationVal != null && durationVal in 1..480 &&
        marksVal != null && marksVal > 0.0 && marksVal <= 100.0 &&
        negVal != null && negVal >= 0.0 && negVal <= 50.0 &&
        passVal != null && passVal in 0.0..100.0 &&
        (scope != AssignedScope.STUDENTS || selectedStudentIds.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Online Examination") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (questions.isEmpty()) {
                    Text(
                        "Your Question Bank is empty. Please import a PDF Set or add questions in Papers & Bank first.",
                        color = StatusWarning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Examination Title *") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Instructions / Description") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject *") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text("Batch") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = durationStr, onValueChange = { durationStr = it }, label = { Text("Duration (min)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = marksStr, onValueChange = { marksStr = it }, label = { Text("Marks / Q") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = negStr, onValueChange = { negStr = it }, label = { Text("Negative Marks") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = passStr, onValueChange = { passStr = it }, label = { Text("Pass %") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                // Explicit Supervising Teacher Selector
                ExposedDropdownMenuBox(expanded = teacherMenuOpen, onExpandedChange = { teacherMenuOpen = it }) {
                    OutlinedTextField(
                        value = selectedTeacher?.let { "${it.name} (${it.teacherCode})" } ?: "Institute-Wide (No specific teacher)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Supervising Teacher") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teacherMenuOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = teacherMenuOpen, onDismissRequest = { teacherMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Institute-Wide (Admin managed)") },
                            onClick = { selectedTeacher = null; teacherMenuOpen = false }
                        )
                        teachers.forEach { t ->
                            DropdownMenuItem(
                                text = { Text("${t.name} (${t.teacherCode})${if (t.linkedUid.isBlank()) " • Pending Sign-In" else ""}") },
                                onClick = { selectedTeacher = t; teacherMenuOpen = false }
                            )
                        }
                    }
                }

                Text("Assign To Scope", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssignedScope.entries.forEach { sc ->
                        FilterChip(selected = scope == sc, onClick = { scope = sc }, label = { Text(sc.label.substringBefore(" ")) })
                    }
                }
                if (scope == AssignedScope.STUDENTS && students.isNotEmpty()) {
                    OutlinedTextField(
                        value = studentSearch,
                        onValueChange = { studentSearch = it },
                        placeholder = { Text("Search students (${selectedStudentIds.size} selected)...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    filteredStudents.take(30).forEach { stu ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedStudentIds.contains(stu.id),
                                onCheckedChange = { checked ->
                                    if (checked) selectedStudentIds.add(stu.id) else selectedStudentIds.remove(stu.id)
                                }
                            )
                            Text("${stu.name} (${stu.studentCode} • ${stu.batch})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                ToggleRow("Publish Immediately", publishNow) { publishNow = it }
                ToggleRow("Randomize Question Order", randomizeQ) { randomizeQ = it }
                ToggleRow("Randomize Option Order", randomizeO) { randomizeO = it }
                ToggleRow("Show Score Immediately", showImmediate) { showImmediate = it }
                ToggleRow("Allow Answer Key Review After Submit", allowReview) { allowReview = it }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Questions (${selectedQuestionIds.size}/${questions.size})", style = MaterialTheme.typography.titleSmall)
                    if (filteredQuestions.isNotEmpty()) {
                        TextButton(onClick = {
                            selectedQuestionIds.clear()
                            selectedQuestionIds.addAll(filteredQuestions.take(15).map { it.id })
                        }) { Text("Pick Top 15") }
                    }
                }

                OutlinedTextField(
                    value = questionSearch,
                    onValueChange = { questionSearch = it },
                    placeholder = { Text("Search bank by topic, subject, or text...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                filteredQuestions.take(visibleQuestionLimit).forEach { q ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedQuestionIds.contains(q.id),
                            onCheckedChange = { checked ->
                                if (checked) selectedQuestionIds.add(q.id) else selectedQuestionIds.remove(q.id)
                            }
                        )
                        Column {
                            Text(q.questionText.take(72), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("${q.subject} • ${q.typeEnum().name}", style = MaterialTheme.typography.labelSmall, color = BrandDarkBlue)
                        }
                    }
                }

                if (filteredQuestions.size > visibleQuestionLimit) {
                    TextButton(onClick = { visibleQuestionLimit += 40 }) {
                        Text("Show More (${filteredQuestions.size - visibleQuestionLimit} remaining)")
                    }
                } else if (canLoadMoreQuestions) {
                    TextButton(onClick = onLoadMoreQuestions) {
                        Text("Load More Questions from Cloud Bank")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosenQs = questions.filter { selectedQuestionIds.contains(it.id) }
                    val chosenStus = when (scope) {
                        AssignedScope.ALL -> students
                        AssignedScope.BATCH -> students.filter { it.batch.equals(batch, ignoreCase = true) }
                        AssignedScope.STUDENTS -> students.filter { selectedStudentIds.contains(it.id) }
                    }
                    onCreate(
                        title,
                        description,
                        subject,
                        batch,
                        if (publishNow) ExamStatus.PUBLISHED else ExamStatus.DRAFT,
                        durationVal ?: 30,
                        marksVal ?: 2.0,
                        negVal ?: 0.0,
                        passVal ?: 40.0,
                        randomizeQ,
                        randomizeO,
                        showImmediate,
                        allowReview,
                        scope,
                        chosenStus,
                        selectedTeacher?.linkedUid.orEmpty(),
                        chosenQs
                    )
                },
                enabled = isValidForm
            ) { Text("Create Exam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
