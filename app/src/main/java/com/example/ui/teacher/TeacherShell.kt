package com.example.ui.teacher

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.EvaluationEngine
import com.example.core.ReportExportManager
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.StudentProfile
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.navigation.NavTransitions
import com.example.navigation.TeacherRoutes
import com.example.navigation.navigateToTopLevelTab
import com.example.ui.admin.StudentProfileDetailDialog
import com.example.ui.components.AdminRoleSwitcherBar
import com.example.ui.components.BarChartCard
import com.example.ui.components.ChartBarEntry
import com.example.ui.components.DonutBreakdownCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.InstituteCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.LoadingSkeletonList
import com.example.ui.components.QuestionAnalysisScreen
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SuperSonicLogoBadge
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
import com.example.viewmodel.TeacherViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private data class TeacherNavDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherShell(
    viewModel: TeacherViewModel,
    isAdminPreview: Boolean,
    activeWorkspaceRole: UserRole,
    onSwitchWorkspaceRole: (UserRole) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val teacherProfileState by viewModel.teacherProfileState.collectAsStateWithLifecycle()
    val studentsState by viewModel.assignedStudentsState.collectAsStateWithLifecycle()
    val examsState by viewModel.examinationsState.collectAsStateWithLifecycle()
    val resultsState by viewModel.studentResultsState.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()

    val teacherProfile = (teacherProfileState as? UiState.Success)?.data
    val students = (studentsState as? UiState.Success)?.data.orEmpty()
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val results = (resultsState as? UiState.Success)?.data.orEmpty()

    val isStudentsLoading = studentsState is UiState.Loading && students.isEmpty()
    val isExamsLoading = examsState is UiState.Loading && exams.isEmpty()
    val isResultsLoading = resultsState is UiState.Loading && results.isEmpty()

    val resultsByStudentId = remember(results) { results.groupBy { it.studentId } }
    val resultsByStudentUid = remember(results) {
        results.filter { it.studentUid.isNotBlank() }.groupBy { it.studentUid }
    }
    val publishedExams = remember(exams) { exams.filter { it.status == "PUBLISHED" } }

    val teacherNavController = rememberNavController()
    val navBackStackEntry by teacherNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDetailRoute = TeacherRoutes.isDetailRoute(currentRoute)

    var searchQuery by remember { mutableStateOf("") }
    val debouncedSearchQuery = rememberDebouncedText(searchQuery)
    var viewingStudent by remember { mutableStateOf<StudentProfile?>(null) }
    var assigningExam by remember { mutableStateOf<Examination?>(null) }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val destinations = remember {
        listOf(
            TeacherNavDest(TeacherRoutes.OVERVIEW, "Dashboard", Icons.Outlined.Dashboard, "teacher_nav_overview"),
            TeacherNavDest(TeacherRoutes.STUDENTS, "Students", Icons.Outlined.Groups, "teacher_nav_students"),
            TeacherNavDest(TeacherRoutes.ASSIGN_EXAMS, "Assign", Icons.AutoMirrored.Outlined.Assignment, "teacher_nav_assign"),
            TeacherNavDest(TeacherRoutes.RESULTS, "Results", Icons.Outlined.Assessment, "teacher_nav_results")
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!isDetailRoute) {
                    Column {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SuperSonicLogoBadge(size = 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(teacherProfile?.name ?: "Teacher Portal", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            teacherProfile?.teacherCode ?: "Super Sonic Faculty",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.testTag("teacher_sign_out_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        if (isAdminPreview) {
                            AdminRoleSwitcherBar(activeRole = activeWorkspaceRole, onSelectRole = onSwitchWorkspaceRole)
                        }
                    }
                }
            },
            bottomBar = {
                if (!useRail && !isDetailRoute) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { teacherNavController.navigateToTopLevelTab(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isDetailRoute) PaddingValues(0.dp) else innerPadding)
            ) {
                if (useRail && !isDetailRoute) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        destinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { teacherNavController.navigateToTopLevelTab(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (!isDetailRoute) {
                        FeedbackBanner(
                            message = banner.message ?: exportNotice,
                            error = banner.error,
                            onDismiss = { viewModel.clearBanner(); exportNotice = null }
                        )
                    }

                    NavHost(
                        navController = teacherNavController,
                        startDestination = TeacherRoutes.OVERVIEW,
                        modifier = Modifier.weight(1f),
                        enterTransition = { NavTransitions.tabFadeIn },
                        exitTransition = { NavTransitions.tabFadeOut }
                    ) {
                        composable(TeacherRoutes.OVERVIEW) {
                            TeacherOverviewTab(
                                students = students,
                                exams = exams,
                                results = results,
                                resultsByStudentId = resultsByStudentId,
                                resultsByStudentUid = resultsByStudentUid,
                                isLoading = isStudentsLoading || isResultsLoading
                            )
                        }

                        composable(TeacherRoutes.STUDENTS) {
                            val filtered = remember(students, debouncedSearchQuery) {
                                if (debouncedSearchQuery.isBlank()) {
                                    students
                                } else {
                                    students.filter {
                                        it.name.contains(debouncedSearchQuery, ignoreCase = true) ||
                                            it.studentCode.contains(debouncedSearchQuery, ignoreCase = true) ||
                                            it.batch.contains(debouncedSearchQuery, ignoreCase = true)
                                    }
                                }
                            }
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search assigned students by name or ID...") },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isStudentsLoading) {
                                        item { LoadingSkeletonList(cardCount = 4) }
                                    } else if (filtered.isEmpty()) {
                                        item {
                                            EmptyStateCard(
                                                icon = Icons.Outlined.Groups,
                                                title = "No Assigned Students",
                                                subtitle = "Students assigned to you by the institute Administrator will appear here."
                                            )
                                        }
                                    } else {
                                        items(filtered, key = { it.id }) { stu ->
                                            val stuRes = remember(stu.id, stu.linkedUid, resultsByStudentId, resultsByStudentUid) {
                                                val byId = resultsByStudentId[stu.id].orEmpty()
                                                val byUid = if (stu.linkedUid.isNotBlank()) resultsByStudentUid[stu.linkedUid].orEmpty() else emptyList()
                                                if (byUid.isEmpty()) byId else (byId + byUid).distinctBy { it.id }
                                            }
                                            val avg = if (stuRes.isNotEmpty()) stuRes.map { it.percentage }.average() else null
                                            val needsHelp = avg != null && avg < 55.0
                                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(stu.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            "${stu.studentCode} • ${stu.course} (${stu.batch})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = avg?.let {
                                                                String.format(Locale.getDefault(), "%d exams • Avg %.1f%%", stuRes.size, it)
                                                            } ?: "Pending first test",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = if (needsHelp) StatusError else BrandDarkBlue
                                                        )
                                                    }
                                                    Button(onClick = { viewingStudent = stu }) { Text("Profile") }
                                                }
                                            }
                                        }
                                    }
                                    item { Spacer(Modifier.height(24.dp)) }
                                }
                            }
                        }

                        composable(TeacherRoutes.ASSIGN_EXAMS) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    SectionHeader(
                                        title = "Assign Published Examinations",
                                        subtitle = "Assign tests to your assigned students and set deadlines"
                                    )
                                }
                                if (isExamsLoading) {
                                    item { LoadingSkeletonList(cardCount = 3) }
                                } else if (publishedExams.isEmpty()) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.AutoMirrored.Outlined.Assignment,
                                            title = "No Published Examinations Available",
                                            subtitle = "Published exams created by the Super Admin will appear here for assignment."
                                        )
                                    }
                                } else {
                                    items(publishedExams, key = { it.id }) { exam ->
                                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(exam.title, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "${exam.subject} • ${exam.questionCount} Qs • ${exam.durationMinutes} min • ${exam.totalMarks} Marks",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    StatusPill("Scope: ${exam.scopeEnum().label}", BrandDarkBlue, BrandSurfaceTint)
                                                    Button(
                                                        onClick = { assigningExam = exam },
                                                        enabled = students.isNotEmpty()
                                                    ) { Text("Assign Students") }
                                                }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }

                        composable(TeacherRoutes.RESULTS) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    ReportExportManager.exportResultsPdf(
                                                        context = context,
                                                        reportTitle = "Teacher Class Performance Report",
                                                        subtitle = "Faculty: ${teacherProfile?.name ?: "Super Sonic Teacher"}",
                                                        results = results
                                                    ).fold(
                                                        onSuccess = { file ->
                                                            exportNotice = "PDF report created"
                                                            ReportExportManager.shareExportedFile(context, file, "application/pdf")
                                                        },
                                                        onFailure = { exportNotice = "Export failed" }
                                                    )
                                                }
                                            },
                                            enabled = results.isNotEmpty(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Export PDF")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    ReportExportManager.exportResultsCsv(context, results).fold(
                                                        onSuccess = { file ->
                                                            exportNotice = "CSV report created"
                                                            ReportExportManager.shareExportedFile(context, file, "text/csv")
                                                        },
                                                        onFailure = { exportNotice = "Export failed" }
                                                    )
                                                }
                                            },
                                            enabled = results.isNotEmpty(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Export CSV")
                                        }
                                    }
                                }
                                if (isResultsLoading) {
                                    item { LoadingSkeletonList(cardCount = 4) }
                                } else if (results.isEmpty()) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.Outlined.Assessment,
                                            title = "No Student Submissions Yet",
                                            subtitle = "Once your assigned students complete examinations, full question-wise reports appear here."
                                        )
                                    }
                                } else {
                                    items(results, key = { it.id }) { res ->
                                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(res.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                        Text("${res.examTitle} • ${res.subject}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    StatusPill(
                                                        text = if (res.passed) "PASS" else "FAIL",
                                                        textColor = if (res.passed) StatusSuccess else StatusError,
                                                        backgroundColor = if (res.passed) StatusSuccessContainer else StatusErrorContainer
                                                    )
                                                }
                                                Text(
                                                    String.format(
                                                        Locale.getDefault(),
                                                        "%.1f/%.1f (%.1f%%) • ✓%d ✗%d ○%d • %s",
                                                        res.obtainedMarks, res.totalMarks, res.percentage,
                                                        res.correctCount, res.wrongCount, res.unattemptedCount,
                                                        EvaluationEngine.formatDuration(res.timeTakenSeconds)
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                                    color = BrandDarkBlue
                                                )
                                                Button(
                                                    onClick = {
                                                        teacherNavController.navigate(TeacherRoutes.resultAnalysis(res.id))
                                                    }
                                                ) {
                                                    Text(if (res.pendingManualCount > 0) "Grade Short Answers" else "Question-Wise Analysis")
                                                }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }

                        composable(
                            route = TeacherRoutes.RESULT_ANALYSIS,
                            arguments = listOf(navArgument(TeacherRoutes.ARG_RESULT_ID) { type = NavType.StringType }),
                            enterTransition = { NavTransitions.slideInForward(this) },
                            exitTransition = { NavTransitions.slideOutForward(this) },
                            popEnterTransition = { NavTransitions.slideInBackward(this) },
                            popExitTransition = { NavTransitions.slideOutBackward(this) }
                        ) { backStackEntry ->
                            val rawId = backStackEntry.arguments?.getString(TeacherRoutes.ARG_RESULT_ID).orEmpty()
                            val resultId = Uri.decode(rawId)
                            val inspectingResult = remember(resultId, results) {
                                results.firstOrNull { it.id == resultId }
                            }
                            if (inspectingResult != null) {
                                QuestionAnalysisScreen(
                                    result = inspectingResult,
                                    canGradeSubjective = true,
                                    canControlVisibility = false,
                                    isStudentView = false,
                                    onBack = { teacherNavController.popBackStack() },
                                    onGradeQuestion = { qId, marks ->
                                        viewModel.gradeSubjectiveAnswer(inspectingResult, qId, marks)
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Result not found or still syncing.", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = { teacherNavController.popBackStack() }) {
                                            Text("Back to Results")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewingStudent?.let { stu ->
        StudentProfileDetailDialog(
            student = stu,
            results = results.filter { it.studentId == stu.id || (stu.linkedUid.isNotBlank() && it.studentUid == stu.linkedUid) },
            onDismiss = { viewingStudent = null }
        )
    }

    assigningExam?.let { exam ->
        val chosen = remember { mutableStateListOf<String>().apply { addAll(students.map { it.id }) } }
        var daysStr by remember { mutableStateOf("7") }
        AlertDialog(
            onDismissRequest = { assigningExam = null },
            title = { Text("Assign ${exam.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = daysStr,
                        onValueChange = { daysStr = it },
                        label = { Text("Deadline (Days from now)") },
                        singleLine = true
                    )
                    Text("Select Assigned Students (${chosen.size}/${students.size}):", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(students, key = { it.id }) { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = chosen.contains(s.id),
                                    onCheckedChange = { c -> if (c) chosen.add(s.id) else chosen.remove(s.id) }
                                )
                                Text("${s.name} (${s.studentCode})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.assignExamToStudents(
                            exam = exam,
                            students = students.filter { chosen.contains(it.id) },
                            daysUntilDeadline = daysStr.toIntOrNull() ?: 7
                        )
                        assigningExam = null
                    },
                    enabled = chosen.isNotEmpty()
                ) { Text("Assign") }
            },
            dismissButton = { TextButton(onClick = { assigningExam = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TeacherOverviewTab(
    students: List<StudentProfile>,
    exams: List<Examination>,
    results: List<ExamResult>,
    resultsByStudentId: Map<String, List<ExamResult>>,
    resultsByStudentUid: Map<String, List<ExamResult>>,
    isLoading: Boolean
) {
    val activeStudents = remember(students) { students.count { it.active } }
    val avgMarks = remember(results) { if (results.isNotEmpty()) results.map { it.percentage }.average() else 0.0 }
    val needingHelp = remember(students, resultsByStudentId, resultsByStudentUid) {
        students.count { stu ->
            val byId = resultsByStudentId[stu.id].orEmpty()
            val byUid = if (stu.linkedUid.isNotBlank()) resultsByStudentUid[stu.linkedUid].orEmpty() else emptyList()
            val stuRes = if (byUid.isEmpty()) byId else (byId + byUid).distinctBy { it.id }
            stuRes.isNotEmpty() && stuRes.map { it.percentage }.average() < 55.0
        }
    }
    val passed = remember(results) { results.count { it.passed } }
    val failed = results.size - passed
    val pendingManual = remember(results) { results.count { it.pendingManualCount > 0 } }

    val subjectBars = remember(results) {
        results.groupBy { it.subject.ifBlank { "General" } }.map { (subj, list) ->
            ChartBarEntry(subj.take(10), list.map { it.percentage }.average().toFloat())
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            item { LoadingSkeletonList(cardCount = 4) }
            return@LazyColumn
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMetricCard(
                    title = "Assigned Students",
                    value = students.size.toString(),
                    subtitle = "$activeStudents active learners",
                    icon = Icons.Outlined.Groups,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Tests Assigned",
                    value = exams.size.toString(),
                    subtitle = "${results.size} completed",
                    icon = Icons.Outlined.CheckCircle,
                    accentColor = StatusSuccess,
                    accentBg = StatusSuccessContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMetricCard(
                    title = "Avg Class Score",
                    value = String.format(Locale.getDefault(), "%.1f%%", avgMarks),
                    subtitle = "Across all graded exams",
                    icon = Icons.Outlined.TrendingUp,
                    accentColor = StatusInfo,
                    accentBg = StatusInfoContainer,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Need Improvement",
                    value = needingHelp.toString(),
                    subtitle = "Students scoring < 55%",
                    icon = Icons.Outlined.WarningAmber,
                    accentColor = StatusWarning,
                    accentBg = StatusWarningContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            DonutBreakdownCard(
                title = "Assigned Class Outcomes",
                centerLabel = "Completed",
                centerValue = results.size.toString(),
                segments = listOf(
                    Triple("Passed", passed, StatusSuccess),
                    Triple("Needs Coaching", failed, StatusError),
                    Triple("Pending Review", pendingManual, Color(0xFFF59E0B))
                )
            )
        }
        item {
            BarChartCard(
                title = "Subject-Wise Class Performance",
                subtitle = "Identify weak topics among your assigned students",
                entries = subjectBars
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
