package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Tactile spring scale modifier driven by a shared MutableInteractionSource.
 * Gives buttons and pills crisp physical bounce feedback on press/release.
 */
fun Modifier.springPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.88f,
    extraScale: Float = 1f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "springPressScale"
    )
    this.graphicsLayer(
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
