package com.example.ui.student

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.EvaluationEngine
import com.example.data.model.AttemptStatus
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.example.navigation.NavTransitions
import com.example.navigation.StudentRoutes
import com.example.navigation.navigateToTopLevelTab
import com.example.ui.components.AdminRoleSwitcherBar
import com.example.ui.components.BarChartCard
import com.example.ui.components.ChartBarEntry
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.InstituteCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.LoadingSkeletonList
import com.example.ui.components.QuestionAnalysisScreen
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SuperSonicLogoBadge
import com.example.ui.components.TrendLineChartCard
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.viewmodel.ExamSessionViewModel
import com.example.viewmodel.StudentViewModel
import java.util.Locale

private data class StudentNavDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentShell(
    studentViewModel: StudentViewModel,
    examSessionViewModel: ExamSessionViewModel,
    userAccount: UserAccount?,
    isAdminPreview: Boolean,
    activeWorkspaceRole: UserRole,
    authMessage: String?,
    authError: String?,
    claimingCode: Boolean,
    onClaimPortalCode: (String, String) -> Unit,
    onClearAuthMessages: () -> Unit,
    onSwitchWorkspaceRole: (UserRole) -> Unit,
    onSignOut: () -> Unit
) {
    val profileState by studentViewModel.studentProfileState.collectAsStateWithLifecycle()
    val examsState by studentViewModel.availableExamsState.collectAsStateWithLifecycle()
    val attemptsState by studentViewModel.myAttemptsState.collectAsStateWithLifecycle()
    val resultsState by studentViewModel.myResultsState.collectAsStateWithLifecycle()

    val studentProfile = (profileState as? UiState.Success)?.data
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val attempts = (attemptsState as? UiState.Success)?.data.orEmpty()
    val results = (resultsState as? UiState.Success)?.data.orEmpty()

    val studentNavController = rememberNavController()
    val navBackStackEntry by studentNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isFullScreenRoute = StudentRoutes.isFullScreenRoute(currentRoute)

    var instructionsExam by remember { mutableStateOf<Examination?>(null) }
    var lastSubmittedResult by remember { mutableStateOf<ExamResult?>(null) }
    var showClaimDialog by remember { mutableStateOf(false) }

    val isExamsLoading = examsState is UiState.Loading && exams.isEmpty()
    val isResultsLoading = resultsState is UiState.Loading && results.isEmpty()

    val submittedExamIds = remember(attempts, results) {
        (attempts.filter { it.statusEnum() == AttemptStatus.SUBMITTED }.map { it.examId } + results.map { it.examId }).toSet()
    }
    val inProgressExamIds = remember(attempts) {
        attempts.filter { it.statusEnum() == AttemptStatus.IN_PROGRESS }.map { it.examId }.toSet()
    }
    val pendingExams = remember(exams, submittedExamIds) {
        exams.filterNot { submittedExamIds.contains(it.id) }
    }
    val visibleResults = remember(results) {
        results.filter { it.resultsVisibleToStudent }
    }
    val resultByExamId = remember(results) {
        results.associateBy { it.examId }
    }
    val avgPct = remember(visibleResults) {
        if (visibleResults.isNotEmpty()) visibleResults.map { it.percentage }.average() else 0.0
    }
    val highestPct = remember(visibleResults) {
        visibleResults.maxOfOrNull { it.percentage } ?: 0.0
    }
    val passedCount = remember(visibleResults) {
        visibleResults.count { it.passed }
    }

    val destinations = remember {
        listOf(
            StudentNavDest(StudentRoutes.DASHBOARD, "Dashboard", Icons.Outlined.Dashboard, "student_nav_dashboard"),
            StudentNavDest(StudentRoutes.EXAMS, "Exams", Icons.AutoMirrored.Outlined.Assignment, "student_nav_exams"),
            StudentNavDest(StudentRoutes.PROGRESS, "Progress", Icons.Outlined.Timeline, "student_nav_progress")
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!isFullScreenRoute) {
                    Column {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SuperSonicLogoBadge(size = 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            studentProfile?.name ?: userAccount?.displayName ?: "Student Portal",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            studentProfile?.studentCode?.let { "$it • ${studentProfile.batch}" } ?: "Super Sonic Student",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (!isAdminPreview && studentProfile == null) {
                                    IconButton(
                                        onClick = { showClaimDialog = true },
                                        modifier = Modifier.testTag("link_institute_id_button")
                                    ) {
                                        Icon(Icons.Outlined.Key, contentDescription = "Link Institute ID")
                                    }
                                }
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.testTag("student_sign_out_button")
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
                if (!useRail && !isFullScreenRoute) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { studentNavController.navigateToTopLevelTab(dest.route) },
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
                    .padding(if (isFullScreenRoute) PaddingValues(0.dp) else innerPadding)
            ) {
                if (useRail && !isFullScreenRoute) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        destinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { studentNavController.navigateToTopLevelTab(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (!isFullScreenRoute) {
                        FeedbackBanner(message = authMessage, error = authError, onDismiss = onClearAuthMessages)
                    }

                    NavHost(
                        navController = studentNavController,
                        startDestination = StudentRoutes.DASHBOARD,
                        modifier = Modifier.weight(1f),
                        enterTransition = { NavTransitions.tabFadeIn },
                        exitTransition = { NavTransitions.tabFadeOut }
                    ) {
                        composable(StudentRoutes.DASHBOARD) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    InstituteCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        containerColor = BrandDarkBlue
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("SUPER SONIC COMPUTERS EDUCATION", style = MaterialTheme.typography.labelSmall, color = Color(0xFF70C5F0))
                                            Text(
                                                "Welcome, ${studentProfile?.name ?: userAccount?.displayName ?: "Learner"}",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = Color.White
                                            )
                                            Text(
                                                text = if (studentProfile != null) {
                                                    "ID: ${studentProfile.studentCode} • ${studentProfile.course} • Teacher: ${studentProfile.assignedTeacherName.ifBlank { "Institute Faculty" }}"
                                                } else {
                                                    "Learn Smart. Practice More. Achieve Excellence."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFD7F0FD)
                                            )
                                            if (!isAdminPreview && studentProfile == null) {
                                                Spacer(Modifier.height(6.dp))
                                                OutlinedButton(
                                                    onClick = { showClaimDialog = true },
                                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                                ) {
                                                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Link Institute ID & Passcode (Student / Teacher)")
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        KpiMetricCard(
                                            title = "Available Tests",
                                            value = pendingExams.size.toString(),
                                            subtitle = "${exams.size} total assigned",
                                            icon = Icons.Outlined.Schedule,
                                            modifier = Modifier.weight(1f)
                                        )
                                        KpiMetricCard(
                                            title = "Completed",
                                            value = results.size.toString(),
                                            subtitle = String.format(Locale.getDefault(), "Overall %.1f%%", avgPct),
                                            icon = Icons.Outlined.CheckCircle,
                                            accentColor = StatusSuccess,
                                            accentBg = StatusSuccessContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                visibleResults.firstOrNull()?.let { latest ->
                                    item { SectionHeader(title = "Latest Result") }
                                    item {
                                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(latest.examTitle, style = MaterialTheme.typography.titleMedium)
                                                        Text(latest.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    StatusPill(
                                                        text = if (latest.passed) "PASSED" else "RETRY",
                                                        textColor = if (latest.passed) StatusSuccess else StatusError,
                                                        backgroundColor = if (latest.passed) StatusSuccessContainer else StatusErrorContainer
                                                    )
                                                }
                                                Text(
                                                    String.format(
                                                        Locale.getDefault(),
                                                        "Score: %.1f/%.1f (%.1f%%) • Correct: %d/%d",
                                                        latest.obtainedMarks, latest.totalMarks, latest.percentage,
                                                        latest.correctCount, latest.totalQuestions
                                                    ),
                                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                                    color = BrandDarkBlue
                                                )
                                                Button(
                                                    onClick = {
                                                        studentNavController.navigate(StudentRoutes.resultAnalysis(latest.id))
                                                    }
                                                ) { Text("View Question Analysis") }
                                            }
                                        }
                                    }
                                }

                                item {
                                    SectionHeader(
                                        title = "Up Next (${pendingExams.size})",
                                        trailing = {
                                            TextButton(
                                                onClick = { studentNavController.navigateToTopLevelTab(StudentRoutes.EXAMS) }
                                            ) { Text("All Exams") }
                                        }
                                    )
                                }
                                if (isExamsLoading) {
                                    item { LoadingSkeletonList(cardCount = 2) }
                                } else if (pendingExams.isEmpty()) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.AutoMirrored.Outlined.Assignment,
                                            title = "All Caught Up",
                                            subtitle = "No pending examinations right now. Check back when your teacher publishes a new test."
                                        )
                                    }
                                } else {
                                    items(pendingExams.take(5), key = { it.id }) { exam ->
                                        ExamCardItem(
                                            exam = exam,
                                            completed = false,
                                            inProgress = inProgressExamIds.contains(exam.id),
                                            onStartClick = { instructionsExam = exam },
                                            onViewResultClick = {}
                                        )
                                    }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }

                        composable(StudentRoutes.EXAMS) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    SectionHeader(
                                        title = "Available Examinations (${exams.size})",
                                        subtitle = "Timed online tests with automatic evaluation"
                                    )
                                }
                                if (isExamsLoading) {
                                    item { LoadingSkeletonList(cardCount = 3) }
                                } else if (exams.isEmpty()) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.AutoMirrored.Outlined.Assignment,
                                            title = "No Published Examinations",
                                            subtitle = "Published examinations assigned to you or your batch will appear here."
                                        )
                                    }
                                } else {
                                    items(exams, key = { it.id }) { exam ->
                                        val doneRes = resultByExamId[exam.id]
                                        ExamCardItem(
                                            exam = exam,
                                            completed = submittedExamIds.contains(exam.id),
                                            inProgress = inProgressExamIds.contains(exam.id),
                                            onStartClick = { instructionsExam = exam },
                                            onViewResultClick = {
                                                doneRes?.let {
                                                    studentNavController.navigate(StudentRoutes.resultAnalysis(it.id))
                                                }
                                            }
                                        )
                                    }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }

                        composable(StudentRoutes.PROGRESS) {
                            val trendPoints = remember(visibleResults) {
                                visibleResults.sortedBy { it.submittedAt?.seconds ?: 0L }
                                    .mapIndexed { idx, r -> "E${idx + 1}" to r.percentage.toFloat() }
                            }
                            val subjectBars = remember(visibleResults) {
                                visibleResults.groupBy { it.subject.ifBlank { "General" } }
                                    .map { (subj, list) -> ChartBarEntry(subj.take(10), list.map { it.percentage }.average().toFloat()) }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        KpiMetricCard(
                                            title = "Average Score",
                                            value = String.format(Locale.getDefault(), "%.1f%%", avgPct),
                                            subtitle = "${visibleResults.size} graded exams",
                                            icon = Icons.Outlined.Timeline,
                                            modifier = Modifier.weight(1f)
                                        )
                                        KpiMetricCard(
                                            title = "Highest Score",
                                            value = String.format(Locale.getDefault(), "%.1f%%", highestPct),
                                            subtitle = "$passedCount passed",
                                            icon = Icons.Outlined.EmojiEvents,
                                            accentColor = StatusSuccess,
                                            accentBg = StatusSuccessContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                item {
                                    TrendLineChartCard(
                                        title = "Score Improvement Over Time",
                                        subtitle = "Track your progression across completed examinations",
                                        points = trendPoints
                                    )
                                }
                                item {
                                    BarChartCard(
                                        title = "Subject-Wise Mastery",
                                        subtitle = "Average percentage by computer subject",
                                        entries = subjectBars
                                    )
                                }
                                item { SectionHeader(title = "Examination History (${results.size})") }
                                if (isResultsLoading) {
                                    item { LoadingSkeletonList(cardCount = 3) }
                                } else if (results.isEmpty()) {
                                    item {
                                        EmptyStateCard(
                                            icon = Icons.Outlined.Timeline,
                                            title = "No Performance History Yet",
                                            subtitle = "Complete your first examination to unlock score charts and question-wise reviews."
                                        )
                                    }
                                } else {
                                    items(results, key = { it.id }) { res ->
                                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(res.examTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        "${res.subject} • ${EvaluationEngine.formatDuration(res.timeTakenSeconds)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (res.resultsVisibleToStudent) {
                                                        Text(
                                                            String.format(Locale.getDefault(), "%.1f/%.1f (%.1f%%)", res.obtainedMarks, res.totalMarks, res.percentage),
                                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                                            color = if (res.passed) StatusSuccess else StatusError
                                                        )
                                                    } else {
                                                        Text("Awaiting admin result release", style = MaterialTheme.typography.labelSmall, color = StatusWarning)
                                                    }
                                                }
                                                Button(
                                                    onClick = {
                                                        studentNavController.navigate(StudentRoutes.resultAnalysis(res.id))
                                                    }
                                                ) { Text("Review") }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }

                        // Distraction-Free Timed Exam Session Route
                        composable(
                            route = StudentRoutes.EXAM_SESSION,
                            arguments = listOf(navArgument(StudentRoutes.ARG_EXAM_ID) { type = NavType.StringType }),
                            enterTransition = { NavTransitions.slideInForward(this) },
                            exitTransition = { NavTransitions.slideOutForward(this) },
                            popEnterTransition = { NavTransitions.slideInBackward(this) },
                            popExitTransition = { NavTransitions.slideOutBackward(this) }
                        ) {
                            ExamTakingScreen(
                                viewModel = examSessionViewModel,
                                onExitWithoutSubmit = {
                                    examSessionViewModel.resetSession()
                                    studentNavController.popBackStack()
                                },
                                onCompletedResult = { result ->
                                    lastSubmittedResult = result
                                    examSessionViewModel.resetSession()
                                    studentNavController.navigate(StudentRoutes.resultAnalysis(result.id)) {
                                        popUpTo(StudentRoutes.EXAM_SESSION) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        // Question-Wise Result Review Route
                        composable(
                            route = StudentRoutes.RESULT_ANALYSIS,
                            arguments = listOf(navArgument(StudentRoutes.ARG_RESULT_ID) { type = NavType.StringType }),
                            enterTransition = { NavTransitions.slideInForward(this) },
                            exitTransition = { NavTransitions.slideOutForward(this) },
                            popEnterTransition = { NavTransitions.slideInBackward(this) },
                            popExitTransition = { NavTransitions.slideOutBackward(this) }
                        ) { backStackEntry ->
                            val rawId = backStackEntry.arguments?.getString(StudentRoutes.ARG_RESULT_ID).orEmpty()
                            val resultId = Uri.decode(rawId)
                            val inspectingResult = remember(resultId, results, lastSubmittedResult) {
                                results.firstOrNull { it.id == resultId }
                                    ?: lastSubmittedResult?.takeIf { it.id == resultId }
                            }
                            when {
                                inspectingResult == null -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Loading examination result…", style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.height(12.dp))
                                            Button(onClick = { studentNavController.popBackStack() }) {
                                                Text("Back")
                                            }
                                        }
                                    }
                                }
                                !inspectingResult.resultsVisibleToStudent -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(
                                                modifier = Modifier.padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.CheckCircle,
                                                    contentDescription = null,
                                                    tint = StatusSuccess,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Text("Examination Submitted", style = MaterialTheme.typography.titleLarge)
                                                Text(
                                                    "Your responses for '${inspectingResult.examTitle}' have been securely submitted. Results will appear once released by your institute administrator.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Button(onClick = { studentNavController.popBackStack() }) {
                                                    Text("Return to Dashboard")
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    QuestionAnalysisScreen(
                                        result = inspectingResult,
                                        canGradeSubjective = false,
                                        canControlVisibility = false,
                                        isStudentView = true,
                                        onBack = { studentNavController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Exam Instructions Dialog before Starting Timed Session
    instructionsExam?.let { exam ->
        AlertDialog(
            onDismissRequest = { instructionsExam = null },
            title = { Text(exam.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (exam.description.isNotBlank()) {
                        Text(exam.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Subject: ${exam.subject}", style = MaterialTheme.typography.bodySmall)
                    Text("Total Questions: ${exam.questionCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Total Marks: ${exam.totalMarks} (${exam.marksPerQuestion} per question)", style = MaterialTheme.typography.bodySmall)
                    Text("Negative Marking: ${if (exam.negativeMarks > 0.0) "-${exam.negativeMarks} per wrong answer" else "None"}", style = MaterialTheme.typography.bodySmall)
                    Text("Duration: ${exam.durationMinutes} minutes (auto-submits when timer ends)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = BrandDarkBlue)
                    Text("Passing Requirement: ${exam.passingPercentage}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = exam
                        instructionsExam = null
                        examSessionViewModel.beginSession(
                            exam = chosen,
                            studentProfile = studentProfile,
                            studentFallbackName = userAccount?.displayName ?: "Student"
                        )
                        studentNavController.navigate(StudentRoutes.examSession(chosen.id))
                    },
                    modifier = Modifier.testTag("start_exam_confirm_button")
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Begin Examination")
                }
            },
            dismissButton = { TextButton(onClick = { instructionsExam = null }) { Text("Cancel") } }
        )
    }

    // Link Institute ID & Passcode Dialog (for Admin-created Student or Teacher accounts)
    if (showClaimDialog) {
        var code by remember { mutableStateOf("") }
        var passcode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showClaimDialog = false },
            title = { Text("Link Institute ID & Passcode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the Student ID (e.g. STU-1234) or Teacher ID (e.g. TCH-1234) and passcode provided by Super Sonic Computers Education.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Institute ID (STU-xxxx / TCH-xxxx)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = { passcode = it },
                        label = { Text("Passcode") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClaimPortalCode(code, passcode)
                        showClaimDialog = false
                    },
                    enabled = !claimingCode && code.isNotBlank() && passcode.length >= 4
                ) { Text("Verify & Link") }
            },
            dismissButton = { TextButton(onClick = { showClaimDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ExamCardItem(
    exam: Examination,
    completed: Boolean,
    inProgress: Boolean,
    onStartClick: () -> Unit,
    onViewResultClick: () -> Unit
) {
    InstituteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exam.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${exam.subject} • ${exam.questionCount} Questions • ${exam.durationMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = when {
                        completed -> "Completed"
                        inProgress -> "In Progress"
                        else -> "${exam.totalMarks.toInt()} Marks"
                    },
                    textColor = when {
                        completed -> StatusSuccess
                        inProgress -> StatusWarning
                        else -> StatusInfo
                    },
                    backgroundColor = when {
                        completed -> StatusSuccessContainer
                        inProgress -> StatusWarningContainer
                        else -> StatusInfoContainer
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pass: ${exam.passingPercentage}% • Neg: ${if (exam.negativeMarks > 0.0) "-${exam.negativeMarks}" else "None"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandDarkBlue
                )
                if (completed) {
                    OutlinedButton(onClick = onViewResultClick) { Text("View Result") }
                } else {
                    Button(
                        onClick = onStartClick,
                        modifier = Modifier.testTag("start_exam_${exam.id}")
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (inProgress) "Resume" else "Start Exam")
                    }
                }
            }
        }
    }
}
