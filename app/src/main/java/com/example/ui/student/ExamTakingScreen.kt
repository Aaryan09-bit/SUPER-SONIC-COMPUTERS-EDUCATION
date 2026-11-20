package com.example.ui.student

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EvaluationEngine
import com.example.data.model.ExamResult
import com.example.data.model.QuestionType
import com.example.ui.components.InstituteCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.BrandBorderBlue
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.BrandLightBlue
import com.example.ui.theme.BrandSurfaceTint
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.viewmodel.ExamSessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamTakingScreen(
    viewModel: ExamSessionViewModel,
    onExitWithoutSubmit: () -> Unit,
    onCompletedResult: (ExamResult) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showGridSheet by remember { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    state.completedResult?.let { res ->
        onCompletedResult(res)
        return
    }

    BackHandler { showExitConfirm = true }

    val timerUrgent = state.remainingSeconds in 1..60
    val timerWarning = state.remainingSeconds in 61..300
    val timerBg = when {
        timerUrgent -> StatusErrorContainer
        timerWarning -> StatusWarningContainer
        else -> BrandSurfaceTint
    }
    val timerFg = when {
        timerUrgent -> StatusError
        timerWarning -> StatusWarning
        else -> BrandDarkBlue
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(state.exam?.title ?: "Examination", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.CloudDone, contentDescription = null, modifier = Modifier.size(14.dp), tint = StatusSuccess)
                                Text(state.lastSavedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = timerBg,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.Timer, contentDescription = "Remaining Time", tint = timerFg, modifier = Modifier.size(16.dp))
                                Text(
                                    text = EvaluationEngine.formatDuration(state.remainingSeconds),
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                                    color = timerFg
                                )
                            }
                        }
                        IconButton(
                            onClick = { showGridSheet = true },
                            modifier = Modifier.testTag("exam_navigator_grid_button")
                        ) {
                            Icon(Icons.Outlined.GridView, contentDescription = "Question Navigator")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                val total = state.orderedQuestions.size.coerceAtLeast(1)
                val answeredCount = state.responses.count { it.value.isNotBlank() }
                LinearProgressIndicator(
                    progress = { answeredCount.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandDarkBlue,
                    trackColor = BrandSurfaceTint
                )
            }
        }
    ) { padding ->
        when {
            state.loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Preparing distraction-free examination session...")
                    }
                }
            }
            state.error != null && state.orderedQuestions.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.Center) {
                    InstituteCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Unable to Open Examination", style = MaterialTheme.typography.titleMedium, color = StatusError)
                            Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onExitWithoutSubmit) { Text("Return to Dashboard") }
                        }
                    }
                }
            }
            else -> {
                val question = state.orderedQuestions.getOrNull(state.currentIndex)
                if (question != null) {
                    val qType = QuestionType.fromRaw(question.questionType)
                    val currentAnswer = state.responses[question.questionId].orEmpty()
                    val isFlagged = state.flaggedIds.contains(question.questionId)
                    val displayOptions = state.displayOptionsByQuestion[question.questionId].orEmpty()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(
                                    text = "Question ${state.currentIndex + 1} of ${state.orderedQuestions.size}",
                                    textColor = BrandDarkBlue,
                                    backgroundColor = BrandSurfaceTint
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${question.marks} Marks",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                        color = BrandDarkBlue
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.toggleFlagQuestion(question.questionId) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isFlagged) StatusWarningContainer else Color.Transparent,
                                            contentColor = if (isFlagged) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("flag_question_button")
                                    ) {
                                        Icon(Icons.Outlined.Flag, contentDescription = "Flag for Review", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isFlagged) "Flagged" else "Flag")
                                    }
                                }
                            }

                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(qType.label, style = MaterialTheme.typography.labelMedium, color = BrandDarkBlue)
                                    Text(
                                        text = question.text,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Answer Input by Question Type
                            when (qType) {
                                QuestionType.MCQ -> {
                                    displayOptions.forEach { (letter, optText) ->
                                        val selected = currentAnswer.equals(letter, ignoreCase = true)
                                        Surface(
                                            onClick = { viewModel.selectSingleAnswer(question.questionId, letter) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (selected) BrandSurfaceTint else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) BrandDarkBlue else BrandBorderBlue
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("exam_option_$letter")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = selected,
                                                    onClick = { viewModel.selectSingleAnswer(question.questionId, letter) }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "($letter)  $optText",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }

                                QuestionType.MULTI_SELECT -> {
                                    val pickedSet = currentAnswer.split(",").map { it.trim().uppercase() }.toSet()
                                    displayOptions.forEach { (letter, optText) ->
                                        val selected = pickedSet.contains(letter)
                                        Surface(
                                            onClick = { viewModel.toggleMultiSelectAnswer(question.questionId, letter) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (selected) BrandSurfaceTint else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BrandDarkBlue else BrandBorderBlue),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = selected,
                                                    onCheckedChange = { viewModel.toggleMultiSelectAnswer(question.questionId, letter) }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("($letter)  $optText", style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    }
                                }

                                QuestionType.TRUE_FALSE -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        listOf("True", "False").forEach { choice ->
                                            val selected = currentAnswer.equals(choice, ignoreCase = true)
                                            Surface(
                                                onClick = { viewModel.selectSingleAnswer(question.questionId, choice) },
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (selected) BrandDarkBlue else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.5.dp, if (selected) BrandDarkBlue else BrandBorderBlue),
                                                modifier = Modifier.weight(1f).height(58.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        choice,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                QuestionType.FILL_BLANK -> {
                                    OutlinedTextField(
                                        value = currentAnswer,
                                        onValueChange = { viewModel.selectSingleAnswer(question.questionId, it) },
                                        label = { Text("Type your exact answer") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                QuestionType.SHORT_ANSWER -> {
                                    OutlinedTextField(
                                        value = currentAnswer,
                                        onValueChange = { viewModel.selectSingleAnswer(question.questionId, it) },
                                        label = { Text("Write your detailed short answer (reviewed by teacher)") },
                                        minLines = 4,
                                        maxLines = 8,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (currentAnswer.isNotBlank()) {
                                TextButton(onClick = { viewModel.clearAnswer(question.questionId) }) {
                                    Text("Clear Selection")
                                }
                            }

                            state.error?.let {
                                Text(it, color = StatusError, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Bottom Navigation & Submit Bar
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = viewModel::previousQuestion,
                                enabled = state.currentIndex > 0 && !state.submitting,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Prev")
                            }

                            if (state.currentIndex < state.orderedQuestions.lastIndex) {
                                Button(
                                    onClick = viewModel::nextQuestion,
                                    enabled = !state.submitting,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("exam_next_button")
                                ) {
                                    Text("Next")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }

                            Button(
                                onClick = { showSubmitConfirm = true },
                                enabled = !state.submitting,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess, contentColor = Color.White),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("exam_submit_button")
                            ) {
                                if (state.submitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Submit")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGridSheet) {
        ModalBottomSheet(onDismissRequest = { showGridSheet = false }) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Question Navigation Grid", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusPill("Answered: ${state.responses.count { it.value.isNotBlank() }}", StatusSuccess, StatusSuccessContainer)
                    StatusPill("Flagged: ${state.flaggedIds.size}", StatusWarning, StatusWarningContainer)
                    StatusPill("Left: ${state.orderedQuestions.size - state.responses.count { it.value.isNotBlank() }}", BrandDarkBlue, BrandSurfaceTint)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 54.dp),
                    modifier = Modifier.height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.orderedQuestions) { idx, q ->
                        val answered = !state.responses[q.questionId].isNullOrBlank()
                        val flagged = state.flaggedIds.contains(q.questionId)
                        val isCur = idx == state.currentIndex
                        val bg = when {
                            isCur -> BrandDarkBlue
                            flagged -> StatusWarningContainer
                            answered -> StatusSuccessContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val fg = when {
                            isCur -> Color.White
                            flagged -> StatusWarning
                            answered -> StatusSuccess
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Surface(
                            onClick = {
                                viewModel.goToQuestion(idx)
                                showGridSheet = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            border = BorderStroke(1.dp, if (isCur) BrandLightBlue else BrandBorderBlue),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${idx + 1}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily), color = fg)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showSubmitConfirm) {
        val answered = state.responses.count { it.value.isNotBlank() }
        val unanswered = state.orderedQuestions.size - answered
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Submit Final Examination?") },
            text = {
                Text(
                    "Answered: $answered / ${state.orderedQuestions.size}\n" +
                        "Unattempted: $unanswered\n" +
                        "Flagged for review: ${state.flaggedIds.size}\n\n" +
                        "Once submitted, your responses are permanently locked and evaluated automatically."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirm = false
                        viewModel.submitExam(autoSubmitted = false)
                    },
                    modifier = Modifier.testTag("confirm_submit_exam_button")
                ) { Text("Confirm Submit") }
            },
            dismissButton = { TextButton(onClick = { showSubmitConfirm = false }) { Text("Review Answers") } }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Pause & Exit Examination?") },
            text = { Text("Your answered progress is auto-saved, but the examination timer continues running until you submit.") },
            confirmButton = {
                Button(onClick = { showExitConfirm = false; onExitWithoutSubmit() }) { Text("Exit for Now") }
            },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Stay in Exam") } }
        )
    }
}
