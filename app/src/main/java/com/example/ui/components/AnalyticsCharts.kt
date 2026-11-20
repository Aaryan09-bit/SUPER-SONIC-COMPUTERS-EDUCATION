package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.BrandLightBlue
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.util.Locale

data class ChartBarEntry(
    val label: String,
    val value: Float,
    val maxValue: Float = 100f,
    val valueSuffix: String = "%"
)

@Composable
fun BarChartCard(
    title: String,
    subtitle: String,
    entries: List<ChartBarEntry>,
    modifier: Modifier = Modifier
) {
    InstituteCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (entries.isEmpty()) {
                Text(
                    "Complete or import examinations to populate chart analytics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val displayEntries = entries.take(6)
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    val chartW = size.width
                    val chartH = size.height
                    // Horizontal guide lines
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                        val y = chartH * (1f - frac)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartW, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    val slotW = chartW / displayEntries.size
                    val barW = (slotW * 0.46f).coerceIn(14.dp.toPx(), 42.dp.toPx())
                    displayEntries.forEachIndexed { idx, entry ->
                        val norm = (entry.value / entry.maxValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val barH = (chartH * norm).coerceAtLeast(6.dp.toPx())
                        val left = idx * slotW + (slotW - barW) / 2f
                        val top = chartH - barH
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(BrandLightBlue, BrandDarkBlue)),
                            topLeft = Offset(left, top),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    displayEntries.forEach { entry ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format(Locale.getDefault(), "%.0f%s", entry.value, entry.valueSuffix),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = BrandDarkBlue
                            )
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrendLineChartCard(
    title: String,
    subtitle: String,
    points: List<Pair<String, Float>>,
    modifier: Modifier = Modifier
) {
    InstituteCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (points.size < 2) {
                Text(
                    "At least 2 completed tests are needed to display a performance trend curve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val safePoints = points.takeLast(8)
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val padH = 14.dp.toPx()
                    val padV = 12.dp.toPx()
                    val drawW = w - padH * 2
                    val drawH = h - padV * 2

                    listOf(0f, 0.5f, 1f).forEach { f ->
                        val y = padV + drawH * (1f - f)
                        drawLine(gridColor, Offset(padH, y), Offset(w - padH, y), strokeWidth = 1.dp.toPx())
                    }

                    val coords = safePoints.mapIndexed { idx, (_, pct) ->
                        val x = padH + drawW * (idx.toFloat() / (safePoints.size - 1).coerceAtLeast(1))
                        val y = padV + drawH * (1f - (pct / 100f).coerceIn(0f, 1f))
                        Offset(x, y)
                    }

                    val fillPath = Path().apply {
                        moveTo(coords.first().x, h - padV)
                        coords.forEach { lineTo(it.x, it.y) }
                        lineTo(coords.last().x, h - padV)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(BrandLightBlue.copy(alpha = 0.38f), BrandLightBlue.copy(alpha = 0.02f))
                        )
                    )

                    val linePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        coords.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = linePath,
                        color = BrandDarkBlue,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    coords.forEach { pt ->
                        drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = pt)
                        drawCircle(color = BrandDarkBlue, radius = 3.8.dp.toPx(), center = pt)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    safePoints.forEach { (label, pct) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${pct.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandDarkBlue
                            )
                            Text(
                                label.take(6),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonutBreakdownCard(
    title: String,
    centerLabel: String,
    centerValue: String,
    segments: List<Triple<String, Int, Color>>,
    modifier: Modifier = Modifier
) {
    InstituteCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val total = segments.sumOf { it.second }.coerceAtLeast(1)
            Box(modifier = Modifier.size(106.dp), contentAlignment = Alignment.Center) {
                val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                Canvas(modifier = Modifier.size(106.dp)) {
                    val stroke = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Butt)
                    val inset = 7.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke
                    )
                    var startAngle = -90f
                    segments.forEach { (_, count, color) ->
                        if (count > 0) {
                            val sweep = (count.toFloat() / total) * 360f
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = stroke
                            )
                            startAngle += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        centerValue,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        centerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                segments.forEach { (label, count, color) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

fun answerStatusColor(correct: Boolean, unattempted: Boolean, pending: Boolean): Color = when {
    pending -> StatusWarning
    unattempted -> Color(0xFF64748B)
    correct -> StatusSuccess
    else -> StatusError
}
