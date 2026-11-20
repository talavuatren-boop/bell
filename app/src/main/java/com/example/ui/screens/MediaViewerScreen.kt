package com.example.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.example.ui.components.formatShutterSpeed
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val asset = assets[page]
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
                        onHaptic = { viewModel.performHapticTick() }
                    )
                }
                else -> {
                    ZoomablePhotoPane(uri = asset.mediaStoreUri, contentDescription = asset.displayName)
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
            IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureWhite)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (currentAsset.typeEnum == MediaItemType.LIVE_PHOTO) "LIVE PHOTO • HOLD TO PLAY" else currentAsset.displayName,
                    style = MonoTelemetrySmall,
                    color = if (currentAsset.typeEnum == MediaItemType.LIVE_PHOTO) accent else PureWhite
                )
                Text(
                    text = "${currentAsset.width}×${currentAsset.height}",
                    style = MonoTelemetrySmall,
                    color = MutedSlate
                )
            }

            Row {
                if (currentAsset.typeEnum == MediaItemType.VIDEO || currentAsset.typeEnum == MediaItemType.LIVE_PHOTO) {
                    IconButton(
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
                IconButton(
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
            IconButton(
                onClick = { scope.launch { viewModel.mediaRepo.toggleFavorite(currentAsset) } },
                modifier = Modifier.testTag("viewer_favorite_button")
            ) {
                Icon(
                    imageVector = if (currentAsset.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (currentAsset.isFavorite) CrimsonShutter else PureWhite
                )
            }

            IconButton(
                onClick = {
                    val intent = viewModel.mediaRepo.buildShareIntent(currentAsset)
                    context.startActivity(android.content.Intent.createChooser(intent, "Share via HUNG CAM"))
                },
                modifier = Modifier.testTag("viewer_share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = PureWhite)
            }

            IconButton(
                onClick = {
                    if (currentAsset.typeEnum == MediaItemType.VIDEO) onOpenVideoEditor(currentAsset)
                    else onOpenPhotoEditor(currentAsset)
                },
                modifier = Modifier.testTag("viewer_edit_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Media", tint = PureWhite)
            }

            IconButton(
                onClick = { onOpenAiStudio(currentAsset) },
                modifier = Modifier.testTag("viewer_ai_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Enhance in AI Studio", tint = accent)
            }

            IconButton(
                onClick = {
                    scope.launch {
                        if (currentAsset.isDeleted) {
                            viewModel.mediaRepo.permanentlyDelete(currentAsset)
                        } else {
                            viewModel.mediaRepo.moveToTrash(currentAsset)
                        }
                        if (assets.size <= 1) onBack()
                    }
                },
                modifier = Modifier.testTag("viewer_delete_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonShutter)
            }
        }
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
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                        } else {
                            scale = 2.5f
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
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun LivePhotoViewerPane(
    asset: MediaAssetEntity,
    audioEnabled: Boolean,
    onHaptic: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    var isPlayingLive by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(asset.liveVideoUri) {
                detectTapGestures(
                    onLongPress = {
                        if (!asset.liveVideoUri.isNullOrBlank()) {
                            onHaptic()
                            isPlayingLive = true
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        isPlayingLive = false
                        runCatching {
                            player?.pause()
                            player?.seekTo(0)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isPlayingLive && !asset.liveVideoUri.isNullOrBlank()) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                val mp = MediaPlayer().apply {
                                    setDataSource(ctx, Uri.parse(asset.liveVideoUri))
                                    setSurface(Surface(st))
                                    isLooping = true
                                    val vol = if (audioEnabled) 1f else 0f
                                    setVolume(vol, vol)
                                    setOnPreparedListener { it.start() }
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
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = asset.mediaStoreUri,
                contentDescription = asset.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtle Live indicator pill
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 92.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                .border(0.75.dp, if (isPlayingLive) accent else HairlineBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.MotionPhotosOn, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            Text(
                text = if (isPlayingLive) "PLAYING LIVE ${asset.liveDurationSec}s" else "PRESS & HOLD FOR LIVE",
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
    var isPlaying by remember { mutableStateOf(true) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = {
                val mp = mediaPlayer ?: return@IconButton
                if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                } else {
                    mp.start()
                    isPlaying = true
                }
            },
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
