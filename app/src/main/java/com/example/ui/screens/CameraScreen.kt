package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.AspectRatioMode
import com.example.model.CameraMode
import com.example.model.FlashMode
import com.example.model.LivePhotoDuration
import com.example.model.SelfTimerDuration
import com.example.model.StabilizationMode
import com.example.model.TimelapseSpeed
import com.example.ui.MainViewModel
import com.example.ui.components.ElectronicLevelOverlay
import com.example.ui.components.FocusPeakingOverlay
import com.example.ui.components.GridOverlay
import com.example.ui.components.LiveHistogramHud
import com.example.ui.components.ProControlDeck
import com.example.ui.components.SelfTimerCountdownHud
import com.example.ui.components.TapFocusReticle
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetryLarge
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    onOpenGallery: () -> Unit,
    onOpenStickersMeme: (com.example.data.MediaAssetEntity) -> Unit = {},
    onOpenVideoMerge: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val activeLens by viewModel.activeLens.collectAsStateWithLifecycle()
    val mode by viewModel.cameraMode.collectAsStateWithLifecycle()
    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val liveDuration by viewModel.liveDuration.collectAsStateWithLifecycle()
    val selfTimer by viewModel.selfTimer.collectAsStateWithLifecycle()
    val countdown by viewModel.countdownRemaining.collectAsStateWithLifecycle()
    val proParams by viewModel.proParams.collectAsStateWithLifecycle()
    val runtime by viewModel.cameraRuntime.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val focusPeaking by viewModel.focusPeakingData.collectAsStateWithLifecycle()
    val timelapseSpeed by viewModel.timelapseSpeed.collectAsStateWithLifecycle()
    val slowMoOption by viewModel.slowMoOption.collectAsStateWithLifecycle()
    val assets by viewModel.activeAssets.collectAsStateWithLifecycle()

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPerm = perms[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPerm = perms[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPerm) {
            viewModel.refreshCapabilities()
            viewModel.cameraEngine.openCameraIfReady()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm || !hasAudioPerm) {
            permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    DisposableEffect(Unit) {
        viewModel.sensorTelemetry.start()
        onDispose {
            viewModel.sensorTelemetry.stop()
        }
    }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusReticle by remember { mutableStateOf(false) }
    var showZoomSlider by remember { mutableStateOf(false) }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            showFocusReticle = true
            delay(1400L)
            showFocusReticle = false
        }
    }

    LaunchedEffect(runtime.statusBanner, runtime.errorMessage) {
        if (runtime.statusBanner != null || runtime.errorMessage != null) {
            delay(2800L)
            viewModel.cameraEngine.clearStatusMessages()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        if (!hasCameraPerm) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "HUNG CAM Optical Engine",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite
                )
                Text(
                    text = "Grant Camera and Microphone access to activate real Camera2 manual controls, Live Photo rolling capture, and stabilized video.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate
                )
                Button(
                    onClick = {
                        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = PureBlack),
                    modifier = Modifier.testTag("grant_camera_permission_button")
                ) {
                    Text("Enable Camera Access")
                }
            }
            return@Box
        }

        // Viewfinder container respecting orientation, video mode 16:9, and aspect ratio (4:3, 16:9, 1:1, Full-Screen)
        val effectiveAspect = if (
            mode == CameraMode.VIDEO ||
            mode == CameraMode.SLOW_MOTION ||
            mode == CameraMode.TIMELAPSE
        ) {
            AspectRatioMode.RATIO_16_9
        } else {
            settings.aspectRatio
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isLandscape = maxWidth > maxHeight
            val boxRatio = (maxWidth / maxHeight).coerceAtLeast(0.1f)
            val targetAspect = when (effectiveAspect) {
                AspectRatioMode.RATIO_1_1 -> 1f
                AspectRatioMode.RATIO_4_3 -> if (isLandscape) 4f / 3f else 3f / 4f
                AspectRatioMode.RATIO_16_9 -> if (isLandscape) 16f / 9f else 9f / 16f
                AspectRatioMode.FULL_SCREEN -> null
            }
            val viewfinderMod = when {
                targetAspect == null -> Modifier.fillMaxSize()
                targetAspect > boxRatio -> Modifier.fillMaxWidth().aspectRatio(targetAspect)
                else -> Modifier.fillMaxHeight().aspectRatio(targetAspect)
            }

            Box(
                modifier = viewfinderMod
                    .clip(RoundedCornerShape(if (effectiveAspect == AspectRatioMode.FULL_SCREEN) 0.dp else 12.dp))
                    .pointerInput(activeLens) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (zoomChange != 1f) {
                                viewModel.cameraEngine.setZoomRatio(runtime.activeZoom * zoomChange)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            focusPoint = Offset(nx, ny)
                            viewModel.cameraEngine.triggerTapToFocus(nx, ny)
                            viewModel.performHapticTick()
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            tv.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                                if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                                    viewModel.cameraEngine.refreshTextureTransform()
                                }
                            }
                            viewModel.cameraEngine.attachTextureView(tv, getDisplayRotationDegrees(ctx))
                        }
                    },
                    update = { tv ->
                        viewModel.cameraEngine.updateDisplayRotation(getDisplayRotationDegrees(tv.context))
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (mode == CameraMode.PRO && proParams.focusPeakingEnabled) {
                    FocusPeakingOverlay(data = focusPeaking)
                }
                GridOverlay(gridType = settings.gridType)
                if (settings.electronicLevelEnabled) {
                    ElectronicLevelOverlay(
                        rollDegrees = telemetry.rollDegrees,
                        isAligned = telemetry.isLevelAligned
                    )
                }
                TapFocusReticle(normalizedPos = focusPoint, visible = showFocusReticle)
                SelfTimerCountdownHud(
                    secondsRemaining = countdown,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Top Scrim for high legibility over bright viewfinders
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(170.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                    )
                )
        )

        // Bottom Scrim for shutter & mode dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(250.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                    )
                )
        )

        // Top Bar: Camera Controls & Hardware Status
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Optical Brand & Quick Creative Badges Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(accent, CircleShape))
                    val opticText = activeLens?.let {
                        "HUNG CAM • ${it.focalLengthMm}mm f/${it.aperture}"
                    } ?: "HUNG CAM • OPTICS"
                    Text(
                        text = opticText,
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val latestPhoto = assets.firstOrNull {
                        it.typeEnum != com.example.model.MediaItemType.VIDEO
                    }
                    if (latestPhoto != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.58f))
                                .border(0.75.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .clickable { onOpenStickersMeme(latestPhoto) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("camera_quick_meme_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.EmojiEmotions, contentDescription = "Stickers & Meme", tint = accent, modifier = Modifier.size(13.dp))
                            Text("Meme", style = MonoTelemetrySmall.copy(fontSize = 10.sp), color = accent)
                        }
                    }
                    if (mode == CameraMode.VIDEO) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.58f))
                                .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenVideoMerge)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("camera_quick_merge_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.MergeType, contentDescription = "Merge Videos", tint = PureWhite, modifier = Modifier.size(13.dp))
                            Text("Merge", style = MonoTelemetrySmall.copy(fontSize = 10.sp), color = PureWhite)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(24.dp))
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash
                IconButton(
                    onClick = { viewModel.cycleFlashMode() },
                    enabled = activeLens?.hasFlash == true,
                    modifier = Modifier.testTag("camera_flash_button")
                ) {
                    val flashIcon = when (flashMode) {
                        FlashMode.OFF -> Icons.Default.FlashOff
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.TORCH -> Icons.Default.FlashlightOn
                    }
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "Flash ${flashMode.label}",
                        tint = if (flashMode == FlashMode.OFF) PureWhite else accent
                    )
                }

                // Live Photo Pill (OFF / 2s / 3s / 5s)
                if (mode == CameraMode.PHOTO || mode == CameraMode.LIVE) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (liveDuration != LivePhotoDuration.OFF) accent.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { viewModel.cycleLiveDuration() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("camera_live_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MotionPhotosOn,
                            contentDescription = "Live Photo ${liveDuration.label}",
                            tint = if (liveDuration != LivePhotoDuration.OFF) accent else PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (liveDuration == LivePhotoDuration.OFF) "LIVE OFF" else "LIVE ${liveDuration.label}",
                            style = MonoTelemetrySmall,
                            color = if (liveDuration != LivePhotoDuration.OFF) accent else PureWhite
                        )
                    }
                }

                // Self-Timer
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.cycleSelfTimer() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("camera_timer_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer ${selfTimer.label}",
                        tint = if (selfTimer != SelfTimerDuration.OFF) accent else PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    if (selfTimer != SelfTimerDuration.OFF) {
                        Text(selfTimer.label, style = MonoTelemetrySmall, color = accent)
                    }
                }

                // Aspect Ratio (locked to 16:9 in video modes to match hardware recording stream)
                val isVideoFamilyMode = mode == CameraMode.VIDEO ||
                    mode == CameraMode.SLOW_MOTION ||
                    mode == CameraMode.TIMELAPSE
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !isVideoFamilyMode) { viewModel.cycleAspectRatio() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("camera_aspect_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = if (isVideoFamilyMode) "Aspect 16:9" else "Aspect ${settings.aspectRatio.label}",
                        tint = if (isVideoFamilyMode) MutedSlate else PureWhite,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = if (isVideoFamilyMode) "16:9" else settings.aspectRatio.label,
                        style = MonoTelemetrySmall,
                        color = if (isVideoFamilyMode) MutedSlate else PureWhite
                    )
                }

                // Grid & Level toggles
                IconButton(
                    onClick = { viewModel.cycleGridType() },
                    modifier = Modifier.testTag("camera_grid_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Grid ${settings.gridType.label}",
                        tint = if (settings.gridType != com.example.model.GridType.NONE) accent else PureWhite
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.updateSettings { it.copy(electronicLevelEnabled = !it.electronicLevelEnabled) }
                    },
                    modifier = Modifier.testTag("camera_level_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Straighten,
                        contentDescription = "Electronic Level",
                        tint = if (settings.electronicLevelEnabled) accent else PureWhite
                    )
                }
            }

            // Secondary Telemetry / Video Options / PRO Histogram Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mode == CameraMode.PRO) {
                    LiveHistogramHud(
                        bins = telemetry.luminanceBins,
                        highlightClipPct = telemetry.highlightClipPercent,
                        shadowClipPct = telemetry.shadowClipPercent,
                        showExposureWarning = proParams.exposureWarningEnabled
                    )
                } else if (mode == CameraMode.VIDEO) {
                    VideoQuickSpecStrip(
                        lens = activeLens,
                        currentRes = settings.videoResolution,
                        currentFps = settings.videoFps,
                        stabilization = settings.stabilizationMode,
                        onSelectResFps = { res, fps ->
                            viewModel.updateSettings { it.copy(videoResolution = res, videoFps = fps) }
                        },
                        onCycleStab = {
                            val order = buildList {
                                add(StabilizationMode.OFF)
                                if (activeLens?.supportsOis == true) add(StabilizationMode.OIS)
                                if (activeLens?.supportsEis == true) add(StabilizationMode.EIS)
                                if (activeLens?.supportsOis == true && activeLens?.supportsEis == true) add(StabilizationMode.HYBRID)
                            }
                            val next = order[(order.indexOf(settings.stabilizationMode) + 1) % order.size]
                            viewModel.updateSettings { it.copy(stabilizationMode = next) }
                        }
                    )
                } else if (mode == CameraMode.SLOW_MOTION) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val options = activeLens?.supportedSlowMoModes.orEmpty()
                        if (options.isEmpty()) {
                            Text("High-speed 120/240fps unsupported on this lens", style = MonoTelemetrySmall, color = MutedSlate)
                        } else {
                            options.forEach { opt ->
                                val selected = slowMoOption == opt
                                Text(
                                    text = opt.label,
                                    style = MonoTelemetrySmall,
                                    color = if (selected) PureBlack else PureWhite,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) accent else ElevatedObsidian)
                                        .clickable { viewModel.setSlowMoOption(opt) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                } else if (mode == CameraMode.TIMELAPSE) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimelapseSpeed.entries.forEach { spd ->
                            val selected = timelapseSpeed == spd
                            Text(
                                text = spd.label,
                                style = MonoTelemetrySmall,
                                color = if (selected) PureBlack else PureWhite,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) accent else ElevatedObsidian)
                                    .clickable { viewModel.setTimelapseSpeed(spd) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Recording Timer or Live Buffer / Storage pill
                if (runtime.isRecording) {
                    val secs = (runtime.recordingElapsedMs / 1000L).toInt()
                    val mm = secs / 60
                    val ss = secs % 60
                    Row(
                        modifier = Modifier
                            .background(CrimsonShutter.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(PureWhite, CircleShape))
                        Text(
                            text = String.format("%02d:%02d", mm, ss),
                            style = MonoTelemetryLarge,
                            color = PureWhite
                        )
                        if (runtime.isRecordingPaused) {
                            Text("PAUSED", style = MonoTelemetrySmall, color = PureWhite)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        if (mode == CameraMode.LIVE || (mode == CameraMode.PHOTO && liveDuration != LivePhotoDuration.OFF)) {
                            Text(
                                text = "${telemetry.livePhotoBufferHealth} • ${if (settings.livePhotoSoundEnabled) "SND" else "MUTE"}",
                                style = MonoTelemetrySmall,
                                color = accent,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (runtime.livePhotoFallbackNotice != null) {
                            Text(
                                text = runtime.livePhotoFallbackNotice!!,
                                style = MonoTelemetrySmall.copy(fontSize = 9.sp),
                                color = MutedSlate
                            )
                        }
                    }
                }
            }

            // Status / Error Banner
            AnimatedVisibility(visible = runtime.statusBanner != null || runtime.errorMessage != null) {
                val isErr = runtime.errorMessage != null
                Text(
                    text = runtime.errorMessage ?: runtime.statusBanner.orEmpty(),
                    style = MonoTelemetrySmall,
                    color = PureWhite,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(
                            if (isErr) CrimsonShutter.copy(alpha = 0.85f) else CardSlate.copy(alpha = 0.9f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Controls Dock: Zoom / Lenses / PRO Deck / Mode Strip / Shutter
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mode == CameraMode.PRO) {
                ProControlDeck(
                    lens = activeLens,
                    params = proParams,
                    liveIso = telemetry.activeIso,
                    liveExposureNs = telemetry.activeExposureNs,
                    onUpdate = viewModel::updateProParams
                )
            }

            // Zoom Slider (expandable on tap)
            AnimatedVisibility(visible = showZoomSlider && activeLens != null) {
                activeLens?.let { lens ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(String.format("%.1fx", runtime.activeZoom), style = MonoTelemetrySmall, color = accent)
                        Slider(
                            value = runtime.activeZoom.coerceIn(lens.minZoom, lens.maxZoom.coerceAtMost(10f)),
                            onValueChange = { viewModel.cameraEngine.setZoomRatio(it) },
                            valueRange = lens.minZoom..lens.maxZoom.coerceAtMost(10f).coerceAtLeast(lens.minZoom + 0.1f),
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                            modifier = Modifier.weight(1f).testTag("camera_zoom_slider")
                        )
                    }
                }
            }

            // Physical Lens & Zoom Pills
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(22.dp))
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backLenses = capabilities?.lenses
                    ?.filter { it.lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
                    ?.distinctBy { it.displayLabel }
                    .orEmpty()

                backLenses.forEach { lensOption ->
                    val selected = activeLens?.cameraId == lensOption.cameraId &&
                        activeLens?.physicalId == lensOption.physicalId
                    Text(
                        text = lensOption.displayLabel,
                        style = MonoTelemetrySmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (selected) PureBlack else PureWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) accent else Color.Transparent)
                            .clickable { viewModel.selectLens(lensOption) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("lens_pill_${lensOption.displayLabel}")
                    )
                }

                Text(
                    text = String.format("%.1fx", runtime.activeZoom),
                    style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ElevatedObsidian)
                        .border(0.75.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { showZoomSlider = !showZoomSlider }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("zoom_pill_toggle")
                )
            }

            // Camera Modes Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                CameraMode.entries.forEach { itemMode ->
                    val selected = mode == itemMode
                    Text(
                        text = itemMode.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) accent else PureWhite.copy(alpha = 0.72f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
                            .then(
                                if (selected) Modifier.border(0.75.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                else Modifier
                            )
                            .clickable { viewModel.setCameraMode(itemMode) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("mode_tab_${itemMode.name.lowercase()}")
                    )
                }
            }

            // Shutter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Last captured thumbnail or Pause/Resume during video recording
                if (runtime.isRecording) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { viewModel.cameraEngine.pauseOrResumeVideoRecording() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(ElevatedObsidian, CircleShape)
                                .border(1.dp, HairlineBorder, CircleShape)
                                .testTag("video_pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (runtime.isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pause or Resume Video",
                                tint = PureWhite
                            )
                        }
                        IconButton(
                            onClick = { viewModel.cameraEngine.toggleRecordingAudioMute() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(ElevatedObsidian, CircleShape)
                                .border(1.dp, HairlineBorder, CircleShape)
                                .testTag("video_mute_button")
                        ) {
                            Icon(
                                imageVector = if (runtime.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute Video Mic",
                                tint = if (runtime.isAudioMuted) CrimsonShutter else PureWhite
                            )
                        }
                    }
                } else {
                    val latest = assets.firstOrNull()
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElevatedObsidian)
                            .border(1.5.dp, HairlineBorder, RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenGallery)
                            .testTag("camera_thumbnail_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (latest != null) {
                            AsyncImage(
                                model = latest.mediaStoreUri,
                                contentDescription = "Latest media",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Open Gallery",
                                tint = MutedSlate
                            )
                        }
                    }
                }

                // Center: Tactile Double-Ring Pro Shutter / Record Button
                val isVideoFamily = mode == CameraMode.VIDEO || mode == CameraMode.SLOW_MOTION || mode == CameraMode.TIMELAPSE
                val shutterScale by animateFloatAsState(if (runtime.isCapturing) 0.88f else 1f, label = "shutterScale")
                val outerRingColor = when {
                    runtime.isRecording || isVideoFamily -> CrimsonShutter
                    mode == CameraMode.PRO || mode == CameraMode.LIVE -> accent
                    else -> PureWhite
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(3.dp, outerRingColor, CircleShape)
                        .padding(7.dp)
                        .clip(if (runtime.isRecording) RoundedCornerShape(14.dp) else CircleShape)
                        .background(
                            when {
                                runtime.isRecording -> CrimsonShutter
                                isVideoFamily -> CrimsonShutter
                                mode == CameraMode.LIVE -> accent
                                else -> PureWhite
                            }
                        )
                        .pointerInput(mode, runtime.isRecording) {
                            detectTapGestures(
                                onTap = { viewModel.triggerShutterAction() },
                                onLongPress = {
                                    if (mode == CameraMode.PHOTO || mode == CameraMode.PRO) {
                                        viewModel.startBurstCapture()
                                    }
                                },
                                onPress = {
                                    tryAwaitRelease()
                                    viewModel.stopBurstCapture()
                                }
                            )
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (!runtime.isRecording && !isVideoFamily) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (mode == CameraMode.PRO) PureBlack.copy(alpha = 0.35f) else Color.Transparent)
                        )
                    }
                }

                // Right: Flip Camera (Front/Rear)
                IconButton(
                    onClick = { viewModel.flipFrontBackCamera() },
                    enabled = !runtime.isRecording,
                    modifier = Modifier
                        .size(52.dp)
                        .background(ElevatedObsidian, CircleShape)
                        .border(1.dp, HairlineBorder, CircleShape)
                        .testTag("camera_flip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Front/Back Camera",
                        tint = if (runtime.isRecording) MutedSlate else PureWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoQuickSpecStrip(
    lens: com.example.model.CameraLensInfo?,
    currentRes: com.example.model.VideoResolutionPreset,
    currentFps: Int,
    stabilization: StabilizationMode,
    onSelectResFps: (com.example.model.VideoResolutionPreset, Int) -> Unit,
    onCycleStab: () -> Unit
) {
    val accent = LocalAccentColor.current
    val profiles = lens?.supportedVideoProfiles.orEmpty()
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        profiles.forEach { profile ->
            profile.supportedFps.forEach { fps ->
                val selected = profile.resolution == currentRes && fps == currentFps
                Text(
                    text = "${profile.resolution.label}•${fps}",
                    style = MonoTelemetrySmall,
                    color = if (selected) PureBlack else PureWhite,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) accent else ElevatedObsidian)
                        .clickable { onSelectResFps(profile.resolution, fps) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Text(
            text = "STAB:${stabilization.label}",
            style = MonoTelemetrySmall,
            color = if (stabilization != StabilizationMode.OFF) accent else MutedSlate,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(ElevatedObsidian)
                .clickable(onClick = onCycleStab)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("video_stab_toggle")
        )
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun getDisplayRotationDegrees(context: android.content.Context): Int {
    val hostContext = context.findActivity() ?: context
    val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        runCatching { hostContext.display?.rotation }.getOrNull() ?: android.view.Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (hostContext.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    }
    return when (rotation) {
        android.view.Surface.ROTATION_90 -> 90
        android.view.Surface.ROTATION_180 -> 180
        android.view.Surface.ROTATION_270 -> 270
        else -> 0
    }
}
