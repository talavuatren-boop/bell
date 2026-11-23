package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FilterCategory
import com.example.model.PhotoFilterPreset
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

@Composable
fun CameraFilterBar(
    visible: Boolean,
    activeFilter: PhotoFilterPreset,
    filterIntensity: Float,
    naturalBoostEnabled: Boolean,
    isRecording: Boolean,
    onSelectFilter: (PhotoFilterPreset) -> Unit,
    onChangeIntensity: (Float) -> Unit,
    onToggleNaturalBoost: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var categoryFilter by remember { mutableStateOf<FilterCategory?>(null) }

    val animEnabled = LocalAnimationsEnabled.current

    AnimatedVisibility(
        visible = visible,
        enter = HungCamMotion.panelEnter(animEnabled),
        exit = HungCamMotion.panelExit(animEnabled),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE00A0B0E))
                .border(0.75.dp, HairlineBorder.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .testTag("camera_filter_carousel"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top bar: All / Leica / Film category pills + Natural Base Tone+ toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterGroupChip(
                        label = "Tất cả",
                        selected = categoryFilter == null,
                        isLeicaDot = false,
                        onClick = { categoryFilter = null }
                    )
                    FilterGroupChip(
                        label = "Leica",
                        selected = categoryFilter == FilterCategory.LEICA,
                        isLeicaDot = true,
                        onClick = { categoryFilter = FilterCategory.LEICA }
                    )
                    FilterGroupChip(
                        label = "Máy ảnh",
                        selected = categoryFilter == FilterCategory.FILM,
                        isLeicaDot = false,
                        onClick = { categoryFilter = FilterCategory.FILM }
                    )
                }

                // "Màu gốc đẹp+" quick toggle right inside filter bar
                val boostInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .springPressScale(boostInteraction, pressedScale = 0.90f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (naturalBoostEnabled) accent.copy(alpha = 0.18f) else ElevatedObsidian)
                        .border(
                            0.75.dp,
                            if (naturalBoostEnabled) accent else HairlineBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(interactionSource = boostInteraction, indication = null) {
                            onToggleNaturalBoost()
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .testTag("filter_bar_natural_boost"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (naturalBoostEnabled) accent else MutedSlate)
                    )
                    Text(
                        text = if (naturalBoostEnabled) "Màu gốc+" else "Gốc thô",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = if (naturalBoostEnabled) accent else MutedSlate
                    )
                }
            }

            // Scrollable Swatch Carousel
            val displayedPresets = remember(categoryFilter) {
                when (categoryFilter) {
                    null -> PhotoFilterPreset.entries
                    FilterCategory.LEICA -> listOf(PhotoFilterPreset.NATURAL) +
                        PhotoFilterPreset.entries.filter { it.category == FilterCategory.LEICA }
                    FilterCategory.FILM -> listOf(PhotoFilterPreset.NATURAL) +
                        PhotoFilterPreset.entries.filter { it.category == FilterCategory.FILM }
                    FilterCategory.BASE -> listOf(PhotoFilterPreset.NATURAL)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                displayedPresets.forEach { preset ->
                    val selected = activeFilter == preset
                    val colors = filterSwatchGradient(preset)
                    val interaction = remember(preset) { MutableInteractionSource() }
                    val swatchScale by animateFloatAsState(
                        targetValue = if (selected && animEnabled) 1.07f else 1f,
                        animationSpec = if (animEnabled) HungCamMotion.bouncySpringFloat else tween(0),
                        label = "swatchScale_${preset.name}"
                    )
                    val cardBg by animateColorAsState(
                        targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                        animationSpec = tween(160),
                        label = "swatchBg_${preset.name}"
                    )

                    Column(
                        modifier = Modifier
                            .width(62.dp)
                            .springPressScale(interaction, pressedScale = 0.90f, extraScale = swatchScale)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                enabled = !isRecording
                            ) {
                                onSelectFilter(preset)
                            }
                            .padding(vertical = 5.dp, horizontal = 4.dp)
                            .testTag("camera_filter_${preset.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors))
                                .border(
                                    width = if (selected) 2.2.dp else 1.dp,
                                    color = if (selected) accent else PureWhite.copy(alpha = 0.25f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            if (preset.isLeica) {
                                Box(
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(CrimsonShutter)
                                        .border(1.dp, PureWhite, CircleShape)
                                )
                            }
                        }
                        Text(
                            text = preset.label,
                            style = MonoTelemetrySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) accent else PureWhite.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Filter Intensity Slider when a non-base filter is active
            AnimatedVisibility(
                visible = activeFilter != PhotoFilterPreset.NATURAL,
                enter = if (animEnabled) fadeIn(tween(170)) + expandVertically(animationSpec = tween(190)) else fadeIn(tween(0)),
                exit = if (animEnabled) fadeOut(tween(130)) + shrinkVertically(animationSpec = tween(150)) else fadeOut(tween(0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Cường độ",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                        color = MutedSlate
                    )
                    Slider(
                        value = filterIntensity,
                        onValueChange = onChangeIntensity,
                        valueRange = 0.15f..1.0f,
                        enabled = !isRecording,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = HairlineBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .testTag("camera_filter_intensity_slider")
                    )
                    Text(
                        text = "${(filterIntensity * 100).toInt()}%",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = accent,
                        modifier = Modifier.width(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterGroupChip(
    label: String,
    selected: Boolean,
    isLeicaDot: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalAccentColor.current
    val interaction = remember(label) { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .springPressScale(interaction, pressedScale = 0.90f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PureWhite else ElevatedObsidian)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isLeicaDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(CrimsonShutter)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) PureBlack else PureWhite.copy(alpha = 0.85f)
        )
    }
}

fun filterSwatchGradient(preset: PhotoFilterPreset): List<Color> = when (preset) {
    PhotoFilterPreset.NATURAL -> listOf(Color(0xFFECEFF4), Color(0xFF9BA3B0))
    PhotoFilterPreset.LEICA_VIBRANT -> listOf(Color(0xFFFF4B3E), Color(0xFFFFB800), Color(0xFF1F7AE0))
    PhotoFilterPreset.LEICA_AUTHENTIC -> listOf(Color(0xFFB57C50), Color(0xFF3E444E), Color(0xFF1A1B20))
    PhotoFilterPreset.LEICA_BW_NAT -> listOf(Color(0xFFE0E2E5), Color(0xFF636870), Color(0xFF23262A))
    PhotoFilterPreset.LEICA_BW_HC -> listOf(Color(0xFFFFFFFF), Color(0xFF202020), Color(0xFF000000))
    PhotoFilterPreset.LEICA_SEPIA -> listOf(Color(0xFFE0C097), Color(0xFF8C6239), Color(0xFF3E2714))
    PhotoFilterPreset.LEICA_BLUE -> listOf(Color(0xFF7FD8FF), Color(0xFF1D4E89), Color(0xFF0E1B33))
    PhotoFilterPreset.PORTRA -> listOf(Color(0xFFFFD5B8), Color(0xFFE29D7D), Color(0xFF7CA7BA))
    PhotoFilterPreset.FUJI_PRO -> listOf(Color(0xFF7ED6DF), Color(0xFF22A6B3), Color(0xFF2B4C59))
    PhotoFilterPreset.VIVID -> listOf(Color(0xFFFF5E57), Color(0xFFFFB800))
    PhotoFilterPreset.WARM -> listOf(Color(0xFFFFCC70), Color(0xFFC850C0))
    PhotoFilterPreset.COOL -> listOf(Color(0xFF4BCFFA), Color(0xFF3B3B98))
    PhotoFilterPreset.FILM -> listOf(Color(0xFF55E6C1), Color(0xFF2C3A47))
    PhotoFilterPreset.VINTAGE -> listOf(Color(0xFFD1A36C), Color(0xFF6D4C3D))
    PhotoFilterPreset.CINEMATIC -> listOf(Color(0xFF0FB9B1), Color(0xFFFF7F50))
    PhotoFilterPreset.BW -> listOf(Color(0xFFBDC3C7), Color(0xFF2C3E50))
}
