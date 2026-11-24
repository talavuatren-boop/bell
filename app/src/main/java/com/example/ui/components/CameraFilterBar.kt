package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.FilterCategory
import com.example.model.FilterSampleSubject
import com.example.model.PhotoFilterPreset
import com.example.processing.ImageProcessor
import com.example.ui.theme.CrimsonLeica
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import java.util.EnumMap

enum class FilterShelfTab(val label: String, val isLeicaDot: Boolean) {
    ALL("Tất cả", false),
    LEICA_WARM("Leica Ấm", true),
    LEICA("Leica", true),
    STANDARD("Thường", false);

    val cachedPresets: List<PhotoFilterPreset> by lazy {
        when (this) {
            ALL -> PhotoFilterPreset.entries
            LEICA_WARM -> listOf(PhotoFilterPreset.NATURAL) +
                PhotoFilterPreset.entries.filter { it.isLeica && it.isWarm }
            LEICA -> listOf(PhotoFilterPreset.NATURAL) +
                PhotoFilterPreset.entries.filter { it.category == FilterCategory.LEICA }
            STANDARD -> listOf(PhotoFilterPreset.NATURAL) +
                PhotoFilterPreset.entries.filter { it.category == FilterCategory.FILM }
        }
    }

    val displayCount: Int by lazy {
        if (this == ALL) PhotoFilterPreset.entries.size else (cachedPresets.size - 1).coerceAtLeast(0)
    }

    fun filterPresets(): List<PhotoFilterPreset> = cachedPresets
}

private object FilterSamplePreviewCache {
    private val bitmapCache = EnumMap<FilterSampleSubject, ImageBitmap>(FilterSampleSubject::class.java)
    private val colorFilterCache = EnumMap<PhotoFilterPreset, ColorFilter>(PhotoFilterPreset::class.java)

    fun getSampleImage(context: Context, subject: FilterSampleSubject): ImageBitmap? {
        bitmapCache[subject]?.let { return it }
        val resId = when (subject) {
            FilterSampleSubject.STREET_GOLDEN -> R.drawable.img_filter_golden_street
            FilterSampleSubject.PORTRAIT_SKIN -> R.drawable.img_filter_portrait_warm
            FilterSampleSubject.CAFE_ANALOG -> R.drawable.img_filter_cafe_coffee
            FilterSampleSubject.SCENIC_BLUE -> R.drawable.img_filter_scenic_blue
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 6
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeResource(context.resources, resId, opts)?.asImageBitmap()
        if (decoded != null) {
            bitmapCache[subject] = decoded
        }
        return decoded
    }

    fun getColorFilter(preset: PhotoFilterPreset): ColorFilter? {
        if (preset == PhotoFilterPreset.NATURAL) return null
        colorFilterCache[preset]?.let { return it }
        val cm = ColorMatrix(ImageProcessor.buildFilterColorMatrix(preset, 1f).array)
        return ColorFilter.colorMatrix(cm).also { colorFilterCache[preset] = it }
    }
}

@Composable
fun FilterPreviewThumb(
    preset: PhotoFilterPreset,
    selected: Boolean,
    accent: Color,
    size: Dp = 52.dp,
    cornerRadius: Dp = 12.dp
) {
    val context = LocalContext.current
    val sampleBitmap = remember(preset.sampleSubject) {
        FilterSamplePreviewCache.getSampleImage(context, preset.sampleSubject)
    }
    val filterColor = remember(preset) {
        FilterSamplePreviewCache.getColorFilter(preset)
    }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(preset.swatchColors))
            .border(
                width = if (selected) 2.dp else 0.75.dp,
                color = if (selected) accent else PureWhite.copy(alpha = 0.22f),
                shape = shape
            )
    ) {
        if (sampleBitmap != null) {
            Image(
                bitmap = sampleBitmap,
                contentDescription = preset.label,
                contentScale = ContentScale.Crop,
                colorFilter = filterColor,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (preset.isLeica) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CrimsonLeica)
                    .border(1.dp, PureWhite, CircleShape)
            )
        }
    }
}

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
    var shelfTab by remember { mutableStateOf(FilterShelfTab.ALL) }
    val animEnabled = LocalAnimationsEnabled.current
    val listState = rememberLazyListState()

    val displayedPresets = remember(shelfTab) { shelfTab.filterPresets() }

    LaunchedEffect(visible, shelfTab) {
        if (visible) {
            val idx = displayedPresets.indexOf(activeFilter)
            if (idx > 2) {
                listState.scrollToItem((idx - 2).coerceAtLeast(0))
            }
        }
    }

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
            // Top bar: Filter category tabs (All / Leica Ấm / Leica / Thường) + Natural Base Tone+ toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterShelfTab.entries.forEach { tab ->
                        FilterGroupChip(
                            label = "${tab.label} (${tab.displayCount})",
                            selected = shelfTab == tab,
                            isLeicaDot = tab.isLeicaDot,
                            onClick = { shelfTab = tab }
                        )
                    }
                }

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
                        color = if (naturalBoostEnabled) accent else MutedSlate,
                        softWrap = false
                    )
                }
            }

            // Virtualized 60fps Photographic Thumbnail Carousel for 116 presets
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    items = displayedPresets,
                    key = { it.name }
                ) { preset ->
                    val selected = activeFilter == preset
                    val cardBg = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent

                    Column(
                        modifier = Modifier
                            .width(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .clickable(enabled = !isRecording) {
                                onSelectFilter(preset)
                            }
                            .padding(vertical = 5.dp, horizontal = 4.dp)
                            .testTag("camera_filter_${preset.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        FilterPreviewThumb(
                            preset = preset,
                            selected = selected,
                            accent = accent,
                            size = 52.dp,
                            cornerRadius = 11.dp
                        )
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
fun FilterGroupChip(
    label: String,
    selected: Boolean,
    isLeicaDot: Boolean,
    onClick: () -> Unit
) {
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
                    .background(CrimsonLeica)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) PureBlack else PureWhite.copy(alpha = 0.85f),
            softWrap = false
        )
    }
}

fun filterSwatchGradient(preset: PhotoFilterPreset): List<Color> = preset.swatchColors
