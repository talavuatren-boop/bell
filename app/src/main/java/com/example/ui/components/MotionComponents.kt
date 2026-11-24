package com.example.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.PureWhite

val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/**
 * 60fps+ physics-based motion tokens tuned for zero-jank GPU-backed UI transitions.
 */
object HungCamMotion {
    val EmphasizedDecelerate = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    val fastSpringFloat = spring<Float>(
        dampingRatio = 0.84f,
        stiffness = 420f
    )
    val bouncySpringFloat = spring<Float>(
        dampingRatio = 0.74f,
        stiffness = 390f
    )
    val drawerSpringOffset = spring<IntOffset>(
        dampingRatio = 0.86f,
        stiffness = 360f
    )
    val smoothAspectSpring = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 320f
    )

    fun panelEnter(animationsEnabled: Boolean): EnterTransition {
        if (!animationsEnabled) return EnterTransition.None
        return fadeIn(tween(220, easing = EmphasizedDecelerate)) +
            slideInVertically(animationSpec = drawerSpringOffset) { it / 6 } +
            scaleIn(initialScale = 0.94f, animationSpec = fastSpringFloat)
    }

    fun panelExit(animationsEnabled: Boolean): ExitTransition {
        if (!animationsEnabled) return ExitTransition.None
        return fadeOut(tween(180, easing = EmphasizedDecelerate)) +
            slideOutVertically(animationSpec = tween(200, easing = EmphasizedDecelerate)) { it / 6 } +
            scaleOut(targetScale = 0.95f, animationSpec = tween(180, easing = EmphasizedDecelerate))
    }

    fun sheetEnter(animationsEnabled: Boolean): EnterTransition {
        if (!animationsEnabled) return EnterTransition.None
        return fadeIn(tween(240, easing = EmphasizedDecelerate)) +
            slideInVertically(animationSpec = drawerSpringOffset) { it / 2 }
    }

    fun sheetExit(animationsEnabled: Boolean): ExitTransition {
        if (!animationsEnabled) return ExitTransition.None
        return fadeOut(tween(190, easing = EmphasizedDecelerate)) +
            slideOutVertically(animationSpec = tween(220, easing = EmphasizedDecelerate)) { it / 2 }
    }

    fun screenTransition(forward: Boolean, animationsEnabled: Boolean): ContentTransform {
        if (!animationsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val sign = if (forward) 1 else -1
        return (
            slideInHorizontally(animationSpec = tween(190, easing = EmphasizedDecelerate)) { w -> sign * (w / 10) } +
                fadeIn(tween(180, easing = EmphasizedDecelerate))
            ) togetherWith (
            slideOutHorizontally(animationSpec = tween(150, easing = EmphasizedDecelerate)) { w -> -sign * (w / 14) } +
                fadeOut(tween(140, easing = EmphasizedDecelerate))
            )
    }
}

/**
 * Tactile spring scale modifier driven by a shared MutableInteractionSource.
 * Uses graphicsLayer for 60/120fps GPU-accelerated transforms without layout passes.
 */
@Composable
fun Modifier.springPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.88f,
    extraScale: Float = 1f
): Modifier {
    val animEnabled = LocalAnimationsEnabled.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && animEnabled) pressedScale else 1f,
        animationSpec = if (animEnabled) HungCamMotion.bouncySpringFloat else tween(0),
        label = "springPressScale"
    )
    return this.graphicsLayer(
        scaleX = pressScale * extraScale,
        scaleY = pressScale * extraScale
    )
}

@Composable
fun SpringIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = 0.84f,
    extraScale: Float = 1f,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        modifier = modifier.springPressScale(
            interactionSource = interactionSource,
            pressedScale = pressedScale,
            extraScale = extraScale
        ),
        content = content
    )
}

/**
 * Ultra-fast 150ms shutter flash curtain rendered cleanly over the viewfinder on capture.
 */
@Composable
fun ShutterFlashOverlay(
    triggerCount: Int,
    modifier: Modifier = Modifier
) {
    val animEnabled = LocalAnimationsEnabled.current
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(triggerCount) {
        if (triggerCount > 0 && animEnabled) {
            flashAlpha.snapTo(0.42f)
            flashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 155, easing = FastOutSlowInEasing)
            )
        }
    }

    if (flashAlpha.value > 0.005f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { alpha = flashAlpha.value }
                .background(Color.Black)
        )
    }
}

/**
 * 60fps pulsing live-photo capture indicator showing smooth progress during 2s/3s/5s/10s clips.
 */
@Composable
fun LivePhotoCaptureProgressPill(
    progress: Float,
    durationSec: Int,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60, easing = FastOutSlowInEasing),
        label = "liveProgressPill"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "liveRecPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveRecDotPulse"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xD80A0B0E))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = accent.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer { alpha = pulseAlpha }
                    .clip(CircleShape)
                    .background(CrimsonShutter)
            )
        }
        Text(
            text = "LIVE ${durationSec}s • ${(animatedProgress * 100).toInt()}%",
            style = MonoTelemetrySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = PureWhite
        )
    }
}
