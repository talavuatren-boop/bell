package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Compare
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
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
import com.example.ui.theme.LocalOnAccentColor
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
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val assets by viewModel.activeAssets.collectAsStateWithLifecycle()
    val aiProgress by viewModel.aiProgress.collectAsStateWithLifecycle()
    val aiOutput by viewModel.aiOutput.collectAsStateWithLifecycle()

    var selectedTool by remember { mutableStateOf(AiToolType.ENHANCE_ALL) }
    var selectedMode by remember(settings.aiDefaultMode) { mutableStateOf(settings.aiDefaultMode) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var holdingOriginal by remember { mutableStateOf(false) }

    // Automatically pre-select latest photo/asset if user enters AI Lab with media in gallery
    LaunchedEffect(assets) {
        if (aiOutput.selectedAsset == null && assets.isNotEmpty()) {
            val defaultTarget = assets.firstOrNull { it.typeEnum != MediaItemType.VIDEO } ?: assets.first()
            viewModel.selectAssetForAiStudio(defaultTarget)
        }
    }

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
    val visibleTools = remember(isVideoSelected) {
        if (isVideoSelected) {
            AiToolType.entries.filter { it.isVideoOnly || it == AiToolType.ENHANCE_ALL }
        } else {
            AiToolType.entries.filter { it.isPhotoCompatible }
        }
    }
    if (selectedTool !in visibleTools && visibleTools.isNotEmpty()) {
        selectedTool = visibleTools.first()
    }

    val featuredTool = remember(visibleTools) {
        visibleTools.firstOrNull { it == AiToolType.ENHANCE_ALL }
    }
    val secondaryTools = remember(visibleTools) {
        visibleTools.filter { it != AiToolType.ENHANCE_ALL }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Compact Header (No overflow on narrow widths)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "AI Lab",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = LevelGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Xử lý cục bộ • Sắc nét & HDR",
                        style = MonoTelemetrySmall.copy(fontSize = 10.5.sp),
                        color = MutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(ElevatedObsidian)
                    .border(0.75.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(13.dp))
                    .clickable {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                    .padding(horizontal = 12.dp)
                    .testTag("ai_studio_pick_media"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Nhập ảnh hoặc video",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Nhập ảnh",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = PureWhite,
                    softWrap = false
                )
            }
        }

        // 2. Darkroom Before/After Comparison Stage + Result Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ObsidianSurface)
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(20.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(ElevatedObsidian),
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

                        if (holdingOriginal) {
                            Image(
                                bitmap = origBmp.asImageBitmap(),
                                contentDescription = "Ảnh gốc",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "ĐANG XEM ẢNH GỐC",
                                style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = PureWhite,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(10.dp)
                                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        } else {
                            Image(
                                bitmap = enhBmp.asImageBitmap(),
                                contentDescription = "Ảnh sau khi nâng cấp AI",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(splitFraction)
                                    .clipToBounds()
                            ) {
                                Image(
                                    bitmap = origBmp.asImageBitmap(),
                                    contentDescription = "Ảnh gốc",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(containerWidth)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset((widthPx * splitFraction).roundToInt(), 0) }
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .background(accent)
                            )
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
                                    contentDescription = "Kéo để so sánh Trước và Sau",
                                    tint = onAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "GỐC",
                                style = MonoTelemetrySmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = PureWhite,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                            Text(
                                text = "SAU AI",
                                style = MonoTelemetrySmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = accent,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.74f))
                                .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            holdingOriginal = true
                                            tryAwaitRelease()
                                            holdingOriginal = false
                                        }
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = "Nhấn giữ xem ảnh gốc",
                                tint = PureWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Giữ xem gốc",
                                style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                                color = PureWhite
                            )
                        }
                    }
                } else if (selectedAsset != null) {
                    val stageRequest = remember(selectedAsset.mediaStoreUri, context) {
                        ImageRequest.Builder(context)
                            .data(selectedAsset.mediaStoreUri)
                            .size(1024)
                            .precision(Precision.INEXACT)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .build()
                    }
                    AsyncImage(
                        model = stageRequest,
                        contentDescription = selectedAsset.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.14f))
                                .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Chọn ảnh hoặc video để làm nét",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Khử nhiễu đêm, tái tạo nét HD, cân HDR & nâng 2x",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            aiOutput.imageResult?.let { res ->
                HorizontalDivider(thickness = 0.6.dp, color = HairlineBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "${res.originalWidth}×${res.originalHeight} → ${res.outputWidth}×${res.outputHeight}",
                            style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                        Text(
                            text = "${res.summaryLabel} • ${res.processingTimeMs} ms",
                            style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                            color = accent
                        )
                    }
                    Button(
                        onClick = { viewModel.saveAiEnhancedPhotoCopy() },
                        enabled = !aiOutput.savedCopySuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("ai_save_copy_button")
                    ) {
                        Icon(
                            imageVector = if (aiOutput.savedCopySuccess) Icons.Default.CheckCircle else Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (aiOutput.savedCopySuccess) "Đã lưu" else "Lưu bản mới")
                    }
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
                    text = "Video AI đã lưu vào Thư viện (${aiOutput.processingTimeMs} ms)",
                    style = MonoTelemetrySmall,
                    color = PureWhite
                )
            }
        }

        // 3. Fast Recent Gallery Strip
        if (assets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                assets.take(12).forEach { asset ->
                    val isSelected = selectedAsset?.id == asset.id
                    val thumbReq = remember(asset.mediaStoreUri, context) {
                        ImageRequest.Builder(context)
                            .data(asset.mediaStoreUri)
                            .size(180)
                            .precision(Precision.INEXACT)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .build()
                    }
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.75.dp,
                                color = if (isSelected) accent else HairlineBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectAssetForAiStudio(asset) }
                            .testTag("ai_recent_asset_${asset.id}")
                    ) {
                        AsyncImage(
                            model = thumbReq,
                            contentDescription = asset.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // 4. Symmetrical AI Tools Layout (Featured 1-Tap Hero + Balanced 2x2 Grid, zero truncation)
        Text(
            text = "Công cụ phục hồi & Làm nét",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PureWhite
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            featuredTool?.let { hero ->
                val selected = selectedTool == hero
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) accent.copy(alpha = 0.16f) else CardSlate)
                        .border(
                            width = if (selected) 1.5.dp else 0.75.dp,
                            color = if (selected) accent else HairlineBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = !aiProgress.isRunning) { selectedTool = hero }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("ai_tool_${hero.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) accent else ElevatedObsidian),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = aiToolIcon(hero),
                            contentDescription = null,
                            tint = if (selected) onAccent else accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = hero.label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = PureWhite
                        )
                        Text(
                            text = "1 chạm: Khử nhiễu + HDR cân màu + Làm nét sâu HD",
                            style = MonoTelemetrySmall.copy(fontSize = 10.5.sp),
                            color = if (selected) accent else MutedSlate
                        )
                    }
                }
            }

            val toolRows = remember(secondaryTools) { secondaryTools.chunked(2) }
            toolRows.forEach { rowTools ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTools.forEach { tool ->
                        val selected = selectedTool == tool
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) accent.copy(alpha = 0.14f) else CardSlate)
                                .border(
                                    width = if (selected) 1.5.dp else 0.75.dp,
                                    color = if (selected) accent else HairlineBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(enabled = !aiProgress.isRunning) { selectedTool = tool }
                                .padding(horizontal = 11.dp, vertical = 11.dp)
                                .testTag("ai_tool_${tool.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) accent else ElevatedObsidian),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = aiToolIcon(tool),
                                    contentDescription = null,
                                    tint = if (selected) onAccent else accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = tool.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = aiToolBenefit(tool),
                                    style = MonoTelemetrySmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                                    color = if (selected) accent else MutedSlate,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    if (rowTools.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 5. Sleek 3-Segment Quality Track (clean helper line below, zero header collision)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Cường độ xử lý",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PureWhite
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedObsidian)
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AiProcessingMode.entries.forEach { m ->
                    val selected = selectedMode == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accent else Color.Transparent)
                            .clickable(enabled = !aiProgress.isRunning) { selectedMode = m }
                            .testTag("ai_mode_${m.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = m.label,
                            style = MonoTelemetryLarge.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                            color = if (selected) onAccent else MutedSlate,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = selectedMode.subtitle,
                style = MonoTelemetrySmall.copy(fontSize = 10.5.sp),
                color = MutedSlate
            )
        }

        // 6. Active Progress & Cancel Panel
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
                            Text("Hủy")
                        }
                    }
                }
            }
        }

        // 7. Primary AI Run CTA
        Button(
            onClick = { viewModel.runAiStudioProcessing(selectedTool, selectedMode) },
            enabled = selectedAsset != null && !aiProgress.isRunning,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("ai_run_button")
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedAsset == null) "Chọn ảnh hoặc video trước" else "Chạy ${selectedTool.label}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        // 8. Creative Utility Dock
        Text(
            text = "Công cụ sáng tạo",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PureWhite
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(CardSlate, accent.copy(alpha = 0.12f))
                        )
                    )
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(16.dp))
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
                    Icon(Icons.Default.MergeType, contentDescription = "Ghép Video", tint = onAccent)
                }
                Column {
                    Text(
                        text = "Ghép Video",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
                        color = PureWhite,
                        maxLines = 1
                    )
                    Text(
                        text = "Nối & Chia đôi clip",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                        color = MutedSlate,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSlate)
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(16.dp))
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
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Sticker & Meme", tint = accent)
                }
                Column {
                    Text(
                        text = "Sticker & Meme",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
                        color = PureWhite,
                        maxLines = 1
                    )
                    Text(
                        text = "Tem & Chữ vui",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                        color = MutedSlate,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun aiToolIcon(tool: AiToolType) = when (tool) {
    AiToolType.ENHANCE_ALL -> Icons.Default.AutoAwesome
    AiToolType.DENOISE -> Icons.Default.BlurOff
    AiToolType.SHARPEN -> Icons.Default.CenterFocusStrong
    AiToolType.UPSCALE -> Icons.Default.Hd
    AiToolType.COLOR_CORRECTION -> Icons.Default.Palette
    AiToolType.VIDEO_STABILIZE -> Icons.Default.VideoStable
    AiToolType.FRAME_ENHANCE -> Icons.Default.AutoFixHigh
    AiToolType.FRAME_INTERPOLATE -> Icons.Default.SlowMotionVideo
}

private fun aiToolBenefit(tool: AiToolType): String = when (tool) {
    AiToolType.ENHANCE_ALL -> "Khử nhiễu + HDR + Nét sâu"
    AiToolType.DENOISE -> "Sạch nhiễu ảnh đêm"
    AiToolType.SHARPEN -> "Rõ từng chi tiết cạnh"
    AiToolType.UPSCALE -> "Nhân 2x độ phân giải"
    AiToolType.COLOR_CORRECTION -> "HDR & cân màu chuẩn"
    AiToolType.VIDEO_STABILIZE -> "Khử rung gyro mượt"
    AiToolType.FRAME_ENHANCE -> "Làm nét khung video"
    AiToolType.FRAME_INTERPOLATE -> "Nội suy lên 60fps"
}
