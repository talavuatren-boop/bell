package com.example.ui.screens

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.MediaAssetEntity
import com.example.model.MediaItemType
import com.example.ui.MainViewModel
import com.example.ui.components.SpringIconButton
import com.example.ui.components.formatShutterSpeed
import com.example.ui.components.springPressScale
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    assets: List<MediaAssetEntity>,
    initialIndex: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPhotoEditor: (MediaAssetEntity) -> Unit,
    onOpenVideoEditor: (MediaAssetEntity) -> Unit,
    onOpenAiStudio: (MediaAssetEntity) -> Unit
) {
    BackHandler(onBack = onBack)

    if (assets.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, assets.lastIndex),
        pageCount = { assets.size }
    )
    val currentAsset = assets.getOrNull(pagerState.currentPage) ?: assets.first()

    var showInfoSheet by remember { mutableStateOf(false) }
    var audioEnabled by remember { mutableStateOf(true) }
    var deletingAssetId by remember { mutableStateOf<Long?>(null) }
    var pendingConfirmDeleteAsset by remember { mutableStateOf<MediaAssetEntity?>(null) }

    val favoriteBounce = remember { Animatable(1f) }
    LaunchedEffect(currentAsset.id, currentAsset.isFavorite) {
        if (currentAsset.isFavorite) {
            favoriteBounce.snapTo(1.38f)
            favoriteBounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            favoriteBounce.snapTo(1f)
        }
    }

    LaunchedEffect(assets.size) {
        if (assets.isNotEmpty() && pagerState.currentPage >= assets.size) {
            pagerState.scrollToPage((assets.size - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> assets[page].id },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val asset = assets[page]
            val isBeingDeleted = deletingAssetId == asset.id
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val pagerScale = (1f - (pageOffset.coerceIn(0f, 1f) * 0.06f))
            val pagerAlpha = (1f - (pageOffset.coerceIn(0f, 1f) * 0.25f))

            val deleteScale by animateFloatAsState(
                targetValue = if (isBeingDeleted) 0.78f else 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "deletePageScale"
            )
            val deleteAlpha by animateFloatAsState(
                targetValue = if (isBeingDeleted) 0f else 1f,
                animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
                label = "deletePageAlpha"
            )
            val deleteShiftY by animateFloatAsState(
                targetValue = if (isBeingDeleted) 120f else 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "deletePageShiftY"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = pagerScale * deleteScale,
                        scaleY = pagerScale * deleteScale,
                        alpha = (pagerAlpha * deleteAlpha).coerceIn(0f, 1f),
                        translationY = deleteShiftY
                    )
            ) {
                when (asset.typeEnum) {
                    MediaItemType.VIDEO -> {
                        VideoPlayerPane(
                            uri = Uri.parse(asset.mediaStoreUri),
                            audioEnabled = audioEnabled,
                            isCurrentPage = pagerState.currentPage == page
                        )
                    }
                    MediaItemType.LIVE_PHOTO -> {
                        LivePhotoViewerPane(
                            asset = asset,
                            audioEnabled = audioEnabled,
                            isCurrentPage = pagerState.currentPage == page,
                            onHaptic = { viewModel.performHapticTick() }
                        )
                    }
                    else -> {
                        ZoomablePhotoPane(uri = asset.mediaStoreUri, contentDescription = asset.displayName)
                    }
                }
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpringIconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureWhite)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (currentAsset.typeEnum == MediaItemType.LIVE_PHOTO) "LIVE PHOTO • TAP OR HOLD" else currentAsset.displayName,
                    style = MonoTelemetrySmall,
                    color = if (currentAsset.typeEnum == MediaItemType.LIVE_PHOTO) accent else PureWhite
                )
                Text(
                    text = "${pagerState.currentPage + 1}/${assets.size} • ${currentAsset.width}×${currentAsset.height}",
                    style = MonoTelemetrySmall,
                    color = MutedSlate
                )
            }

            Row {
                if (currentAsset.typeEnum == MediaItemType.VIDEO || currentAsset.typeEnum == MediaItemType.LIVE_PHOTO) {
                    SpringIconButton(
                        onClick = { audioEnabled = !audioEnabled },
                        modifier = Modifier.testTag("viewer_audio_toggle")
                    ) {
                        Icon(
                            imageVector = if (audioEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Toggle Audio",
                            tint = if (audioEnabled) accent else MutedSlate
                        )
                    }
                }
                SpringIconButton(
                    onClick = { showInfoSheet = true },
                    modifier = Modifier.testTag("viewer_info_button")
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Media Info", tint = PureWhite)
                }
            }
        }

        // Bottom Action Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpringIconButton(
                onClick = {
                    viewModel.performHapticTick()
                    scope.launch { viewModel.mediaRepo.toggleFavorite(currentAsset) }
                },
                extraScale = favoriteBounce.value,
                modifier = Modifier.testTag("viewer_favorite_button")
            ) {
                Icon(
                    imageVector = if (currentAsset.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (currentAsset.isFavorite) CrimsonShutter else PureWhite
                )
            }

            SpringIconButton(
                onClick = {
                    val intent = viewModel.mediaRepo.buildShareIntent(currentAsset)
                    context.startActivity(android.content.Intent.createChooser(intent, "Share via HUNG CAM"))
                },
                modifier = Modifier.testTag("viewer_share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = PureWhite)
            }

            SpringIconButton(
                onClick = {
                    if (currentAsset.typeEnum == MediaItemType.VIDEO) onOpenVideoEditor(currentAsset)
                    else onOpenPhotoEditor(currentAsset)
                },
                modifier = Modifier.testTag("viewer_edit_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Media", tint = PureWhite)
            }

            SpringIconButton(
                onClick = { onOpenAiStudio(currentAsset) },
                modifier = Modifier.testTag("viewer_ai_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Enhance in AI Studio", tint = accent)
            }

            val isDeleteBusy = deletingAssetId != null
            SpringIconButton(
                onClick = {
                    if (isDeleteBusy) return@SpringIconButton
                    viewModel.performHapticTick()
                    pendingConfirmDeleteAsset = currentAsset
                },
                enabled = !isDeleteBusy,
                pressedScale = 0.78f,
                extraScale = if (deletingAssetId == currentAsset.id) 1.25f else 1f,
                modifier = Modifier.testTag("viewer_delete_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonShutter)
            }
        }
    }

    pendingConfirmDeleteAsset?.let { target ->
        val isTrashItem = target.isDeleted
        AlertDialog(
            onDismissRequest = { pendingConfirmDeleteAsset = null },
            containerColor = ElevatedObsidian,
            titleContentColor = PureWhite,
            textContentColor = MutedSlate,
            title = {
                Text(if (isTrashItem) "Bạn có chắc muốn xóa vĩnh viễn?" else "Bạn có chắc muốn xóa không?")
            },
            text = {
                Text(
                    if (isTrashItem) {
                        "Mục \"${target.displayName}\" sẽ bị xóa vĩnh viễn và không thể khôi phục."
                    } else {
                        "Mục \"${target.displayName}\" sẽ được chuyển vào Thùng rác (Trash)."
                    }
                )
            },
            confirmButton = {
                val confirmInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        pendingConfirmDeleteAsset = null
                        viewModel.performHapticTick()
                        val isLastItem = assets.size <= 1
                        deletingAssetId = target.id
                        scope.launch {
                            delay(230L)
                            if (target.isDeleted) {
                                viewModel.mediaRepo.permanentlyDelete(target)
                            } else {
                                viewModel.mediaRepo.moveToTrash(target)
                            }
                            deletingAssetId = null
                            if (isLastItem) onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonShutter, contentColor = PureWhite),
                    modifier = Modifier
                        .springPressScale(confirmInteraction, pressedScale = 0.88f)
                        .testTag("viewer_confirm_delete_button"),
                    interactionSource = confirmInteraction
                ) {
                    Text(if (isTrashItem) "Xóa vĩnh viễn" else "Xóa")
                }
            },
            dismissButton = {
                val cancelInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { pendingConfirmDeleteAsset = null },
                    modifier = Modifier
                        .springPressScale(cancelInteraction, pressedScale = 0.92f)
                        .testTag("viewer_cancel_delete_button"),
                    interactionSource = cancelInteraction
                ) {
                    Text("Hủy", color = PureWhite)
                }
            }
        )
    }

    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            containerColor = ElevatedObsidian,
            contentColor = PureWhite
        ) {
            val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
            val sizeMb = String.format(Locale.US, "%.2f MB", currentAsset.fileSizeBytes / (1024f * 1024f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Media Telemetry & EXIF", style = MaterialTheme.typography.titleLarge, color = accent)
                InfoRow("Filename", currentAsset.displayName)
                InfoRow("Type", currentAsset.typeEnum.name)
                InfoRow("Resolution", "${currentAsset.width} × ${currentAsset.height}")
                InfoRow("File Size", sizeMb)
                if (currentAsset.fps > 0) InfoRow("Frame Rate", "${currentAsset.fps} FPS")
                if (currentAsset.bitrateBps > 0) InfoRow("Bitrate", "${currentAsset.bitrateBps / 1_000_000} Mbps")
                InfoRow("Capture Date", sdf.format(Date(currentAsset.captureDateMs)))
                if (currentAsset.lensLabel.isNotBlank()) InfoRow("Optics", currentAsset.lensLabel)
                if (currentAsset.iso != null) InfoRow("ISO", "ISO ${currentAsset.iso}")
                if (currentAsset.exposureTimeNs != null) InfoRow("Shutter", formatShutterSpeed(currentAsset.exposureTimeNs))
                if (currentAsset.focalLengthMm != null) InfoRow("Focal Length", "${currentAsset.focalLengthMm} mm")
                if (currentAsset.livePairId != null) {
                    InfoRow("Live Pair ID", currentAsset.livePairId.take(12))
                    InfoRow("Live Duration", "${currentAsset.liveDurationSec}s (${if (currentAsset.liveHasAudio) "Audio" else "Silent"})")
                    InfoRow("Shutter Sync Offset", "${currentAsset.shutterOffsetMs} ms")
                }
                if (currentAsset.aiEnhancementTag != null) {
                    InfoRow("AI Lineage", currentAsset.aiEnhancementTag)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedSlate)
        Text(value, style = MonoTelemetrySmall, color = PureWhite)
    }
}

@Composable
private fun ZoomablePhotoPane(uri: String, contentDescription: String) {
    var targetScale by remember(uri) { mutableFloatStateOf(1f) }
    var targetOffsetX by remember(uri) { mutableFloatStateOf(0f) }
    var targetOffsetY by remember(uri) { mutableFloatStateOf(0f) }

    val animScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "photoScale"
    )
    val animOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "photoOffsetX"
    )
    val animOffsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "photoOffsetY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Custom pinch/pan detector: at 1x scale, single-finger drags stay unconsumed
            // so HorizontalPager swipes left/right effortlessly across photos.
            .pointerInput(uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val pointerCount = event.changes.count { it.pressed }

                        if (pointerCount >= 2 || targetScale > 1.03f || zoomChange != 1f) {
                            val newScale = (targetScale * zoomChange).coerceIn(1f, 5f)
                            targetScale = newScale
                            if (newScale > 1.03f) {
                                val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                targetOffsetX = (targetOffsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                targetOffsetY = (targetOffsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                event.changes.forEach { it.consume() }
                            } else {
                                targetScale = 1f
                                targetOffsetX = 0f
                                targetOffsetY = 0f
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(uri) {
                detectTapGestures(
                    onDoubleTap = {
                        if (targetScale > 1.1f) {
                            targetScale = 1f
                            targetOffsetX = 0f
                            targetOffsetY = 0f
                        } else {
                            targetScale = 2.5f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = animScale,
                    scaleY = animScale,
                    translationX = animOffsetX,
                    translationY = animOffsetY
                )
        )
    }
}

private enum class LivePlaybackPhase {
    STILL,
    PLAYING,
    BLUR_SETTLE
}

@Composable
private fun LivePhotoViewerPane(
    asset: MediaAssetEntity,
    audioEnabled: Boolean,
    isCurrentPage: Boolean,
    onHaptic: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    var phase by remember(asset.id) { mutableStateOf(LivePlaybackPhase.STILL) }
    var isVideoFrameReady by remember(asset.id) { mutableStateOf(false) }
    var isLongPressHolding by remember(asset.id) { mutableStateOf(false) }
    var player by remember(asset.id) { mutableStateOf<MediaPlayer?>(null) }
    var videoAspect by remember(asset.id) {
        val fallback = if (asset.width > 0 && asset.height > 0) asset.width.toFloat() / asset.height.toFloat() else 3f / 4f
        mutableFloatStateOf(fallback)
    }

    LaunchedEffect(asset.liveVideoUri) {
        val vUri = asset.liveVideoUri?.let { Uri.parse(it) } ?: return@LaunchedEffect
        val ratio = withContext(Dispatchers.IO) { readVideoDisplayAspect(context, vUri) }
        if (ratio != null && ratio > 0f) videoAspect = ratio
    }

    fun startLivePlayback() {
        if (asset.liveVideoUri.isNullOrBlank()) return
        onHaptic()
        isVideoFrameReady = false
        phase = LivePlaybackPhase.PLAYING
    }

    fun transitionToStillWithBlur() {
        if (phase != LivePlaybackPhase.PLAYING) return
        phase = LivePlaybackPhase.BLUR_SETTLE
    }

    // Smooth ~460ms optical blur & cross-dissolve settle before releasing/rewinding video layer
    LaunchedEffect(phase) {
        if (phase == LivePlaybackPhase.BLUR_SETTLE) {
            delay(460L)
            runCatching {
                player?.pause()
                player?.seekTo(0)
            }
            isVideoFrameReady = false
            phase = LivePlaybackPhase.STILL
        }
    }

    // Pause and reset cleanly when user swipes to another page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && phase != LivePlaybackPhase.STILL) {
            isLongPressHolding = false
            runCatching {
                player?.pause()
                player?.seekTo(0)
            }
            isVideoFrameReady = false
            phase = LivePlaybackPhase.STILL
        }
    }

    DisposableEffect(asset.liveVideoUri) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    LaunchedEffect(audioEnabled, player) {
        val vol = if (audioEnabled) 1f else 0f
        runCatching { player?.setVolume(vol, vol) }
    }

    // Animated optical blur + crossfade values
    val showVideoSurface = phase != LivePlaybackPhase.STILL && !asset.liveVideoUri.isNullOrBlank()
    val videoVisibleTarget = phase == LivePlaybackPhase.PLAYING && isVideoFrameReady

    val videoAlpha by animateFloatAsState(
        targetValue = if (videoVisibleTarget) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (phase == LivePlaybackPhase.BLUR_SETTLE) 440 else 180,
            easing = FastOutSlowInEasing
        ),
        label = "liveVideoAlpha"
    )
    val videoScale by animateFloatAsState(
        targetValue = if (phase == LivePlaybackPhase.PLAYING) 1.035f else 1.0f,
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "liveVideoScale"
    )
    val videoBlurDp by animateDpAsState(
        targetValue = if (phase == LivePlaybackPhase.BLUR_SETTLE) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "liveVideoBlur"
    )

    // Still image stays mounted beneath; during BLUR_SETTLE it unblurs smoothly from 10.dp -> 0.dp
    val stillBlurDp by animateDpAsState(
        targetValue = when (phase) {
            LivePlaybackPhase.BLUR_SETTLE -> 0.dp
            LivePlaybackPhase.PLAYING -> 10.dp
            LivePlaybackPhase.STILL -> 0.dp
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "liveStillBlur"
    )
    val stillAlpha by animateFloatAsState(
        targetValue = when (phase) {
            LivePlaybackPhase.PLAYING -> if (isVideoFrameReady) 0.72f else 1f
            LivePlaybackPhase.BLUR_SETTLE -> 1f
            LivePlaybackPhase.STILL -> 1f
        },
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "liveStillAlpha"
    )
    val stillScale by animateFloatAsState(
        targetValue = if (phase == LivePlaybackPhase.PLAYING) 1.018f else 1f,
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "liveStillScale"
    )
    // Gentle misty veil during blur settle (ensures silky blur feel even below Android 12)
    val mistOverlayAlpha by animateFloatAsState(
        targetValue = if (phase == LivePlaybackPhase.BLUR_SETTLE) 0.14f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "liveMistAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(asset.liveVideoUri) {
                detectTapGestures(
                    onTap = {
                        when (phase) {
                            LivePlaybackPhase.STILL -> startLivePlayback()
                            LivePlaybackPhase.PLAYING -> transitionToStillWithBlur()
                            LivePlaybackPhase.BLUR_SETTLE -> Unit
                        }
                    },
                    onLongPress = {
                        isLongPressHolding = true
                        startLivePlayback()
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isLongPressHolding) {
                            isLongPressHolding = false
                            transitionToStillWithBlur()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Persistent crisp Still Photo base layer (never unmounted -> zero black flash)
        val stillBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && stillBlurDp > 0.2.dp) {
            Modifier.blur(stillBlurDp, BlurredEdgeTreatment.Rectangle)
        } else {
            Modifier
        }
        AsyncImage(
            model = asset.mediaStoreUri,
            contentDescription = asset.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    alpha = stillAlpha,
                    scaleX = stillScale,
                    scaleY = stillScale
                )
                .then(stillBlurModifier)
        )

        // Matched-aspect Live Video overlay
        if (showVideoSurface) {
            val stillAspect = if (asset.width > 0 && asset.height > 0) {
                asset.width.toFloat() / asset.height.toFloat()
            } else {
                videoAspect
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val outerRatio = (maxWidth / maxHeight).coerceAtLeast(0.1f)
                val frameW = if (stillAspect > outerRatio) maxWidth else maxHeight * stillAspect
                val frameH = if (stillAspect > outerRatio) maxWidth / stillAspect else maxHeight
                val coverW = if (videoAspect > stillAspect) frameH * videoAspect else frameW
                val coverH = if (videoAspect > stillAspect) frameH else frameW / videoAspect

                val vidBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && videoBlurDp > 0.2.dp) {
                    Modifier.blur(videoBlurDp, BlurredEdgeTreatment.Rectangle)
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .requiredSize(frameW, frameH)
                        .clipToBounds()
                        .graphicsLayer(
                            alpha = videoAlpha,
                            scaleX = videoScale,
                            scaleY = videoScale
                        )
                        .then(vidBlurModifier),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                        val mp = MediaPlayer().apply {
                                            val vUri = Uri.parse(asset.liveVideoUri)
                                            if (vUri.scheme == "file" && !vUri.path.isNullOrBlank()) {
                                                setDataSource(vUri.path)
                                            } else {
                                                setDataSource(ctx, vUri)
                                            }
                                            setSurface(Surface(st))
                                            isLooping = false
                                            val vol = if (audioEnabled) 1f else 0f
                                            setVolume(vol, vol)
                                            setOnInfoListener { _, what, _ ->
                                                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                                    isVideoFrameReady = true
                                                }
                                                false
                                            }
                                            setOnPreparedListener { readyMp ->
                                                readyMp.start()
                                                // Fallback in case a chipset omits MEDIA_INFO_VIDEO_RENDERING_START
                                                postDelayed({ isVideoFrameReady = true }, 120L)
                                            }
                                            setOnCompletionListener {
                                                isLongPressHolding = false
                                                transitionToStillWithBlur()
                                            }
                                            prepareAsync()
                                        }
                                        player = mp
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) = Unit
                                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                                        runCatching { player?.release() }
                                        player = null
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) = Unit
                                }
                            }
                        },
                        modifier = Modifier.requiredSize(coverW, coverH)
                    )
                }
            }
        }

        // Soft atmospheric transition sheen during blur settle
        if (mistOverlayAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = mistOverlayAlpha))
            )
        }

        // Live indicator pill (tappable shortcut as well)
        val livePillInteraction = remember(asset.id) { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 92.dp)
                .springPressScale(livePillInteraction, pressedScale = 0.90f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(0.75.dp, if (phase == LivePlaybackPhase.PLAYING) accent else HairlineBorder, RoundedCornerShape(14.dp))
                .clickable(interactionSource = livePillInteraction, indication = null) {
                    when (phase) {
                        LivePlaybackPhase.STILL -> startLivePlayback()
                        LivePlaybackPhase.PLAYING -> transitionToStillWithBlur()
                        LivePlaybackPhase.BLUR_SETTLE -> Unit
                    }
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.MotionPhotosOn, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            Text(
                text = when (phase) {
                    LivePlaybackPhase.STILL -> "TAP OR HOLD • LIVE ${asset.liveDurationSec}s"
                    LivePlaybackPhase.PLAYING -> "PLAYING LIVE ${asset.liveDurationSec}s"
                    LivePlaybackPhase.BLUR_SETTLE -> "BLUR TRANSITION..."
                },
                style = MonoTelemetrySmall,
                color = PureWhite
            )
        }
    }
}

@Composable
private fun VideoPlayerPane(
    uri: Uri,
    audioEnabled: Boolean,
    isCurrentPage: Boolean
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoAspect by remember(uri) { mutableFloatStateOf(9f / 16f) }

    LaunchedEffect(uri) {
        val ratio = withContext(Dispatchers.IO) { readVideoDisplayAspect(context, uri) }
        if (ratio != null && ratio > 0f) videoAspect = ratio
    }

    LaunchedEffect(isCurrentPage, mediaPlayer) {
        if (!isCurrentPage) {
            runCatching { mediaPlayer?.pause() }
            isPlaying = false
        }
    }

    LaunchedEffect(audioEnabled, mediaPlayer) {
        val vol = if (audioEnabled) 1f else 0f
        runCatching { mediaPlayer?.setVolume(vol, vol) }
    }

    DisposableEffect(uri) {
        onDispose {
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxRatio = maxWidth / maxHeight
        val fitMod = if (videoAspect > boxRatio) {
            Modifier.fillMaxWidth().aspectRatio(videoAspect)
        } else {
            Modifier.fillMaxHeight().aspectRatio(videoAspect)
        }
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                            val mp = MediaPlayer().apply {
                                setDataSource(ctx, uri)
                                setSurface(Surface(st))
                                isLooping = true
                                val vol = if (audioEnabled) 1f else 0f
                                setVolume(vol, vol)
                                setOnPreparedListener {
                                    if (isCurrentPage) {
                                        it.start()
                                        isPlaying = true
                                    }
                                }
                                prepareAsync()
                            }
                            mediaPlayer = mp
                        }
                        override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                            runCatching { mediaPlayer?.release() }
                            mediaPlayer = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) = Unit
                    }
                }
            },
            modifier = fitMod
        )

        SpringIconButton(
            onClick = {
                val mp = mediaPlayer ?: return@SpringIconButton
                if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                } else {
                    mp.start()
                    isPlaying = true
                }
            },
            pressedScale = 0.82f,
            modifier = Modifier
                .size(60.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .border(1.dp, HairlineBorder, CircleShape)
                .testTag("video_play_pause_button")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = PureWhite,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun readVideoDisplayAspect(context: android.content.Context, uri: Uri): Float? {
    return runCatching {
        val r = MediaMetadataRetriever()
        if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            r.setDataSource(uri.path)
        } else {
            r.setDataSource(context, uri)
        }
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        r.release()
        if (w <= 0 || h <= 0) return null
        if (rot == 90 || rot == 270) h.toFloat() / w.toFloat() else w.toFloat() / h.toFloat()
    }.getOrNull()
}
