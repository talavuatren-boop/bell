package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOff
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.VideoStable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.MediaAssetEntity
import com.example.model.AiProcessingMode
import com.example.model.AiToolType
import com.example.model.MediaItemType
import com.example.ui.MainViewModel
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LevelGreen
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetryLarge
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import kotlin.math.roundToInt

@Composable
fun AiStudioScreen(
    viewModel: MainViewModel,
    onOpenVideoMerge: () -> Unit,
    onOpenStickersMeme: (MediaAssetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val assets by viewModel.activeAssets.collectAsStateWithLifecycle()
    val aiProgress by viewModel.aiProgress.collectAsStateWithLifecycle()
    val aiOutput by viewModel.aiOutput.collectAsStateWithLifecycle()

    var selectedTool by remember { mutableStateOf(AiToolType.ENHANCE_ALL) }
    var selectedMode by remember(settings.aiDefaultMode) { mutableStateOf(settings.aiDefaultMode) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importExternalUri(uri) { entity ->
                viewModel.selectAssetForAiStudio(entity)
            }
        }
    }

    val stickerPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importExternalUri(uri) { entity ->
                onOpenStickersMeme(entity)
            }
        }
    }

    val selectedAsset = aiOutput.selectedAsset
    val isVideoSelected = selectedAsset?.typeEnum == MediaItemType.VIDEO
    val latestPhoto = remember(assets) {
        assets.firstOrNull { it.typeEnum != MediaItemType.VIDEO }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Studio Hero Header with Ambient Radial Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.16f),
                            ObsidianSurface,
                            CardSlate
                        )
                    )
                )
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "HUNG AI STUDIO",
                            style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                            color = accent
                        )
                    }
                    Text(
                        text = "Computational Lab",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureWhite
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = LevelGreen, modifier = Modifier.size(12.dp))
                        Text(
                            text = "100% On-Device • Zero Cloud Uploads",
                            style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                            color = LevelGreen
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.testTag("ai_studio_pick_media")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }
            }
        }

        // Featured Creative Suites: Video Merge + Stickers & Meme
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(CardSlate, accent.copy(alpha = 0.14f))
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenVideoMerge)
                    .padding(12.dp)
                    .testTag("ai_studio_open_video_merge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MergeType, contentDescription = "Video Merge", tint = PureBlack)
                }
                Column {
                    Text(
                        text = "Video Merge",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = PureWhite
                    )
                    Text(
                        text = "Join & Split 2 Clips",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                        color = accent
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSlate)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                    .clickable {
                        val photoTarget = selectedAsset?.takeIf { it.typeEnum != MediaItemType.VIDEO } ?: latestPhoto
                        if (photoTarget != null) {
                            onOpenStickersMeme(photoTarget)
                        } else {
                            stickerPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    }
                    .padding(12.dp)
                    .testTag("ai_studio_open_stickers_meme"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElevatedObsidian)
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Stickers & Meme", tint = accent)
                }
                Column {
                    Text(
                        text = "Stickers & Meme",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = PureWhite
                    )
                    Text(
                        text = "Stamps & Captions",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                        color = MutedSlate
                    )
                }
            }
        }

        // Quick Recent Gallery Strip
        if (assets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                assets.take(12).forEach { asset ->
                    val isSelected = selectedAsset?.id == asset.id
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accent else HairlineBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectAssetForAiStudio(asset) }
                            .testTag("ai_recent_asset_${asset.id}")
                    ) {
                        AsyncImage(
                            model = asset.mediaStoreUri,
                            contentDescription = asset.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Original vs Enhanced Comparison Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(ElevatedObsidian)
                .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            val origBmp = aiOutput.originalPreviewBitmap
            val enhBmp = aiOutput.enhancedPreviewBitmap

            if (origBmp != null && enhBmp != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                splitFraction = (change.position.x / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                            }
                        }
                ) {
                    val widthPx = constraints.maxWidth
                    val heightPx = constraints.maxHeight
                    val containerWidth = maxWidth
                    // Enhanced on full background
                    Image(
                        bitmap = enhBmp.asImageBitmap(),
                        contentDescription = "Enhanced Result",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Original clipped on left side up to splitFraction
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(splitFraction)
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = origBmp.asImageBitmap(),
                            contentDescription = "Original",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(containerWidth)
                        )
                    }
                    // Divider line
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((widthPx * splitFraction).roundToInt(), 0) }
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(accent)
                    )
                    // Circular drag handle knob
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (widthPx * splitFraction).roundToInt() - 16,
                                    (heightPx / 2) - 16
                                )
                            }
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(2.dp, PureBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = "Drag to compare",
                            tint = PureBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "BEFORE",
                        style = MonoTelemetrySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Text(
                        text = "AFTER",
                        style = MonoTelemetrySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else if (selectedAsset != null) {
                AsyncImage(
                    model = selectedAsset.mediaStoreUri,
                    contentDescription = selectedAsset.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Pick or Tap a Recent Capture", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Denoise, upscale 2x, stabilize video, or add stickers & memes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
            }
        }

        // Resolution comparison & Save Enhanced Copy row
        aiOutput.imageResult?.let { res ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(14.dp))
                    .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${res.originalWidth}×${res.originalHeight} → ${res.outputWidth}×${res.outputHeight}",
                        style = MonoTelemetrySmall,
                        color = PureWhite
                    )
                    Text(
                        text = "${res.summaryLabel} • ${res.processingTimeMs} ms",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                        color = MutedSlate
                    )
                }
                Button(
                    onClick = { viewModel.saveAiEnhancedPhotoCopy() },
                    enabled = !aiOutput.savedCopySuccess,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = PureBlack),
                    modifier = Modifier.testTag("ai_save_copy_button")
                ) {
                    Icon(
                        imageVector = if (aiOutput.savedCopySuccess) Icons.Default.CheckCircle else Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (aiOutput.savedCopySuccess) "Saved" else "Save Copy")
                }
            }
        }

        if (aiOutput.enhancedVideoUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(14.dp))
                    .border(1.dp, LevelGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LevelGreen)
                Text(
                    text = "Enhanced video saved to Gallery (${aiOutput.processingTimeMs} ms)",
                    style = MonoTelemetrySmall,
                    color = PureWhite
                )
            }
        }

        // Icon-Backed AI Tool Selection Row
        Text("AI Enhancement Engine", style = MaterialTheme.typography.titleMedium, color = PureWhite)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiToolType.entries.forEach { tool ->
                val compatible = if (isVideoSelected) tool.isVideoOnly || tool == AiToolType.ENHANCE_ALL else tool.isPhotoCompatible
                val selected = selectedTool == tool
                val icon = when (tool) {
                    AiToolType.ENHANCE_ALL -> Icons.Default.AutoAwesome
                    AiToolType.DENOISE -> Icons.Default.BlurOff
                    AiToolType.SHARPEN -> Icons.Default.CenterFocusStrong
                    AiToolType.UPSCALE -> Icons.Default.Hd
                    AiToolType.COLOR_CORRECTION -> Icons.Default.Palette
                    AiToolType.VIDEO_STABILIZE -> Icons.Default.VideoStable
                    AiToolType.FRAME_ENHANCE -> Icons.Default.AutoFixHigh
                    AiToolType.FRAME_INTERPOLATE -> Icons.Default.SlowMotionVideo
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) accent else CardSlate)
                        .border(1.dp, if (selected) accent else HairlineBorder, RoundedCornerShape(14.dp))
                        .clickable(enabled = compatible && !aiProgress.isRunning) { selectedTool = tool }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("ai_tool_${tool.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            !compatible -> MutedSlate.copy(alpha = 0.4f)
                            selected -> PureBlack
                            else -> accent
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                        color = when {
                            !compatible -> MutedSlate.copy(alpha = 0.4f)
                            selected -> PureBlack
                            else -> PureWhite
                        }
                    )
                }
            }
        }

        // Processing Mode Selector (FAST / BALANCED / QUALITY)
        Text("Processing Quality Mode", style = MaterialTheme.typography.titleMedium, color = PureWhite)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiProcessingMode.entries.forEach { m ->
                val selected = selectedMode == m
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) accent.copy(alpha = 0.16f) else CardSlate)
                        .border(1.dp, if (selected) accent else HairlineBorder, RoundedCornerShape(14.dp))
                        .clickable(enabled = !aiProgress.isRunning) { selectedMode = m }
                        .padding(10.dp)
                        .testTag("ai_mode_${m.name.lowercase()}")
                ) {
                    Text(m.label, style = MonoTelemetryLarge.copy(fontSize = 12.sp), color = if (selected) accent else PureWhite)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(m.subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = MutedSlate)
                }
            }
        }

        // Active Progress & Cancel Panel
        if (aiProgress.isRunning || aiProgress.errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate, RoundedCornerShape(14.dp))
                    .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = aiProgress.errorMessage ?: aiProgress.stage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (aiProgress.errorMessage != null) CrimsonShutter else PureWhite,
                        modifier = Modifier.weight(1f)
                    )
                    if (aiProgress.isRunning) {
                        Text("${aiProgress.percent}%", style = MonoTelemetryLarge, color = accent)
                    }
                }
                if (aiProgress.isRunning) {
                    LinearProgressIndicator(
                        progress = { (aiProgress.percent / 100f).coerceIn(0f, 1f) },
                        color = accent,
                        trackColor = ElevatedObsidian,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(aiProgress.estimatedStatus, style = MonoTelemetrySmall.copy(fontSize = 10.sp), color = MutedSlate)
                        OutlinedButton(
                            onClick = { viewModel.cancelAiProcessing() },
                            modifier = Modifier.testTag("ai_cancel_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.runAiStudioProcessing(selectedTool, selectedMode) },
            enabled = selectedAsset != null && !aiProgress.isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = PureBlack),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("ai_run_button")
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedAsset == null) "Select Media First" else "Run ${selectedTool.label} (${selectedMode.label})",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
