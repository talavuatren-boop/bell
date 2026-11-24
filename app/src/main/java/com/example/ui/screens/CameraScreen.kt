package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.camera.HardwareCapabilityDetector
import com.example.data.MediaAssetEntity
import com.example.model.AspectRatioMode
import com.example.model.CameraMode
import com.example.model.FlashMode
import com.example.model.HdrMode
import com.example.model.LivePhotoDuration
import com.example.model.MediaItemType
import com.example.model.ModeColorStyle
import com.example.model.PhotoFilterPreset
import com.example.model.PhotoResolutionTier
import com.example.model.ProCaptureType
import com.example.model.StabilizationMode
import com.example.model.TimelapseSpeed
import com.example.model.VideoBitrateTier
import com.example.processing.ImageProcessor
import com.example.ui.MainViewModel
import com.example.ui.components.CameraFilterBar
import com.example.ui.components.ElectronicLevelOverlay
import com.example.ui.components.FocusPeakingOverlay
import com.example.ui.components.GridOverlay
import com.example.ui.components.HungCamMotion
import com.example.ui.components.LiveHistogramHud
import com.example.ui.components.LivePhotoCaptureProgressPill
import com.example.ui.components.LocalAnimationsEnabled
import com.example.ui.components.ModeQuickControlGrid
import com.example.ui.components.MoreModesFlagshipSheet
import com.example.ui.components.ProControlDeck
import com.example.ui.components.SelfTimerCountdownHud
import com.example.ui.components.ShutterFlashOverlay
import com.example.ui.components.SpringIconButton
import com.example.ui.components.TapFocusReticle
import com.example.ui.components.springPressScale
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonLeica
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalOnAccentColor
import com.example.ui.theme.ModeMoreIndigo
import com.example.ui.theme.MonoTelemetryLarge
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.resolveCameraModeColor
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenCapabilityReport: () -> Unit = {},
    onOpenStickersMeme: (MediaAssetEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val activeLens by viewModel.activeLens.collectAsStateWithLifecycle()
    val mode by viewModel.cameraMode.collectAsStateWithLifecycle()
    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val liveDuration by viewModel.liveDuration.collectAsStateWithLifecycle()
    val selfTimer by viewModel.selfTimer.collectAsStateWithLifecycle()
    val countdown by viewModel.countdownRemaining.collectAsStateWithLifecycle()
    val proParams by viewModel.proParams.collectAsStateWithLifecycle()
    val proCaptureType by viewModel.proCaptureType.collectAsStateWithLifecycle()
    val portraitBokeh by viewModel.portraitBokehStrength.collectAsStateWithLifecycle()
    val runtime by viewModel.cameraRuntime.collectAsStateWithLifecycle()
    val timelapseSpeed by viewModel.timelapseSpeed.collectAsStateWithLifecycle()
    val slowMoOption by viewModel.slowMoOption.collectAsStateWithLifecycle()
    val activeFilter by viewModel.cameraFilter.collectAsStateWithLifecycle()
    val filterIntensity by viewModel.cameraFilterIntensity.collectAsStateWithLifecycle()
    val isMemeMode by viewModel.isMemeCaptureMode.collectAsStateWithLifecycle()
    val assets by viewModel.activeAssets.collectAsStateWithLifecycle()
    val activeModeColor by animateColorAsState(
        targetValue = resolveCameraModeColor(mode, settings.modeColorStyle, accent),
        animationSpec = tween(200),
        label = "activeModeColor"
    )
    val onActiveModeColor = if (activeModeColor == PureWhite) PureBlack else PureWhite

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
        onDispose { viewModel.sensorTelemetry.stop() }
    }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusReticle by remember { mutableStateOf(false) }
    var showQuickGrid by remember { mutableStateOf(false) }
    var showZoomSlider by remember { mutableStateOf(false) }
    var showMoreModesPanel by remember { mutableStateOf(false) }
    var showFilterStrip by remember { mutableStateOf(false) }
    var showPortraitBokehBar by remember { mutableStateOf(true) }
    var showProControlDeck by remember { mutableStateOf(true) }
    var shutterFlashTrigger by remember { mutableStateOf(0) }
    var wasCapturing by remember { mutableStateOf(false) }
    val animEnabled = LocalAnimationsEnabled.current

    BackHandler(enabled = showQuickGrid || showMoreModesPanel || showFilterStrip || showZoomSlider) {
        when {
            showMoreModesPanel -> showMoreModesPanel = false
            showQuickGrid -> showQuickGrid = false
            showFilterStrip -> showFilterStrip = false
            showZoomSlider -> showZoomSlider = false
        }
    }

    LaunchedEffect(runtime.isCapturing) {
        if (runtime.isCapturing && !wasCapturing) {
            shutterFlashTrigger += 1
        }
        wasCapturing = runtime.isCapturing
    }

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

    LaunchedEffect(mode) {
        showQuickGrid = false
        showMoreModesPanel = false
    }

    val isVideoFamilyActive = mode == CameraMode.VIDEO ||
        mode == CameraMode.SLOW_MOTION ||
        mode == CameraMode.TIMELAPSE ||
        (mode == CameraMode.PRO && proCaptureType == ProCaptureType.VIDEO)

    val latestPhoto = remember(assets) {
        assets.firstOrNull { it.typeEnum != MediaItemType.VIDEO }
    }

    if (!hasCameraPerm) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PureBlack)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("HUNG CAM Optical Engine", style = MaterialTheme.typography.headlineMedium, color = PureWhite)
                Text(
                    text = "Cấp quyền Camera & Micro để kích hoạt điều khiển Camera2 chuyên nghiệp, Live Photo và quay Video chống rung.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate
                )
                Button(
                    onClick = {
                        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                    modifier = Modifier.testTag("grant_camera_permission_button")
                ) {
                    Text("Bật quyền Camera")
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Minimal Flagship Top Bar (Left: Flash + Mode Quick Pill | Center: Chevron v | Right: Spec/Rec)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left Quick Actions
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpringIconButton(
                    onClick = { viewModel.cycleFlashMode() },
                    enabled = activeLens?.hasFlash == true,
                    modifier = Modifier.size(40.dp).testTag("camera_flash_button")
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
                        tint = if (flashMode == FlashMode.OFF) PureWhite else accent,
                        modifier = Modifier.size(21.dp)
                    )
                }

                when (mode) {
                    CameraMode.PHOTO -> {
                        val liveInt = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .springPressScale(liveInt, pressedScale = 0.88f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(interactionSource = liveInt, indication = null) {
                                    viewModel.cycleLiveDuration()
                                }
                                .padding(horizontal = 7.dp, vertical = 6.dp)
                                .testTag("camera_live_toggle"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MotionPhotosOn,
                                contentDescription = "Live Photo ${liveDuration.label}",
                                tint = if (liveDuration != LivePhotoDuration.OFF) accent else PureWhite,
                                modifier = Modifier.size(20.dp)
                            )
                            if (liveDuration != LivePhotoDuration.OFF) {
                                Text(
                                    text = liveDuration.label,
                                    style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = accent
                                )
                            }
                        }
                        val hdrActive = settings.hdrMode != HdrMode.OFF
                        Text(
                            text = settings.hdrMode.badgeLabel,
                            style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = if (hdrActive) accent else PureWhite.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (hdrActive) accent.copy(alpha = 0.14f) else ElevatedObsidian)
                                .border(
                                    0.75.dp,
                                    if (hdrActive) accent.copy(alpha = 0.55f) else HairlineBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.cycleHdrMode() }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("camera_viewfinder_hdr_pill")
                        )
                    }

                    CameraMode.VIDEO -> {
                        val combos = remember(activeLens) {
                            activeLens?.supportedVideoProfiles.orEmpty().flatMap { p ->
                                p.supportedFps.map { fps -> p.resolution to fps }
                            }
                        }
                        Text(
                            text = "${settings.videoResolution.label.uppercase()}•${settings.videoFps}",
                            style = MonoTelemetrySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = if (settings.videoFps >= 60 || settings.videoResolution.height >= 2000) accent else PureWhite,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElevatedObsidian)
                                .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = combos.isNotEmpty() && !runtime.isRecording) {
                                    val curIdx = combos.indexOfFirst {
                                        it.first == settings.videoResolution && it.second == settings.videoFps
                                    }
                                    val next = combos[(curIdx + 1) % combos.size]
                                    viewModel.updateSettings {
                                        it.copy(videoResolution = next.first, videoFps = next.second)
                                    }
                                    viewModel.performHapticTick()
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .testTag("camera_top_video_spec")
                        )
                    }

                    CameraMode.PORTRAIT -> {
                        val fAperture = 5.6f - (portraitBokeh * 4.2f)
                        Text(
                            text = String.format("f/%.1f", fAperture),
                            style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold),
                            color = activeModeColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(activeModeColor.copy(alpha = 0.14f))
                                .border(0.75.dp, activeModeColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .clickable { showPortraitBokehBar = !showPortraitBokehBar }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                        val hdrActive = settings.hdrMode != HdrMode.OFF
                        Text(
                            text = settings.hdrMode.badgeLabel,
                            style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = if (hdrActive) accent else PureWhite.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (hdrActive) accent.copy(alpha = 0.14f) else ElevatedObsidian)
                                .border(
                                    0.75.dp,
                                    if (hdrActive) accent.copy(alpha = 0.55f) else HairlineBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.cycleHdrMode() }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("camera_viewfinder_hdr_pill")
                        )
                    }

                    CameraMode.PRO -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = proCaptureType.label,
                                style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = onActiveModeColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(activeModeColor)
                                    .clickable(enabled = !runtime.isRecording) {
                                        val next = if (proCaptureType == ProCaptureType.PHOTO) ProCaptureType.VIDEO else ProCaptureType.PHOTO
                                        viewModel.setProCaptureType(next)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("camera_top_pro_type")
                            )
                            if (proParams.captureRawDng && activeLens?.supportsRaw == true && proCaptureType == ProCaptureType.PHOTO) {
                                Text(
                                    text = "RAW",
                                    style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = activeModeColor
                                )
                            }
                        }
                    }

                    CameraMode.SLOW_MOTION -> {
                        val options = activeLens?.supportedSlowMoModes.orEmpty()
                        Text(
                            text = slowMoOption?.label ?: "Slow-Mo",
                            style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold),
                            color = activeModeColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(activeModeColor.copy(alpha = 0.14f))
                                .border(0.75.dp, activeModeColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .clickable(enabled = options.isNotEmpty() && !runtime.isRecording) {
                                    val idx = options.indexOf(slowMoOption)
                                    viewModel.setSlowMoOption(options[(idx + 1) % options.size])
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }

                    CameraMode.TIMELAPSE -> {
                        val speeds = TimelapseSpeed.entries
                        Text(
                            text = timelapseSpeed.label,
                            style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold),
                            color = activeModeColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(activeModeColor.copy(alpha = 0.14f))
                                .border(0.75.dp, activeModeColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .clickable(enabled = !runtime.isRecording) {
                                    val idx = speeds.indexOf(timelapseSpeed)
                                    viewModel.setTimelapseSpeed(speeds[(idx + 1) % speeds.size])
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Center Dark Circular Chevron Pill (v / ^) with smooth 180deg rotation
            val chevronInteraction = remember { MutableInteractionSource() }
            val chevronRotation by animateFloatAsState(
                targetValue = if (showQuickGrid) 180f else 0f,
                animationSpec = if (animEnabled) HungCamMotion.fastSpringFloat else tween(0),
                label = "chevronRotation"
            )
            val chevronBorderColor by animateColorAsState(
                targetValue = if (showQuickGrid) accent.copy(alpha = 0.55f) else HairlineBorder,
                animationSpec = tween(160),
                label = "chevronBorder"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(42.dp)
                    .height(26.dp)
                    .springPressScale(chevronInteraction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1C22))
                    .border(
                        width = 0.6.dp,
                        color = chevronBorderColor,
                        shape = CircleShape
                    )
                    .clickable(interactionSource = chevronInteraction, indication = null) {
                        viewModel.performHapticTick()
                        showMoreModesPanel = false
                        showQuickGrid = !showQuickGrid
                    }
                    .testTag("camera_top_chevron_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Mở tùy chỉnh chế độ",
                    tint = if (showQuickGrid) accent else PureWhite,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }

            // Right Minimal Telemetry + Top-Right Settings Gear: Recording Clock OR Active Filter/Focal + Gear
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (runtime.isRecording) {
                    val secs = (runtime.recordingElapsedMs / 1000L).toInt()
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CrimsonShutter)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(PureWhite, CircleShape))
                        Text(
                            text = String.format("%02d:%02d", secs / 60, secs % 60),
                            style = MonoTelemetryLarge.copy(fontSize = 12.sp),
                            color = PureWhite
                        )
                    }
                } else {
                    if (selfTimer != com.example.model.SelfTimerDuration.OFF) {
                        Text(
                            text = selfTimer.label,
                            style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = accent
                        )
                    }
                    val opticLabel = activeLens?.let { lens ->
                        val mm = (lens.equivalentFocalLengthMm * runtime.activeZoom).toInt().coerceAtLeast(10)
                        "${mm}mm"
                    } ?: "LEICA"
                    Text(
                        text = if (activeFilter != PhotoFilterPreset.NATURAL) activeFilter.label else opticLabel,
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = if (activeFilter != PhotoFilterPreset.NATURAL) accent else MutedSlate,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.performHapticTick()
                                if (activeFilter != PhotoFilterPreset.NATURAL) {
                                    showQuickGrid = false
                                    showFilterStrip = !showFilterStrip
                                } else {
                                    showFilterStrip = false
                                    showQuickGrid = !showQuickGrid
                                }
                            }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    )
                    SpringIconButton(
                        onClick = {
                            viewModel.performHapticTick()
                            onOpenSettings()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF181A20))
                            .border(0.6.dp, HairlineBorder, CircleShape)
                            .testTag("camera_top_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Cài đặt Camera",
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Main Aspect-Locked Viewfinder Workspace + Translucent Quick Grid + Floating Zoom/Filter Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val effectiveAspect = if (isVideoFamilyActive) AspectRatioMode.RATIO_16_9 else settings.aspectRatio

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
                    AspectRatioMode.FULL_SCREEN -> boxRatio
                }
                val animatedAspect by animateFloatAsState(
                    targetValue = targetAspect,
                    animationSpec = if (animEnabled) HungCamMotion.smoothAspectSpring else tween(0),
                    label = "viewfinderAspect"
                )
                val viewfinderMod = if (effectiveAspect == AspectRatioMode.FULL_SCREEN) {
                    Modifier.fillMaxSize()
                } else if (animatedAspect > boxRatio) {
                    Modifier.fillMaxWidth().aspectRatio(animatedAspect)
                } else {
                    Modifier.fillMaxHeight().aspectRatio(animatedAspect)
                }

                Box(
                    modifier = viewfinderMod
                        .clip(RoundedCornerShape(if (effectiveAspect == AspectRatioMode.FULL_SCREEN) 0.dp else 4.dp))
                        .pointerInput(activeLens) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                if (zoomChange != 1f) {
                                    viewModel.cameraEngine.setZoomRatio(runtime.activeZoom * zoomChange)
                                }
                            }
                        }
                        .pointerInput(mode, showQuickGrid) {
                            detectTapGestures { offset ->
                                if (showQuickGrid) {
                                    showQuickGrid = false
                                    return@detectTapGestures
                                }
                                val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                focusPoint = Offset(nx, ny)
                                viewModel.cameraEngine.triggerTapToFocus(nx, ny)
                                if (mode == CameraMode.PORTRAIT) {
                                    viewModel.updatePortraitFocusPoint(nx, ny)
                                }
                                viewModel.performHapticTick()
                            }
                        }
                ) {
                    val filterKey = activeFilter to filterIntensity
                    val liveFilterPaint = remember(filterKey) {
                        if (activeFilter == PhotoFilterPreset.NATURAL) {
                            null
                        } else {
                            android.graphics.Paint().apply {
                                colorFilter = android.graphics.ColorMatrixColorFilter(
                                    ImageProcessor.buildFilterColorMatrix(activeFilter, filterIntensity)
                                )
                            }
                        }
                    }
                    // Cache last applied filter & rotation so recompositions never force 60fps-killing TextureView invalidates
                    val lastAppliedState = remember { arrayOfNulls<Any>(2) }

                    AndroidView(
                        factory = { ctx ->
                            val rot = getDisplayRotationDegrees(ctx)
                            TextureView(ctx).also { tv ->
                                val layerType = if (liveFilterPaint == null) {
                                    android.view.View.LAYER_TYPE_NONE
                                } else {
                                    android.view.View.LAYER_TYPE_HARDWARE
                                }
                                tv.setLayerType(layerType, liveFilterPaint)
                                tv.setLayerPaint(liveFilterPaint)
                                lastAppliedState[0] = filterKey
                                lastAppliedState[1] = rot
                                tv.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                                    if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                                        viewModel.cameraEngine.refreshTextureTransform()
                                    }
                                }
                                viewModel.cameraEngine.attachTextureView(tv, rot)
                            }
                        },
                        update = { tv ->
                            if (lastAppliedState[0] != filterKey) {
                                val layerType = if (liveFilterPaint == null) {
                                    android.view.View.LAYER_TYPE_NONE
                                } else {
                                    android.view.View.LAYER_TYPE_HARDWARE
                                }
                                tv.setLayerType(layerType, liveFilterPaint)
                                tv.setLayerPaint(liveFilterPaint)
                                tv.invalidate()
                                lastAppliedState[0] = filterKey
                            }
                            val rot = getDisplayRotationDegrees(tv.context)
                            if (lastAppliedState[1] != rot) {
                                viewModel.cameraEngine.updateDisplayRotation(rot)
                                lastAppliedState[1] = rot
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (mode == CameraMode.PRO && proParams.focusPeakingEnabled) {
                        IsolatedFocusPeakingOverlay(viewModel = viewModel)
                    }
                    GridOverlay(gridType = settings.gridType)
                    if (settings.electronicLevelEnabled) {
                        IsolatedElectronicLevelOverlay(viewModel = viewModel)
                    }
                    TapFocusReticle(normalizedPos = focusPoint, visible = showFocusReticle)
                    SelfTimerCountdownHud(
                        secondsRemaining = countdown,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    ShutterFlashOverlay(triggerCount = shutterFlashTrigger)
                }
            }

            // Live Histogram HUD top-left inside viewfinder in PRO mode (isolated recomposition scope)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                OverlayAnimatedVisibility(
                    visible = mode == CameraMode.PRO,
                    enter = if (animEnabled) fadeIn(tween(220, easing = HungCamMotion.EmphasizedDecelerate)) + scaleIn(initialScale = 0.92f) else fadeIn(tween(0)),
                    exit = if (animEnabled) fadeOut(tween(180, easing = HungCamMotion.EmphasizedDecelerate)) + scaleOut(targetScale = 0.92f) else fadeOut(tween(0))
                ) {
                    IsolatedLiveHistogramHud(
                        viewModel = viewModel,
                        showExposureWarning = proParams.exposureWarningEnabled
                    )
                }
            }

            // Live Photo 2s/3s/5s/10s Post-Shutter Capture Progress Pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                OverlayAnimatedVisibility(
                    visible = runtime.livePhotoCaptureProgress != null && runtime.livePhotoDurationSec > 0,
                    enter = if (animEnabled) fadeIn(tween(160)) + slideInVertically { -it / 2 } else fadeIn(tween(0)),
                    exit = if (animEnabled) fadeOut(tween(150)) + slideOutVertically { -it / 2 } else fadeOut(tween(0))
                ) {
                    LivePhotoCaptureProgressPill(
                        progress = runtime.livePhotoCaptureProgress ?: 0f,
                        durationSec = runtime.livePhotoDurationSec.coerceAtLeast(2)
                    )
                }
            }

            // Status / Error Toast Banner
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                OverlayAnimatedVisibility(
                    visible = (runtime.statusBanner != null || runtime.errorMessage != null) &&
                        runtime.livePhotoCaptureProgress == null,
                    enter = if (animEnabled) fadeIn(tween(180)) + slideInVertically { -it / 2 } else fadeIn(tween(0)),
                    exit = if (animEnabled) fadeOut(tween(150)) + slideOutVertically { -it / 2 } else fadeOut(tween(0))
                ) {
                    val isErr = runtime.errorMessage != null
                    Text(
                        text = runtime.errorMessage ?: runtime.statusBanner.orEmpty(),
                        style = MonoTelemetrySmall,
                        color = PureWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isErr) CrimsonShutter.copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.72f))
                            .border(0.6.dp, HairlineBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Clean viewfinder: HDR control is neatly placed in the Top Bar without obstructing preview corners

            // Active Meme Mode Prompt Pill (Shoot -> Auto Open Sticker & Meme Editor)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 42.dp)
            ) {
                OverlayAnimatedVisibility(
                    visible = isMemeMode && !runtime.isRecording,
                    enter = if (animEnabled) fadeIn(tween(180)) + slideInVertically { -it / 2 } else fadeIn(tween(0)),
                    exit = if (animEnabled) fadeOut(tween(140)) + slideOutVertically { -it / 2 } else fadeOut(tween(0))
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xD9101216))
                            .border(1.dp, accent, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("camera_meme_mode_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MEME • Chụp để ghép Sticker & Chữ",
                            style = MonoTelemetrySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                        if (latestPhoto != null) {
                            Text(
                                text = "Ảnh cũ",
                                style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.16f))
                                    .clickable {
                                        viewModel.setMemeCaptureMode(false)
                                        onOpenStickersMeme(latestPhoto)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Thoát chế độ Meme",
                            tint = MutedSlate,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.setMemeCaptureMode(false) }
                                .testTag("camera_meme_mode_close")
                        )
                    }
                }
            }

            // Expandable Translucent 4-Column Quick Grid overlaid cleanly over lower viewfinder (Screenshot 1)
            ModeQuickControlGrid(
                visible = showQuickGrid,
                mode = mode,
                settings = settings,
                activeLens = activeLens,
                liveDuration = liveDuration,
                selfTimer = selfTimer,
                proParams = proParams,
                proCaptureType = proCaptureType,
                activeFilter = activeFilter,
                isAudioMuted = runtime.isAudioMuted,
                timelapseSpeed = timelapseSpeed,
                slowMoOption = slowMoOption,
                isMemeMode = isMemeMode,
                onCycleHdr = { viewModel.cycleHdrMode() },
                onToggleCameraAi = { viewModel.toggleCameraAiSuperSharp() },
                onToggleMemeMode = { viewModel.setMemeCaptureMode(!isMemeMode) },
                onCycleLiveDuration = { viewModel.cycleLiveDuration() },
                onToggleNaturalBoost = {
                    viewModel.updateSettings { it.copy(naturalColorBoostEnabled = !it.naturalColorBoostEnabled) }
                    viewModel.performHapticTick()
                },
                onCycleAspectRatio = {
                    if (!isVideoFamilyActive) viewModel.cycleAspectRatio()
                },
                onCycleSelfTimer = { viewModel.cycleSelfTimer() },
                onCycleGrid = { viewModel.cycleGridType() },
                onToggleLevel = {
                    viewModel.updateSettings { it.copy(electronicLevelEnabled = !it.electronicLevelEnabled) }
                    viewModel.performHapticTick()
                },
                onToggleLiveSound = {
                    viewModel.updateSettings { it.copy(livePhotoSoundEnabled = !it.livePhotoSoundEnabled) }
                    viewModel.performHapticTick()
                },
                onTogglePhotoResolution = {
                    viewModel.updateSettings {
                        val next = if (it.photoResolutionTier == PhotoResolutionTier.MAX) {
                            PhotoResolutionTier.STANDARD
                        } else {
                            PhotoResolutionTier.MAX
                        }
                        it.copy(photoResolutionTier = next)
                    }
                    viewModel.performHapticTick()
                },
                onToggleWatermark = {
                    viewModel.updateSettings { it.copy(cameraWatermarkEnabled = !it.cameraWatermarkEnabled) }
                    viewModel.performHapticTick()
                },
                onCycleVideoRes = {
                    val profiles = activeLens?.supportedVideoProfiles.orEmpty()
                    if (profiles.isNotEmpty() && !runtime.isRecording) {
                        val curIdx = profiles.indexOfFirst { it.resolution == settings.videoResolution }
                        val nextProfile = profiles[(curIdx + 1) % profiles.size]
                        val fps = if (settings.videoFps in nextProfile.supportedFps) settings.videoFps else nextProfile.supportedFps.first()
                        viewModel.updateSettings { it.copy(videoResolution = nextProfile.resolution, videoFps = fps) }
                        viewModel.performHapticTick()
                    }
                },
                onCycleVideoFps = {
                    val profile = activeLens?.supportedVideoProfiles.orEmpty()
                        .firstOrNull { it.resolution == settings.videoResolution }
                    val fpsList = profile?.supportedFps.orEmpty()
                    if (fpsList.isNotEmpty() && !runtime.isRecording) {
                        val idx = fpsList.indexOf(settings.videoFps)
                        val nextFps = fpsList[(idx + 1) % fpsList.size]
                        viewModel.updateSettings { it.copy(videoFps = nextFps) }
                        viewModel.performHapticTick()
                    }
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
                    viewModel.performHapticTick()
                },
                onCycleVideoBitrate = {
                    val tiers = VideoBitrateTier.entries
                    val next = tiers[(tiers.indexOf(settings.videoBitrateTier) + 1) % tiers.size]
                    viewModel.updateSettings { it.copy(videoBitrateTier = next) }
                    viewModel.performHapticTick()
                },
                onToggleMicMute = { viewModel.toggleRecordingMicMute() },
                onCycleProCaptureType = {
                    val next = if (proCaptureType == ProCaptureType.PHOTO) ProCaptureType.VIDEO else ProCaptureType.PHOTO
                    viewModel.setProCaptureType(next)
                },
                onToggleProRaw = {
                    viewModel.updateProParams { it.copy(captureRawDng = !it.captureRawDng) }
                    viewModel.performHapticTick()
                },
                onToggleFocusPeaking = {
                    viewModel.updateProParams { it.copy(focusPeakingEnabled = !it.focusPeakingEnabled) }
                    viewModel.performHapticTick()
                },
                onToggleExposureWarning = {
                    viewModel.updateProParams { it.copy(exposureWarningEnabled = !it.exposureWarningEnabled) }
                    viewModel.performHapticTick()
                },
                onCycleSlowMo = {
                    val options = activeLens?.supportedSlowMoModes.orEmpty()
                    if (options.isNotEmpty()) {
                        val idx = options.indexOf(slowMoOption)
                        viewModel.setSlowMoOption(options[(idx + 1) % options.size])
                    }
                },
                onCycleTimelapseSpeed = {
                    val speeds = TimelapseSpeed.entries
                    val idx = speeds.indexOf(timelapseSpeed)
                    viewModel.setTimelapseSpeed(speeds[(idx + 1) % speeds.size])
                },
                onOpenFilters = { showFilterStrip = true },
                onDismiss = { showQuickGrid = false },
                modeAccentColor = activeModeColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )

            // Floating Bottom-of-Viewfinder Action Row: Left Quick Circle | Center Zoom Pill | Right Filter Wand
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                OverlayAnimatedVisibility(
                    visible = !showQuickGrid,
                    enter = if (animEnabled) fadeIn(tween(180)) + slideInVertically { it / 4 } else fadeIn(tween(0)),
                    exit = if (animEnabled) fadeOut(tween(130)) + slideOutVertically { it / 4 } else fadeOut(tween(0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Circular Quick Action (Mode-Specific)
                    val leftInt = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .springPressScale(leftInt, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.56f))
                            .border(0.75.dp, HairlineBorder.copy(alpha = 0.8f), CircleShape)
                            .clickable(interactionSource = leftInt, indication = null) {
                                viewModel.performHapticTick()
                                when (mode) {
                                    CameraMode.PRO -> showProControlDeck = !showProControlDeck
                                    CameraMode.PORTRAIT -> showPortraitBokehBar = !showPortraitBokehBar
                                    else -> viewModel.updateSettings {
                                        it.copy(naturalColorBoostEnabled = !it.naturalColorBoostEnabled)
                                    }
                                }
                            }
                            .testTag("camera_left_floating_action"),
                        contentAlignment = Alignment.Center
                    ) {
                        when (mode) {
                            CameraMode.PRO -> Icon(
                                Icons.Default.Tune,
                                contentDescription = "Bảng chỉnh Pro",
                                tint = if (showProControlDeck) accent else PureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            CameraMode.PORTRAIT -> Icon(
                                Icons.Default.BlurOn,
                                contentDescription = "Khẩu độ Bokeh",
                                tint = if (showPortraitBokehBar) accent else PureWhite,
                                modifier = Modifier.size(19.dp)
                            )
                            else -> Text(
                                text = if (settings.naturalColorBoostEnabled) "N+" else "RAW",
                                style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = if (settings.naturalColorBoostEnabled) accent else PureWhite.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Center Zoom Pill Bar (0.6 | 1x | 2 | 5)
                    val zoomStops = remember(capabilities?.lenses, activeLens) {
                        HardwareCapabilityDetector.buildZoomStopPresets(
                            allLenses = capabilities?.lenses.orEmpty(),
                            activeLens = activeLens
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.62f))
                            .border(0.75.dp, HairlineBorder.copy(alpha = 0.7f), CircleShape)
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        zoomStops.forEach { stop ->
                            val sameBacking = activeLens?.cameraId == stop.backingLens.cameraId &&
                                activeLens?.physicalId == stop.backingLens.physicalId
                            val isSelected = sameBacking &&
                                kotlin.math.abs(runtime.activeZoom - stop.targetZoomRatio) < 0.22f
                            val pillScale by animateFloatAsState(
                                targetValue = if (isSelected && animEnabled) 1.06f else 1f,
                                animationSpec = if (animEnabled) HungCamMotion.bouncySpringFloat else tween(0),
                                label = "zoomPillScale"
                            )
                            val pillBg by animateColorAsState(
                                targetValue = if (isSelected) Color(0xCC2C2E36) else Color.Transparent,
                                animationSpec = tween(160),
                                label = "zoomPillBg"
                            )
                            val pillTextColor by animateColorAsState(
                                targetValue = if (isSelected) accent else PureWhite.copy(alpha = 0.85f),
                                animationSpec = tween(150),
                                label = "zoomPillTextColor"
                            )
                            val stopInt = remember(stop.label) { MutableInteractionSource() }
                            val compactLabel = if (isSelected) {
                                String.format("%.1fx", runtime.activeZoom).replace(".0x", "x")
                            } else {
                                stop.label.removeSuffix("x")
                            }
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .springPressScale(stopInt, pressedScale = 0.86f, extraScale = pillScale)
                                    .clip(CircleShape)
                                    .background(pillBg)
                                    .then(
                                        if (isSelected) Modifier.border(1.dp, PureWhite.copy(alpha = 0.45f), CircleShape)
                                        else Modifier
                                    )
                                    .clickable(interactionSource = stopInt, indication = null) {
                                        if (isSelected) {
                                            viewModel.performHapticTick()
                                            showZoomSlider = !showZoomSlider
                                        } else {
                                            viewModel.selectZoomStop(stop)
                                        }
                                    }
                                    .padding(horizontal = if (isSelected) 11.dp else 9.dp)
                                    .testTag("lens_pill_${stop.label}"),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = compactLabel,
                                    transitionSpec = {
                                        if (animEnabled) {
                                            (fadeIn(tween(120)) + scaleIn(initialScale = 0.9f)) togetherWith fadeOut(tween(100))
                                        } else {
                                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                        }
                                    },
                                    label = "zoomLabelFlip"
                                ) { lbl ->
                                    Text(
                                        text = lbl,
                                        style = MonoTelemetrySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = pillTextColor
                                    )
                                }
                            }
                        }
                    }

                    // Right Circular Leica & Filter Wand Toggle (icon highlight only, clean dark surface)
                    val filterInt = remember { MutableInteractionSource() }
                    val filterActive = activeFilter != PhotoFilterPreset.NATURAL || showFilterStrip
                    val filterIconTint by animateColorAsState(
                        targetValue = if (filterActive) accent else PureWhite,
                        animationSpec = tween(180),
                        label = "filterBtnTint"
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .springPressScale(filterInt, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.56f))
                            .border(
                                width = if (filterActive) 1.dp else 0.75.dp,
                                color = if (filterActive) accent else HairlineBorder.copy(alpha = 0.8f),
                                shape = CircleShape
                            )
                            .clickable(interactionSource = filterInt, indication = null) {
                                viewModel.performHapticTick()
                                showQuickGrid = false
                                showMoreModesPanel = false
                                showFilterStrip = !showFilterStrip
                            }
                            .testTag("camera_filter_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterVintage,
                            contentDescription = "Bộ lọc Leica & Máy ảnh",
                            tint = filterIconTint,
                            modifier = Modifier.size(19.dp)
                        )
                        if (activeFilter != PhotoFilterPreset.NATURAL) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonLeica)
                            )
                        }
                    }
                }
            }
            }
        }

        // 3. Collapsible Precision Sliders / Pro Deck / Filter Carousel directly above Bottom Dock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureBlack)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showZoomSlider && activeLens != null,
                enter = HungCamMotion.panelEnter(animEnabled) + expandVertically(),
                exit = HungCamMotion.panelExit(animEnabled) + shrinkVertically()
            ) {
                activeLens?.let { lens ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ElevatedObsidian)
                            .border(0.6.dp, HairlineBorder, RoundedCornerShape(18.dp))
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

            AnimatedVisibility(
                visible = mode == CameraMode.PRO && showProControlDeck,
                enter = HungCamMotion.panelEnter(animEnabled) + expandVertically(),
                exit = HungCamMotion.panelExit(animEnabled) + shrinkVertically()
            ) {
                IsolatedProControlDeck(
                    viewModel = viewModel,
                    lens = activeLens,
                    params = proParams,
                    captureType = proCaptureType,
                    onSelectCaptureType = viewModel::setProCaptureType,
                    onUpdate = viewModel::updateProParams
                )
            }

            AnimatedVisibility(
                visible = mode == CameraMode.PORTRAIT && showPortraitBokehBar,
                enter = HungCamMotion.panelEnter(animEnabled) + expandVertically(),
                exit = HungCamMotion.panelExit(animEnabled) + shrinkVertically()
            ) {
                val fAperture = 5.6f - (portraitBokeh * 4.2f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ElevatedObsidian)
                        .border(0.6.dp, HairlineBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = String.format("f/%.1f", fAperture),
                        style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                    Slider(
                        value = portraitBokeh,
                        onValueChange = { viewModel.setPortraitBokehStrength(it) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier.weight(1f).testTag("portrait_bokeh_slider")
                    )
                    Text(
                        text = "Xóa phông ${(portraitBokeh * 100).toInt()}%",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                        color = PureWhite
                    )
                }
            }

            CameraFilterBar(
                visible = showFilterStrip,
                activeFilter = activeFilter,
                filterIntensity = filterIntensity,
                naturalBoostEnabled = settings.naturalColorBoostEnabled,
                isRecording = runtime.isRecording,
                onSelectFilter = { viewModel.setCameraFilter(it) },
                onChangeIntensity = { viewModel.setCameraFilterIntensity(it) },
                onToggleNaturalBoost = {
                    viewModel.updateSettings { it.copy(naturalColorBoostEnabled = !it.naturalColorBoostEnabled) }
                    viewModel.performHapticTick()
                }
            )
        }

        // 4. Flagship Bottom Mode Strip + Swipe-up Handle + Concentric Shutter Dock (Screenshot 3)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureBlack)
                .pointerInput(runtime.isRecording) {
                    var dragSum = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragSum = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            dragSum += dragAmount
                            if (dragSum < -48f && !runtime.isRecording) {
                                showMoreModesPanel = true
                            } else if (dragSum > 48f) {
                                showMoreModesPanel = false
                            }
                        }
                    )
                }
                .padding(top = 6.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Subtle Swipe-Up Pill Indicator
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.5.dp)
                    .clip(CircleShape)
                    .background(MutedSlate.copy(alpha = 0.45f))
                    .clickable(enabled = !runtime.isRecording) {
                        showMoreModesPanel = !showMoreModesPanel
                    }
            )

            // Centered Flagship Colored Pill Mode Strip: CHUYÊN NGHIỆP | VIDEO | ẢNH | CHÂN DUNG | THÊM
            val isExtraModeActive = mode == CameraMode.SLOW_MOTION || mode == CameraMode.TIMELAPSE
            val isMulticolorMode = settings.modeColorStyle == ModeColorStyle.PER_MODE_VIBRANT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barModes = listOf(
                    CameraMode.PRO,
                    CameraMode.VIDEO,
                    CameraMode.PHOTO,
                    CameraMode.PORTRAIT
                )
                barModes.forEach { itemMode ->
                    val selected = mode == itemMode
                    val itemColor = resolveCameraModeColor(itemMode, settings.modeColorStyle, accent)
                    val isLightChip = itemColor == PureWhite
                    val modeInteraction = remember(itemMode) { MutableInteractionSource() }
                    val pillBgColor by animateColorAsState(
                        targetValue = when {
                            selected && isLightChip -> PureWhite
                            selected -> itemColor.copy(alpha = 0.24f)
                            isMulticolorMode -> itemColor.copy(alpha = 0.08f)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(180),
                        label = "modePillBg_${itemMode.name}"
                    )
                    val pillBorderColor by animateColorAsState(
                        targetValue = when {
                            selected -> itemColor.copy(alpha = 0.88f)
                            isMulticolorMode -> itemColor.copy(alpha = 0.30f)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(180),
                        label = "modePillBorder_${itemMode.name}"
                    )
                    val modeTextColor by animateColorAsState(
                        targetValue = when {
                            selected && isLightChip -> PureBlack
                            selected -> PureWhite
                            isMulticolorMode -> PureWhite.copy(alpha = 0.86f)
                            else -> PureWhite.copy(alpha = 0.65f)
                        },
                        animationSpec = tween(160),
                        label = "modeTextColor_${itemMode.name}"
                    )
                    val dotSize by animateDpAsState(
                        targetValue = when {
                            selected && !isLightChip -> 6.dp
                            isMulticolorMode -> 5.dp
                            else -> 0.dp
                        },
                        animationSpec = if (animEnabled) spring(dampingRatio = 0.78f, stiffness = 480f) else tween(0),
                        label = "modeDotSize_${itemMode.name}"
                    )
                    val labelText = when (itemMode) {
                        CameraMode.PRO -> "Chuyên nghiệp"
                        CameraMode.VIDEO -> "Video"
                        CameraMode.PHOTO -> "Ảnh"
                        CameraMode.PORTRAIT -> "Chân dung"
                        else -> itemMode.flagshipLabel()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (dotSize > 0.dp) 6.dp else 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .springPressScale(modeInteraction, pressedScale = 0.90f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(pillBgColor)
                            .border(
                                width = if (selected) 1.dp else 0.75.dp,
                                color = pillBorderColor,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable(interactionSource = modeInteraction, indication = null) {
                                showMoreModesPanel = false
                                viewModel.setCameraMode(itemMode)
                            }
                            .padding(horizontal = if (selected || isMulticolorMode) 12.dp else 10.dp)
                            .testTag("mode_tab_${itemMode.name.lowercase()}")
                    ) {
                        if (dotSize > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(itemColor)
                            )
                        }
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = modeTextColor,
                            maxLines = 1
                        )
                    }
                }

                // "Thêm" Mode Drawer Trigger
                val moreInteraction = remember { MutableInteractionSource() }
                val moreActive = isExtraModeActive || showMoreModesPanel
                val moreColor = when {
                    isExtraModeActive -> resolveCameraModeColor(mode, settings.modeColorStyle, accent)
                    isMulticolorMode -> ModeMoreIndigo
                    settings.modeColorStyle == ModeColorStyle.MONOCHROME_WHITE -> PureWhite
                    else -> accent
                }
                val isLightMore = moreColor == PureWhite
                val morePillBg by animateColorAsState(
                    targetValue = when {
                        moreActive && isLightMore -> PureWhite
                        moreActive -> moreColor.copy(alpha = 0.24f)
                        isMulticolorMode -> moreColor.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "morePillBg"
                )
                val morePillBorder by animateColorAsState(
                    targetValue = when {
                        moreActive -> moreColor.copy(alpha = 0.88f)
                        isMulticolorMode -> moreColor.copy(alpha = 0.30f)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "morePillBorder"
                )
                val moreTextColor by animateColorAsState(
                    targetValue = when {
                        moreActive && isLightMore -> PureBlack
                        moreActive -> PureWhite
                        isMulticolorMode -> PureWhite.copy(alpha = 0.86f)
                        else -> PureWhite.copy(alpha = 0.65f)
                    },
                    animationSpec = tween(160),
                    label = "moreModeTextColor"
                )
                val moreDotSize by animateDpAsState(
                    targetValue = when {
                        moreActive && !isLightMore -> 6.dp
                        isMulticolorMode -> 5.dp
                        else -> 0.dp
                    },
                    animationSpec = if (animEnabled) spring(dampingRatio = 0.78f, stiffness = 480f) else tween(0),
                    label = "moreModeDotSize"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (moreDotSize > 0.dp) 6.dp else 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .springPressScale(moreInteraction, pressedScale = 0.90f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(morePillBg)
                        .border(
                            width = if (moreActive) 1.dp else 0.75.dp,
                            color = morePillBorder,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable(interactionSource = moreInteraction, indication = null, enabled = !runtime.isRecording) {
                            showMoreModesPanel = !showMoreModesPanel
                        }
                        .padding(horizontal = if (moreActive || isMulticolorMode) 12.dp else 10.dp)
                        .testTag("mode_tab_more")
                ) {
                    if (moreDotSize > 0.dp) {
                        Box(
                            modifier = Modifier
                                .size(moreDotSize)
                                .clip(CircleShape)
                                .background(moreColor)
                        )
                    }
                    Text(
                        text = if (isExtraModeActive) mode.flagshipLabel() else "Thêm",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = if (moreActive) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = moreTextColor,
                        maxLines = 1
                    )
                }
            }

            // Concentric Flagship Shutter Row (Gallery Thumbnail | Concentric Shutter | Flip Camera)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (runtime.isRecording) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpringIconButton(
                            onClick = { viewModel.cameraEngine.pauseOrResumeVideoRecording() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(ElevatedObsidian, CircleShape)
                                .border(0.75.dp, HairlineBorder, CircleShape)
                                .testTag("video_pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (runtime.isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Tạm dừng / Tiếp tục quay",
                                tint = PureWhite
                            )
                        }
                        SpringIconButton(
                            onClick = { viewModel.toggleRecordingMicMute() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(ElevatedObsidian, CircleShape)
                                .border(0.75.dp, HairlineBorder, CircleShape)
                                .testTag("video_mute_button")
                        ) {
                            Icon(
                                imageVector = if (runtime.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Tắt / Mở Mic",
                                tint = if (runtime.isAudioMuted) CrimsonShutter else PureWhite
                            )
                        }
                    }
                } else {
                    val latest = assets.firstOrNull()
                    val thumbInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .springPressScale(thumbInteraction, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .background(ElevatedObsidian)
                            .border(1.5.dp, PureWhite.copy(alpha = 0.25f), CircleShape)
                            .clickable(interactionSource = thumbInteraction, indication = null, onClick = onOpenGallery)
                            .testTag("camera_thumbnail_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (latest != null) {
                            val thumbRequest = remember(latest.mediaStoreUri, context) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(latest.mediaStoreUri)
                                    .size(160)
                                    .precision(coil.size.Precision.INEXACT)
                                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                                    .build()
                            }
                            AsyncImage(
                                model = thumbRequest,
                                contentDescription = "Mở Thư viện",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Mở Thư viện",
                                tint = PureWhite.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Center Double-Ring Flagship Shutter Button with 60fps shape morph + Live Photo progress arc
                val shutterScale by animateFloatAsState(
                    targetValue = if (runtime.isCapturing && animEnabled) 0.85f else 1f,
                    animationSpec = if (animEnabled) HungCamMotion.bouncySpringFloat else tween(0),
                    label = "shutterScale"
                )
                val innerCornerRadius by animateDpAsState(
                    targetValue = if (runtime.isRecording) 10.dp else 32.dp,
                    animationSpec = if (animEnabled) spring(dampingRatio = 0.82f, stiffness = 480f) else tween(0),
                    label = "shutterInnerCorner"
                )
                val innerInsetPad by animateDpAsState(
                    targetValue = if (runtime.isRecording) 20.dp else 6.dp,
                    animationSpec = if (animEnabled) spring(dampingRatio = 0.82f, stiffness = 480f) else tween(0),
                    label = "shutterInnerPad"
                )
                val shutterOuterColor by animateColorAsState(
                    targetValue = when {
                        runtime.isRecording -> CrimsonShutter
                        runtime.livePhotoCaptureProgress != null -> accent
                        else -> PureWhite
                    },
                    animationSpec = tween(180),
                    label = "shutterOuterColor"
                )
                val shutterCoreColor by animateColorAsState(
                    targetValue = if (runtime.isRecording || isVideoFamilyActive) CrimsonShutter else PureWhite,
                    animationSpec = tween(180),
                    label = "shutterCoreColor"
                )
                val liveArcSweep by animateFloatAsState(
                    targetValue = (runtime.livePhotoCaptureProgress ?: 0f) * 360f,
                    animationSpec = tween(60, easing = FastOutSlowInEasing),
                    label = "shutterLiveSweep"
                )
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .graphicsLayer {
                            scaleX = shutterScale
                            scaleY = shutterScale
                        }
                        .pointerInput(mode, proCaptureType, runtime.isRecording) {
                            detectTapGestures(
                                onTap = {
                                    if (!isVideoFamilyActive) shutterFlashTrigger += 1
                                    viewModel.triggerShutterAction()
                                },
                                onLongPress = {
                                    if ((mode == CameraMode.PHOTO || mode == CameraMode.PRO) && !isVideoFamilyActive) {
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(1.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.2.dp,
                                color = shutterOuterColor,
                                shape = CircleShape
                            )
                            .padding(innerInsetPad)
                            .clip(RoundedCornerShape(innerCornerRadius))
                            .background(shutterCoreColor)
                    )
                    if (runtime.livePhotoCaptureProgress != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 3.5.dp.toPx()
                            drawArc(
                                color = CrimsonShutter,
                                startAngle = -90f,
                                sweepAngle = liveArcSweep,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                // Right Circular Camera Flip Button
                SpringIconButton(
                    onClick = { viewModel.flipFrontBackCamera() },
                    enabled = !runtime.isRecording,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF181A20), CircleShape)
                        .border(0.75.dp, HairlineBorder.copy(alpha = 0.55f), CircleShape)
                        .testTag("camera_flip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Đổi camera trước/sau",
                        tint = if (runtime.isRecording) MutedSlate else PureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // Modal-style More Modes Sheet overlay pinned cleanly at bottom with smooth dimmed scrim
    val scrimAlpha by animateFloatAsState(
        targetValue = if (showMoreModesPanel) 0.48f else 0f,
        animationSpec = tween(if (animEnabled) 180 else 0),
        label = "moreSheetScrimAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (scrimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .then(
                        if (showMoreModesPanel) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showMoreModesPanel = false }
                        } else {
                            Modifier
                        }
                    )
            )
        }
        MoreModesFlagshipSheet(
            visible = showMoreModesPanel,
            currentMode = mode,
            isMemeMode = isMemeMode,
            modeColorStyle = settings.modeColorStyle,
            onSelectMode = { viewModel.setCameraMode(it) },
            onOpenCapabilityReport = onOpenCapabilityReport,
            onToggleMemeMode = { viewModel.setMemeCaptureMode(!isMemeMode) },
            onDismiss = { showMoreModesPanel = false }
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

@Composable
private fun OverlayAnimatedVisibility(
    visible: Boolean,
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        content = content
    )
}

@Composable
private fun IsolatedElectronicLevelOverlay(viewModel: MainViewModel) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    ElectronicLevelOverlay(
        rollDegrees = telemetry.rollDegrees,
        isAligned = telemetry.isLevelAligned
    )
}

@Composable
private fun IsolatedFocusPeakingOverlay(viewModel: MainViewModel) {
    val focusPeaking by viewModel.focusPeakingData.collectAsStateWithLifecycle()
    FocusPeakingOverlay(data = focusPeaking)
}

@Composable
private fun IsolatedLiveHistogramHud(
    viewModel: MainViewModel,
    showExposureWarning: Boolean
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    LiveHistogramHud(
        bins = telemetry.luminanceBins,
        highlightClipPct = telemetry.highlightClipPercent,
        shadowClipPct = telemetry.shadowClipPercent,
        showExposureWarning = showExposureWarning
    )
}

@Composable
private fun IsolatedProControlDeck(
    viewModel: MainViewModel,
    lens: com.example.model.CameraLensInfo?,
    params: com.example.model.ProCameraParameters,
    captureType: ProCaptureType,
    onSelectCaptureType: (ProCaptureType) -> Unit,
    onUpdate: ((com.example.model.ProCameraParameters) -> com.example.model.ProCameraParameters) -> Unit
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    ProControlDeck(
        lens = lens,
        params = params,
        captureType = captureType,
        onSelectCaptureType = onSelectCaptureType,
        liveIso = telemetry.activeIso,
        liveExposureNs = telemetry.activeExposureNs,
        onUpdate = onUpdate
    )
}
