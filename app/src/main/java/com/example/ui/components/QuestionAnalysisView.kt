package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.core.EvaluationEngine
import com.example.data.model.AnswerStatus
import com.example.data.model.ExamResult
import com.example.data.model.GradingStatus
import com.example.data.model.QuestionAnalysis
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionAnalysisScreen(
    result: ExamResult,
    canGradeSubjective: Boolean,
    canControlVisibility: Boolean,
    isStudentView: Boolean,
    onBack: () -> Unit,
    onGradeQuestion: ((questionId: String, marks: Double) -> Unit)? = null,
    onUpdateVisibility: ((visible: Boolean, allowReview: Boolean) -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(result.examTitle, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${result.studentName} • ${result.subject}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("analysis_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                DonutBreakdownCard(
                    title = if (result.passed) "PASSED • ${result.gradingStatusEnum().label}" else "NEEDS IMPROVEMENT • ${result.gradingStatusEnum().label}",
                    centerLabel = String.format(Locale.getDefault(), "%.1f/%.1f", result.obtainedMarks, result.totalMarks),
                    centerValue = String.format(Locale.getDefault(), "%.1f%%", result.percentage),
                    segments = listOf(
                        Triple("Correct", result.correctCount, StatusSuccess),
                        Triple("Wrong", result.wrongCount, StatusError),
                        Triple("Unattempted", result.unattemptedCount, Color(0xFF94A3B8)),
                        Triple("Pending Review", result.pendingManualCount, StatusWarning)
                    )
                )
            }

            item {
                InstituteCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Student", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${result.studentName} ${if (result.studentCode.isNotBlank()) "(${result.studentCode})" else ""}",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Time Taken", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                EvaluationEngine.formatDuration(result.timeTakenSeconds),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                color = BrandDarkBlue
                            )
                        }
                    }
                }
            }

            if (canControlVisibility && onUpdateVisibility != null) {
                item {
                    InstituteCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Admin Result Visibility Controls", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (result.resultsVisibleToStudent) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = null,
                                        tint = BrandDarkBlue
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Release Score to Student", style = MaterialTheme.typography.bodyMedium)
                                }
                                Switch(
                                    checked = result.resultsVisibleToStudent,
                                    onCheckedChange = { onUpdateVisibility(it, result.allowAnswerReview) }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Allow Question-Wise Answer Key Review", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = result.allowAnswerReview,
                                    onCheckedChange = { onUpdateVisibility(result.resultsVisibleToStudent, it) }
                                )
                            }
                        }
                    }
                }
            }

            if (isStudentView && !result.allowAnswerReview) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.VisibilityOff,
                        title = "Detailed Answer Review Restricted",
                        subtitle = "Your institute administrator has restricted question-by-question answer key review for this examination."
                    )
                }
            } else {
                item {
                    SectionHeader(
                        title = "Question-Wise Answer Analysis (${result.questionAnalyses.size})",
                        subtitle = if (canGradeSubjective && result.gradingStatusEnum() == GradingStatus.PENDING_REVIEW) {
                            "Short-answer responses awaiting manual evaluation can be graded below."
                        } else {
                            "Review every question, student response, official answer, and marks awarded."
                        }
                    )
                }
                itemsIndexed(result.questionAnalyses, key = { idx, item -> "${item.questionId}_$idx" }) { idx, qa ->
                    QuestionAnalysisCard(
                        index = idx + 1,
                        analysis = qa,
                        canGrade = canGradeSubjective && qa.needsManualReview && result.gradingStatusEnum() != GradingStatus.FINALIZED,
                        onSubmitMarks = { marks -> onGradeQuestion?.invoke(qa.questionId, marks) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun QuestionAnalysisCard(
    index: Int,
    analysis: QuestionAnalysis,
    canGrade: Boolean,
    onSubmitMarks: (Double) -> Unit
) {
    val statusEnum = analysis.statusEnum()
    val (pillBg, pillText) = when (statusEnum) {
        AnswerStatus.CORRECT -> StatusSuccessContainer to StatusSuccess
        AnswerStatus.WRONG -> StatusErrorContainer to StatusError
        AnswerStatus.PENDING -> StatusWarningContainer to StatusWarning
        AnswerStatus.UNATTEMPTED -> StatusInfoContainer to StatusInfo
    }
    var marksInput by remember(analysis.questionId) { mutableStateOf(analysis.maxMarks.toString()) }

    InstituteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Q$index • ${analysis.questionType.replace("_", " ")}",
                    style = MaterialTheme.typography.labelLarge,
                    color = BrandDarkBlue
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(text = statusEnum.label, textColor = pillText, backgroundColor = pillBg)
                    Text(
                        String.format(Locale.getDefault(), "%+.1f / %.1f", analysis.marksObtained, analysis.maxMarks),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = pillText
                    )
                }
            }

            Text(analysis.questionText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

            if (analysis.options.isNotEmpty()) {
                val letters = listOf("A", "B", "C", "D", "E", "F")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    analysis.options.forEachIndexed { oIdx, opt ->
                        val letter = letters.getOrElse(oIdx) { "${oIdx + 1}" }
                        val isStudentChoice = analysis.studentAnswer.split(",").map { it.trim().uppercase() }.contains(letter)
                        val isCorrectChoice = analysis.correctAnswer.split(",").map { it.trim().uppercase() }.contains(letter)
                        val rowColor = when {
                            isCorrectChoice -> StatusSuccess
                            isStudentChoice && !isCorrectChoice -> StatusError
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = "($letter) $opt" +
                                (if (isCorrectChoice) "  ✓ Correct" else "") +
                                (if (isStudentChoice && !isCorrectChoice) "  ✗ Selected" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCorrectChoice || isStudentChoice) FontWeight.SemiBold else FontWeight.Normal,
                            color = rowColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Student Answer: ${analysis.studentAnswer.ifBlank { "Not attempted" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Official Key: ${analysis.correctAnswer.ifBlank { "Manual rubric" }}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusSuccess
                )
            }

            if (canGrade) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = marksInput,
                        onValueChange = { marksInput = it },
                        label = { Text("Award Marks (max ${analysis.maxMarks})") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val parsed = marksInput.toDoubleOrNull() ?: 0.0
                            onSubmitMarks(parsed.coerceIn(0.0, analysis.maxMarks))
                        },
                        modifier = Modifier.testTag("grade_subjective_${analysis.questionId}")
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}
