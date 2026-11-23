package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.FocusPeakingOverlayData
import com.example.model.GridType
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.LevelGreen
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.PureWhite
import kotlin.math.roundToInt

@Composable
fun GridOverlay(gridType: GridType, modifier: Modifier = Modifier) {
    if (gridType == GridType.NONE) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val lineColor = PureWhite.copy(alpha = 0.28f)
        val stroke = 1.dp.toPx()
        when (gridType) {
            GridType.RULE_OF_THIRDS -> {
                for (i in 1..2) {
                    val x = size.width * i / 3f
                    val y = size.height * i / 3f
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke)
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
                }
            }
            GridType.GOLDEN_RATIO -> {
                val r1 = 0.382f
                val r2 = 0.618f
                drawLine(lineColor, Offset(size.width * r1, 0f), Offset(size.width * r1, size.height), stroke)
                drawLine(lineColor, Offset(size.width * r2, 0f), Offset(size.width * r2, size.height), stroke)
                drawLine(lineColor, Offset(0f, size.height * r1), Offset(size.width, size.height * r1), stroke)
                drawLine(lineColor, Offset(0f, size.height * r2), Offset(size.width, size.height * r2), stroke)
            }
            GridType.CENTER_CROSS -> {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arm = 22.dp.toPx()
                drawLine(lineColor, Offset(cx - arm, cy), Offset(cx + arm, cy), stroke * 1.4f)
                drawLine(lineColor, Offset(cx, cy - arm), Offset(cx, cy + arm), stroke * 1.4f)
            }
            GridType.NONE -> Unit
        }
    }
}

@Composable
fun ElectronicLevelOverlay(
    rollDegrees: Float,
    isAligned: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val color = if (isAligned) LevelGreen else PureWhite.copy(alpha = 0.72f)
        val stroke = if (isAligned) 2.2.dp.toPx() else 1.5.dp.toPx()

        // Fixed reference ticks
        drawLine(PureWhite.copy(alpha = 0.35f), Offset(cx - 56.dp.toPx(), cy), Offset(cx - 34.dp.toPx(), cy), 1.5.dp.toPx())
        drawLine(PureWhite.copy(alpha = 0.35f), Offset(cx + 34.dp.toPx(), cy), Offset(cx + 56.dp.toPx(), cy), 1.5.dp.toPx())

        rotate(degrees = -rollDegrees, pivot = Offset(cx, cy)) {
            drawLine(color, Offset(cx - 30.dp.toPx(), cy), Offset(cx - 8.dp.toPx(), cy), stroke)
            drawLine(color, Offset(cx + 8.dp.toPx(), cy), Offset(cx + 30.dp.toPx(), cy), stroke)
            drawCircle(
                color = if (isAligned) LevelGreen else accent,
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
fun FocusPeakingOverlay(
    data: FocusPeakingOverlayData?,
    modifier: Modifier = Modifier
) {
    if (data == null) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val cellW = size.width / data.cols.toFloat()
        val cellH = size.height / data.rows.toFloat()
        for (r in 0 until data.rows) {
            for (c in 0 until data.cols) {
                val strength = data.intensities[r * data.cols + c]
                if (strength > 0.16f) {
                    drawRect(
                        color = ElectricCyan.copy(alpha = (strength * 0.65f).coerceIn(0.15f, 0.75f)),
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW * 0.7f, cellH * 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun TapFocusReticle(
    normalizedPos: Offset?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val animEnabled = LocalAnimationsEnabled.current
    val lockScale = remember { Animatable(1f) }
    LaunchedEffect(normalizedPos, visible) {
        if (visible && normalizedPos != null && animEnabled) {
            lockScale.snapTo(1.36f)
            lockScale.animateTo(1f, animationSpec = HungCamMotion.bouncySpringFloat)
        } else {
            lockScale.snapTo(1f)
        }
    }

    AnimatedVisibility(
        visible = visible && normalizedPos != null,
        enter = if (animEnabled) fadeIn(tween(120)) + scaleIn(initialScale = 1.15f, animationSpec = HungCamMotion.fastSpringFloat) else fadeIn(tween(0)),
        exit = if (animEnabled) fadeOut(tween(180)) + scaleOut(targetScale = 0.88f, animationSpec = tween(180)) else fadeOut(tween(0)),
        modifier = modifier
    ) {
        normalizedPos?.let { pos ->
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val px = pos.x * size.width
                    val py = pos.y * size.height
                    val half = 30.dp.toPx() * lockScale.value
                    val corner = 9.dp.toPx()
                    val stroke = 2.dp.toPx()
                    val left = px - half
                    val right = px + half
                    val top = py - half
                    val bottom = py + half

                    // Four sharp corner brackets
                    drawLine(accent, Offset(left, top), Offset(left + corner, top), stroke)
                    drawLine(accent, Offset(left, top), Offset(left, top + corner), stroke)
                    drawLine(accent, Offset(right, top), Offset(right - corner, top), stroke)
                    drawLine(accent, Offset(right, top), Offset(right, top + corner), stroke)
                    drawLine(accent, Offset(left, bottom), Offset(left + corner, bottom), stroke)
                    drawLine(accent, Offset(left, bottom), Offset(left, bottom - corner), stroke)
                    drawLine(accent, Offset(right, bottom), Offset(right - corner, bottom), stroke)
                    drawLine(accent, Offset(right, bottom), Offset(right, bottom - corner), stroke)
                    drawCircle(accent, radius = 2.5.dp.toPx(), center = Offset(px, py))
                }
            }
        }
    }
}

@Composable
fun LiveHistogramHud(
    bins: List<Float>,
    highlightClipPct: Float,
    shadowClipPct: Float,
    showExposureWarning: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .border(0.75.dp, PureWhite.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .width(108.dp)
                .height(34.dp)
        ) {
            if (bins.isEmpty()) return@Canvas
            val path = Path()
            val stepX = size.width / (bins.size - 1).coerceAtLeast(1)
            path.moveTo(0f, size.height)
            bins.forEachIndexed { idx, value ->
                val x = idx * stepX
                val y = size.height - (value.coerceIn(0f, 1f) * size.height)
                path.lineTo(x, y)
            }
            path.lineTo(size.width, size.height)
            path.close()
            drawPath(path, PureWhite.copy(alpha = 0.34f))
        }
        if (showExposureWarning) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "HI ${String.format("%.1f", highlightClipPct)}%",
                    style = MonoTelemetrySmall.copy(fontSize = 9.sp),
                    color = if (highlightClipPct > 5f) ErrorCrimson else PureWhite.copy(alpha = 0.75f)
                )
                Text(
                    text = "LO ${String.format("%.1f", shadowClipPct)}%",
                    style = MonoTelemetrySmall.copy(fontSize = 9.sp),
                    color = if (shadowClipPct > 8f) ErrorCrimson else PureWhite.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
fun SelfTimerCountdownHud(secondsRemaining: Int, modifier: Modifier = Modifier) {
    val animEnabled = LocalAnimationsEnabled.current
    AnimatedVisibility(
        visible = secondsRemaining > 0,
        enter = if (animEnabled) fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = HungCamMotion.bouncySpringFloat) else fadeIn(tween(0)),
        exit = if (animEnabled) fadeOut(tween(120)) + scaleOut(targetScale = 0.75f) else fadeOut(tween(0)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(55.dp))
                .border(2.dp, LocalAccentColor.current, RoundedCornerShape(55.dp)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = secondsRemaining,
                transitionSpec = {
                    if (animEnabled) {
                        (scaleIn(initialScale = 1.4f, animationSpec = HungCamMotion.bouncySpringFloat) + fadeIn(tween(120))) togetherWith
                            (scaleOut(targetScale = 0.7f, animationSpec = tween(110)) + fadeOut(tween(110)))
                    } else {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }
                },
                label = "countdownNumber"
            ) { sec ->
                Text(
                    text = sec.coerceAtLeast(0).toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                    color = PureWhite,
                    modifier = Modifier.graphicsLayer { clip = false }
                )
            }
        }
    }
}
