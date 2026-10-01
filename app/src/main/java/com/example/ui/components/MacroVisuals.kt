package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MacroCarbsColor
import com.example.ui.theme.MacroFatColor
import com.example.ui.theme.MacroFiberColor
import com.example.ui.theme.MacroProteinColor
import kotlin.math.roundToInt

@Composable
fun MacroDonutChart(
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    totalCalories: Int,
    modifier: Modifier = Modifier,
    chartSize: Dp = 120.dp,
    strokeWidth: Dp = 12.dp,
) {
    val totalMacros = (proteinG + carbsG + fatG).coerceAtLeast(0.1)
    val proteinPercent = (proteinG / totalMacros).toFloat()
    val carbsPercent = (carbsG / totalMacros).toFloat()
    val fatPercent = (fatG / totalMacros).toFloat()

    val animProtein by animateFloatAsState(targetValue = proteinPercent, animationSpec = tween(600), label = "protein")
    val animCarbs by animateFloatAsState(targetValue = carbsPercent, animationSpec = tween(600), label = "carbs")
    val animFat by animateFloatAsState(targetValue = fatPercent, animationSpec = tween(600), label = "fat")

    Box(
        modifier = modifier.size(chartSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(chartSize)) {
            val strokePx = strokeWidth.toPx()
            val canvasSize = size.minDimension - strokePx
            val topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2, strokePx / 2)
            val arcSize = androidx.compose.ui.geometry.Size(canvasSize, canvasSize)

            // Base track
            drawArc(
                color = Color.LightGray.copy(alpha = 0.2f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            var currentAngle = -90f

            // Protein (Red/Coral)
            if (animProtein > 0.01f) {
                val sweep = animProtein * 360f
                drawArc(
                    color = MacroProteinColor,
                    startAngle = currentAngle,
                    sweepAngle = (sweep - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                currentAngle += sweep
            }

            // Carbs (Amber)
            if (animCarbs > 0.01f) {
                val sweep = animCarbs * 360f
                drawArc(
                    color = MacroCarbsColor,
                    startAngle = currentAngle,
                    sweepAngle = (sweep - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                currentAngle += sweep
            }

            // Fat (Blue)
            if (animFat > 0.01f) {
                val sweep = animFat * 360f
                drawArc(
                    color = MacroFatColor,
                    startAngle = currentAngle,
                    sweepAngle = (sweep - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$totalCalories",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MacroPill(
    label: String,
    grams: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${grams}g",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
        }
    }
}

@Composable
fun MacroProgressBar(
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    modifier: Modifier = Modifier
) {
    val total = (proteinG + carbsG + fatG).coerceAtLeast(0.1)
    val proteinWeight = (proteinG / total).toFloat().coerceIn(0.01f, 1f)
    val carbsWeight = (carbsG / total).toFloat().coerceIn(0.01f, 1f)
    val fatWeight = (fatG / total).toFloat().coerceIn(0.01f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.LightGray.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .weight(proteinWeight)
                .fillMaxWidth()
                .height(8.dp)
                .background(MacroProteinColor)
        )
        Box(
            modifier = Modifier
                .weight(carbsWeight)
                .fillMaxWidth()
                .height(8.dp)
                .background(MacroCarbsColor)
        )
        Box(
            modifier = Modifier
                .weight(fatWeight)
                .fillMaxWidth()
                .height(8.dp)
                .background(MacroFatColor)
        )
    }
}

@Composable
fun ConfidenceBadge(
    confidence: Double,
    modifier: Modifier = Modifier
) {
    val percent = (confidence * 100).roundToInt().coerceIn(1, 100)
    val (badgeColor, text) = when {
        percent >= 85 -> Pair(Color(0xFF059669), "High Confidence ($percent%)")
        percent >= 65 -> Pair(Color(0xFFD97706), "Moderate Confidence ($percent%)")
        else -> Pair(Color(0xFFDC2626), "Low Confidence ($percent%)")
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = badgeColor.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (percent >= 75) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = badgeColor
            )
        }
    }
}
