package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MediaAssetEntity
import com.example.model.CropAspectPreset
import com.example.model.MemeCaptionConfig
import com.example.model.PhotoEditAdjustments
import com.example.model.PhotoFilterPreset
import com.example.model.StickerItem
import com.example.processing.ImageProcessor
import com.example.ui.MainViewModel
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalOnAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

private enum class PhotoEditorSection(val label: String) {
    ADJUST("Adjust"),
    FILTERS("Filters"),
    CROP("Crop"),
    STICKERS_MEME("Stickers & Meme")
}

private enum class AdjustSliderParam(val label: String, val min: Float, val max: Float) {
    BRIGHTNESS("Brightness", -0.5f, 0.5f),
    CONTRAST("Contrast", 0.6f, 1.6f),
    SATURATION("Saturation", 0f, 1.8f),
    EXPOSURE("Exposure", -1.5f, 1.5f),
    HIGHLIGHTS("Highlights", -0.8f, 0.8f),
    SHADOWS("Shadows", -0.8f, 0.8f),
    TEMPERATURE("Temperature", -0.8f, 0.8f),
    TINT("Tint", -0.8f, 0.8f),
    SHARPNESS("Sharpness", 0f, 1f)
}

private data class StickerPreset(
    val label: String,
    val isBadge: Boolean
)

private val STICKER_PRESETS = listOf(
    StickerPreset("SHOT ON HUNG CAM", true),
    StickerPreset("PRO RAW", true),
    StickerPreset("LIVE 50MP", true),
    StickerPreset("🔥", false),
    StickerPreset("😎", false),
    StickerPreset("😂", false),
    StickerPreset("💯", false),
    StickerPreset("✨", false),
    StickerPreset("⚡", false),
    StickerPreset("📸", false),
    StickerPreset("🎬", false),
    StickerPreset("❤️", false),
    StickerPreset("🏆", false)
)

@Composable
fun PhotoEditorScreen(
    asset: MediaAssetEntity,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    openStickersMemeInitially: Boolean = false
) {
    BackHandler(onBack = onBack)

    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val undoStack = remember { mutableStateListOf<PhotoEditAdjustments>() }
    val redoStack = remember { mutableStateListOf<PhotoEditAdjustments>() }
    var currentAdj by remember { mutableStateOf(PhotoEditAdjustments()) }

    var activeSection by remember(openStickersMemeInitially) {
        mutableStateOf(
            if (openStickersMemeInitially) PhotoEditorSection.STICKERS_MEME else PhotoEditorSection.ADJUST
        )
    }
    var activeSliderParam by remember { mutableStateOf(AdjustSliderParam.BRIGHTNESS) }
    var activeStickerId by remember { mutableStateOf<String?>(null) }
    var showingBefore by remember { mutableStateOf(false) }

    fun pushAdjustment(next: PhotoEditAdjustments) {
        undoStack.add(currentAdj)
        redoStack.clear()
        currentAdj = next
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLowEndSmooth = viewModel.isLowEndDevice || settings.lowEndOptimizedMode

    LaunchedEffect(asset.mediaStoreUri, isLowEndSmooth) {
        val maxPreviewDim = if (isLowEndSmooth) 960 else 1280
        originalBitmap = viewModel.loadScaledBitmap(Uri.parse(asset.mediaStoreUri), maxPreviewDim)
        previewBitmap = originalBitmap
    }

    // Recompute base image pixels (adjustments/filter/crop) with debounce so slider drags stay 60fps
    val baseImageKey = remember(currentAdj) {
        currentAdj.copy(stickers = emptyList(), memeCaption = MemeCaptionConfig())
    }
    LaunchedEffect(originalBitmap, baseImageKey) {
        val orig = originalBitmap ?: return@LaunchedEffect
        delay(45L)
        val nextPreview = ImageProcessor.applyPhotoEditorAdjustments(orig, baseImageKey)
        val oldPreview = previewBitmap
        previewBitmap = nextPreview
        if (oldPreview != null && oldPreview !== orig && oldPreview !== nextPreview && !oldPreview.isRecycled) {
            oldPreview.recycle()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Toolbar: Back, Undo, Redo, Reset, Save Copy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("photo_editor_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureWhite)
            }
            Row {
                IconButton(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            redoStack.add(currentAdj)
                            currentAdj = undoStack.removeAt(undoStack.lastIndex)
                        }
                    },
                    enabled = undoStack.isNotEmpty(),
                    modifier = Modifier.testTag("photo_editor_undo")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (undoStack.isNotEmpty()) PureWhite else MutedSlate)
                }
                IconButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            undoStack.add(currentAdj)
                            currentAdj = redoStack.removeAt(redoStack.lastIndex)
                        }
                    },
                    enabled = redoStack.isNotEmpty(),
                    modifier = Modifier.testTag("photo_editor_redo")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (redoStack.isNotEmpty()) PureWhite else MutedSlate)
                }
                IconButton(
                    onClick = {
                        activeStickerId = null
                        pushAdjustment(PhotoEditAdjustments())
                    },
                    modifier = Modifier.testTag("photo_editor_reset")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = PureWhite)
                }
            }

            Button(
                onClick = {
                    val orig = originalBitmap ?: return@Button
                    isSaving = true
                    scope.launch {
                        val exportMaxDim = if (isLowEndSmooth) 2400 else 3200
                        val fullOrig = viewModel.loadScaledBitmap(Uri.parse(asset.mediaStoreUri), exportMaxDim) ?: orig
                        val finalBmp = ImageProcessor.applyPhotoEditorAdjustments(fullOrig, currentAdj)
                        viewModel.saveEditedPhotoCopy(finalBmp, asset.id)
                        isSaving = false
                        onBack()
                    }
                },
                enabled = !isSaving && previewBitmap != null,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                modifier = Modifier.testTag("photo_editor_save")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = onAccent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Save Copy")
                }
            }
        }

        // Photo Preview Area + Interactive Stickers & Meme Layer
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ElevatedObsidian)
                .pointerInput(activeSection) {
                    if (activeSection != PhotoEditorSection.STICKERS_MEME) {
                        detectTapGestures(
                            onPress = {
                                showingBefore = true
                                tryAwaitRelease()
                                showingBefore = false
                            }
                        )
                    } else {
                        detectTapGestures(onTap = { activeStickerId = null })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val shown = if (showingBefore) originalBitmap else previewBitmap
            if (shown != null) {
                val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val fitScale = min(boxW / shown.width.toFloat(), boxH / shown.height.toFloat())
                val drawW = shown.width * fitScale
                val drawH = shown.height * fitScale
                val drawWDp = with(density) { drawW.toDp() }
                val drawHDp = with(density) { drawH.toDp() }
                val baseDimDp = min(drawWDp.value, drawHDp.value).dp

                Box(
                    modifier = Modifier.size(drawWDp, drawHDp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = "Edited Photo Preview",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!showingBefore) {
                        // Interactive Sticker Overlays
                        currentAdj.stickers.forEach { sticker ->
                            val isSelected = sticker.id == activeStickerId
                            val pxX = (sticker.normX * drawW).roundToInt()
                            val pxY = (sticker.normY * drawH).roundToInt()

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset { IntOffset(pxX, pxY) }
                                    .graphicsLayer {
                                        translationX = -size.width / 2f
                                        translationY = -size.height / 2f
                                        scaleX = sticker.scale
                                        scaleY = sticker.scale
                                        rotationZ = sticker.rotationDeg
                                    }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(1.5.dp, accent, RoundedCornerShape(10.dp))
                                        } else Modifier
                                    )
                                    .pointerInput(sticker.id, drawW, drawH) {
                                        detectTapGestures {
                                            activeStickerId = sticker.id
                                            activeSection = PhotoEditorSection.STICKERS_MEME
                                        }
                                    }
                                    .pointerInput(sticker.id, drawW, drawH) {
                                        detectDragGestures(
                                            onDragStart = {
                                                activeStickerId = sticker.id
                                                undoStack.add(currentAdj)
                                                redoStack.clear()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                currentAdj = currentAdj.copy(
                                                    stickers = currentAdj.stickers.map { item ->
                                                        if (item.id == sticker.id) {
                                                            item.copy(
                                                                normX = (item.normX + dragAmount.x / drawW).coerceIn(0.05f, 0.95f),
                                                                normY = (item.normY + dragAmount.y / drawH).coerceIn(0.05f, 0.95f)
                                                            )
                                                        } else item
                                                    }
                                                )
                                            }
                                        )
                                    }
                                    .padding(4.dp)
                            ) {
                                if (sticker.isBadgeStamp) {
                                    Text(
                                        text = sticker.label,
                                        style = MonoTelemetrySmall.copy(
                                            fontSize = (baseDimDp.value * 0.042f).coerceIn(10f, 18f).sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = PureWhite,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xC80E1016))
                                            .border(1.2.dp, accent, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                } else {
                                    Text(
                                        text = sticker.label,
                                        fontSize = (baseDimDp.value * 0.12f).coerceIn(24f, 48f).sp
                                    )
                                }
                            }
                        }

                        // Meme Top & Bottom Live Preview Overlays
                        val meme = currentAdj.memeCaption
                        val memeTextSp = ((baseDimDp.value * 0.075f) * meme.fontSizeScale).coerceIn(14f, 42f).sp
                        val memeColor = Color(meme.textColorArgb)
                        if (meme.topText.isNotBlank()) {
                            Text(
                                text = meme.topText.uppercase(),
                                color = memeColor,
                                fontSize = memeTextSp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    shadow = Shadow(color = Color.Black, offset = Offset(3f, 3f), blurRadius = 4f)
                                ),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                        if (meme.bottomText.isNotBlank()) {
                            Text(
                                text = meme.bottomText.uppercase(),
                                color = memeColor,
                                fontSize = memeTextSp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    shadow = Shadow(color = Color.Black, offset = Offset(3f, 3f), blurRadius = 4f)
                                ),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = accent)
            }

            // Hold-to-Compare Badge (tappable when in Stickers/Meme mode)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                showingBefore = true
                                tryAwaitRelease()
                                showingBefore = false
                            }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Compare, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Text(
                    text = if (showingBefore) "ORIGINAL" else "HOLD FOR BEFORE",
                    style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                    color = PureWhite
                )
            }
        }

        // Editor Deck
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSlate)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                PhotoEditorSection.entries.forEach { sec ->
                    val selected = activeSection == sec
                    Text(
                        text = sec.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) onAccent else MutedSlate,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accent else Color.Transparent)
                            .clickable { activeSection = sec }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("photo_editor_tab_${sec.name.lowercase()}")
                    )
                }
            }

            when (activeSection) {
                PhotoEditorSection.ADJUST -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdjustSliderParam.entries.forEach { p ->
                            val selected = activeSliderParam == p
                            Text(
                                text = p.label,
                                style = MonoTelemetrySmall,
                                color = if (selected) onAccent else PureWhite,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) accent else ElevatedObsidian)
                                    .clickable { activeSliderParam = p }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    val curVal = when (activeSliderParam) {
                        AdjustSliderParam.BRIGHTNESS -> currentAdj.brightness
                        AdjustSliderParam.CONTRAST -> currentAdj.contrast
                        AdjustSliderParam.SATURATION -> currentAdj.saturation
                        AdjustSliderParam.EXPOSURE -> currentAdj.exposure
                        AdjustSliderParam.HIGHLIGHTS -> currentAdj.highlights
                        AdjustSliderParam.SHADOWS -> currentAdj.shadows
                        AdjustSliderParam.TEMPERATURE -> currentAdj.temperature
                        AdjustSliderParam.TINT -> currentAdj.tint
                        AdjustSliderParam.SHARPNESS -> currentAdj.sharpness
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format("%.2f", curVal),
                            style = MonoTelemetrySmall,
                            color = accent,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Slider(
                            value = curVal,
                            onValueChange = { v ->
                                val next = when (activeSliderParam) {
                                    AdjustSliderParam.BRIGHTNESS -> currentAdj.copy(brightness = v)
                                    AdjustSliderParam.CONTRAST -> currentAdj.copy(contrast = v)
                                    AdjustSliderParam.SATURATION -> currentAdj.copy(saturation = v)
                                    AdjustSliderParam.EXPOSURE -> currentAdj.copy(exposure = v)
                                    AdjustSliderParam.HIGHLIGHTS -> currentAdj.copy(highlights = v)
                                    AdjustSliderParam.SHADOWS -> currentAdj.copy(shadows = v)
                                    AdjustSliderParam.TEMPERATURE -> currentAdj.copy(temperature = v)
                                    AdjustSliderParam.TINT -> currentAdj.copy(tint = v)
                                    AdjustSliderParam.SHARPNESS -> currentAdj.copy(sharpness = v)
                                }
                                currentAdj = next
                            },
                            onValueChangeFinished = { undoStack.add(currentAdj) },
                            valueRange = activeSliderParam.min..activeSliderParam.max,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                            modifier = Modifier.weight(1f).testTag("photo_editor_adjust_slider")
                        )
                    }
                }
                PhotoEditorSection.FILTERS -> {
                    var editorShelfTab by remember { mutableStateOf(com.example.ui.components.FilterShelfTab.ALL) }
                    val editorFilters = remember(editorShelfTab) { editorShelfTab.filterPresets() }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.example.ui.components.FilterShelfTab.entries.forEach { tab ->
                                com.example.ui.components.FilterGroupChip(
                                    label = "${tab.label} (${if (tab == com.example.ui.components.FilterShelfTab.ALL) PhotoFilterPreset.entries.size else tab.filterPresets().size - 1})",
                                    selected = editorShelfTab == tab,
                                    isLeicaDot = tab.isLeicaDot,
                                    onClick = { editorShelfTab = tab }
                                )
                            }
                        }
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                count = editorFilters.size,
                                key = { editorFilters[it].name }
                            ) { idx ->
                                val preset = editorFilters[idx]
                                val selected = currentAdj.filter == preset
                                Column(
                                    modifier = Modifier
                                        .width(66.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) accent.copy(alpha = 0.2f) else ElevatedObsidian)
                                        .border(
                                            1.dp,
                                            if (selected) accent else HairlineBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { pushAdjustment(currentAdj.copy(filter = preset)) }
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                        .testTag("photo_filter_${preset.name.lowercase()}"),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    com.example.ui.components.FilterPreviewThumb(
                                        preset = preset,
                                        selected = selected,
                                        accent = accent,
                                        size = 46.dp,
                                        cornerRadius = 10.dp
                                    )
                                    Text(
                                        preset.label,
                                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                                        color = if (selected) accent else PureWhite,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (currentAdj.filter != PhotoFilterPreset.NATURAL) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Cường độ", style = MonoTelemetrySmall, color = MutedSlate)
                                Slider(
                                    value = currentAdj.filterIntensity,
                                    onValueChange = { currentAdj = currentAdj.copy(filterIntensity = it) },
                                    onValueChangeFinished = { undoStack.add(currentAdj) },
                                    valueRange = 0.15f..1f,
                                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(currentAdj.filterIntensity * 100).toInt()}%",
                                    style = MonoTelemetrySmall,
                                    color = accent
                                )
                            }
                        }
                    }
                }
                PhotoEditorSection.CROP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { pushAdjustment(currentAdj.copy(rotationDegrees = (currentAdj.rotationDegrees + 90) % 360)) },
                            modifier = Modifier.testTag("photo_editor_rotate")
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90", tint = PureWhite)
                        }
                        IconButton(
                            onClick = { pushAdjustment(currentAdj.copy(flipHorizontal = !currentAdj.flipHorizontal)) },
                            modifier = Modifier.testTag("photo_editor_flip_h")
                        ) {
                            Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal", tint = if (currentAdj.flipHorizontal) accent else PureWhite)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CropAspectPreset.entries.forEach { preset ->
                            val selected = currentAdj.cropPreset == preset
                            Text(
                                text = preset.label,
                                style = MonoTelemetrySmall,
                                color = if (selected) onAccent else PureWhite,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) accent else ElevatedObsidian)
                                    .clickable { pushAdjustment(currentAdj.copy(cropPreset = preset)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                PhotoEditorSection.STICKERS_MEME -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Tap to Stamp Sticker (drag on photo to move)",
                            style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                            color = MutedSlate
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            STICKER_PRESETS.forEachIndexed { idx, preset ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ElevatedObsidian)
                                        .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            val newItem = StickerItem(
                                                id = UUID.randomUUID().toString(),
                                                label = preset.label,
                                                isBadgeStamp = preset.isBadge,
                                                normX = 0.5f,
                                                normY = 0.5f,
                                                scale = 1.0f,
                                                rotationDeg = 0f
                                            )
                                            pushAdjustment(
                                                currentAdj.copy(stickers = currentAdj.stickers + newItem)
                                            )
                                            activeStickerId = newItem.id
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                        .testTag("sticker_preset_$idx")
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = if (preset.isBadge) MonoTelemetrySmall else MaterialTheme.typography.titleMedium,
                                        color = PureWhite
                                    )
                                }
                            }
                        }

                        // Active Sticker Scale / Rotate / Delete Controls
                        val activeSticker = currentAdj.stickers.firstOrNull { it.id == activeStickerId }
                        if (activeSticker != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ElevatedObsidian)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Scale", style = MonoTelemetrySmall.copy(fontSize = 10.sp), color = accent)
                                Slider(
                                    value = activeSticker.scale,
                                    onValueChange = { s ->
                                        currentAdj = currentAdj.copy(
                                            stickers = currentAdj.stickers.map {
                                                if (it.id == activeSticker.id) it.copy(scale = s) else it
                                            }
                                        )
                                    },
                                    valueRange = 0.5f..2.2f,
                                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                                    modifier = Modifier.weight(1f)
                                )
                                Text("Rot", style = MonoTelemetrySmall.copy(fontSize = 10.sp), color = accent)
                                Slider(
                                    value = activeSticker.rotationDeg,
                                    onValueChange = { deg ->
                                        currentAdj = currentAdj.copy(
                                            stickers = currentAdj.stickers.map {
                                                if (it.id == activeSticker.id) it.copy(rotationDeg = deg) else it
                                            }
                                        )
                                    },
                                    valueRange = -45f..45f,
                                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        pushAdjustment(
                                            currentAdj.copy(
                                                stickers = currentAdj.stickers.filterNot { it.id == activeSticker.id }
                                            )
                                        )
                                        activeStickerId = null
                                    },
                                    modifier = Modifier.size(34.dp).testTag("sticker_delete_active")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Sticker", tint = CrimsonShutter, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Meme Caption Inputs + Color + Font Size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = currentAdj.memeCaption.topText,
                                onValueChange = { txt ->
                                    currentAdj = currentAdj.copy(
                                        memeCaption = currentAdj.memeCaption.copy(topText = txt)
                                    )
                                },
                                label = { Text("Top Meme Text", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = HairlineBorder,
                                    focusedTextColor = PureWhite,
                                    unfocusedTextColor = PureWhite
                                ),
                                modifier = Modifier.weight(1f).testTag("meme_top_input")
                            )
                            OutlinedTextField(
                                value = currentAdj.memeCaption.bottomText,
                                onValueChange = { txt ->
                                    currentAdj = currentAdj.copy(
                                        memeCaption = currentAdj.memeCaption.copy(bottomText = txt)
                                    )
                                },
                                label = { Text("Bottom Meme Text", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = HairlineBorder,
                                    focusedTextColor = PureWhite,
                                    unfocusedTextColor = PureWhite
                                ),
                                modifier = Modifier.weight(1f).testTag("meme_bottom_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Meme Size", style = MonoTelemetrySmall.copy(fontSize = 10.sp), color = MutedSlate)
                            Slider(
                                value = currentAdj.memeCaption.fontSizeScale,
                                onValueChange = { sz ->
                                    currentAdj = currentAdj.copy(
                                        memeCaption = currentAdj.memeCaption.copy(fontSizeScale = sz)
                                    )
                                },
                                valueRange = 0.6f..1.6f,
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                                modifier = Modifier.weight(1f)
                            )
                            listOf(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.YELLOW,
                                android.graphics.Color.CYAN,
                                android.graphics.Color.RED
                            ).forEach { argb ->
                                val selected = currentAdj.memeCaption.textColorArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) accent else HairlineBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            pushAdjustment(
                                                currentAdj.copy(
                                                    memeCaption = currentAdj.memeCaption.copy(textColorArgb = argb)
                                                )
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
