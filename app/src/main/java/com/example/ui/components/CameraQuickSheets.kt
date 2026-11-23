package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.CameraEnhance
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RawOn
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HungCamSettings
import com.example.model.CameraLensInfo
import com.example.model.CameraMode
import com.example.model.GridType
import com.example.model.LivePhotoDuration
import com.example.model.PhotoFilterPreset
import com.example.model.PhotoResolutionTier
import com.example.model.ProCameraParameters
import com.example.model.ProCaptureType
import com.example.model.SelfTimerDuration
import com.example.model.SlowMoOption
import com.example.model.StabilizationMode
import com.example.model.TimelapseSpeed
import com.example.ui.theme.CardSlate
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

data class QuickGridAction(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val badgeText: String? = null,
    val isActive: Boolean = false,
    val testTag: String = "quick_grid_$id",
    val onClick: () -> Unit
)

@Composable
fun ModeQuickControlGrid(
    visible: Boolean,
    mode: CameraMode,
    settings: HungCamSettings,
    activeLens: CameraLensInfo?,
    liveDuration: LivePhotoDuration,
    selfTimer: SelfTimerDuration,
    proParams: ProCameraParameters,
    proCaptureType: ProCaptureType,
    activeFilter: PhotoFilterPreset,
    isAudioMuted: Boolean,
    timelapseSpeed: TimelapseSpeed,
    slowMoOption: SlowMoOption?,
    onToggleHdr: () -> Unit,
    onCycleLiveDuration: () -> Unit,
    onToggleNaturalBoost: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onCycleSelfTimer: () -> Unit,
    onCycleGrid: () -> Unit,
    onToggleLevel: () -> Unit,
    onToggleLiveSound: () -> Unit,
    onTogglePhotoResolution: () -> Unit,
    onToggleWatermark: () -> Unit,
    onCycleVideoRes: () -> Unit,
    onCycleVideoFps: () -> Unit,
    onCycleStab: () -> Unit,
    onCycleVideoBitrate: () -> Unit,
    onToggleMicMute: () -> Unit,
    onCycleProCaptureType: () -> Unit,
    onToggleProRaw: () -> Unit,
    onToggleFocusPeaking: () -> Unit,
    onToggleExposureWarning: () -> Unit,
    onCycleSlowMo: () -> Unit,
    onCycleTimelapseSpeed: () -> Unit,
    onOpenFilters: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    val items: List<QuickGridAction> = remember(
        mode, settings, activeLens, liveDuration, selfTimer,
        proParams, proCaptureType, activeFilter, isAudioMuted,
        timelapseSpeed, slowMoOption
    ) {
        when (mode) {
            CameraMode.PHOTO -> listOf(
                QuickGridAction(
                    id = "hdr",
                    label = "HDR",
                    badgeText = if (settings.hdrEnabled) "HDRᴀ" else "HDR",
                    isActive = settings.hdrEnabled,
                    onClick = onToggleHdr
                ),
                QuickGridAction(
                    id = "live",
                    label = if (liveDuration == LivePhotoDuration.OFF) "Live Ảnh" else "Live ${liveDuration.label}",
                    icon = Icons.Outlined.MotionPhotosOn,
                    isActive = liveDuration != LivePhotoDuration.OFF,
                    onClick = onCycleLiveDuration
                ),
                QuickGridAction(
                    id = "natural_plus",
                    label = "Màu đẹp+",
                    icon = Icons.Outlined.Palette,
                    isActive = settings.naturalColorBoostEnabled,
                    onClick = onToggleNaturalBoost
                ),
                QuickGridAction(
                    id = "aspect",
                    label = "Tỷ lệ khung",
                    badgeText = settings.aspectRatio.label,
                    isActive = false,
                    onClick = onCycleAspectRatio
                ),
                QuickGridAction(
                    id = "timer",
                    label = if (selfTimer == SelfTimerDuration.OFF) "Hẹn giờ" else "Hẹn ${selfTimer.label}",
                    icon = Icons.Outlined.Timer,
                    isActive = selfTimer != SelfTimerDuration.OFF,
                    onClick = onCycleSelfTimer
                ),
                QuickGridAction(
                    id = "grid",
                    label = if (settings.gridType == GridType.NONE) "Lưới" else "Lưới ${settings.gridType.label}",
                    icon = Icons.Outlined.GridOn,
                    isActive = settings.gridType != GridType.NONE,
                    onClick = onCycleGrid
                ),
                QuickGridAction(
                    id = "level",
                    label = "Thước thủy",
                    icon = Icons.Outlined.Straighten,
                    isActive = settings.electronicLevelEnabled,
                    onClick = onToggleLevel
                ),
                QuickGridAction(
                    id = "live_sound",
                    label = if (settings.livePhotoSoundEnabled) "Âm Live" else "Tắt âm Live",
                    icon = if (settings.livePhotoSoundEnabled) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    isActive = settings.livePhotoSoundEnabled,
                    onClick = onToggleLiveSound
                ),
                QuickGridAction(
                    id = "photo_res",
                    label = if (settings.photoResolutionTier == PhotoResolutionTier.MAX) "Độ phân giải MAX" else "Tiêu chuẩn",
                    icon = Icons.Outlined.HighQuality,
                    isActive = settings.photoResolutionTier == PhotoResolutionTier.MAX,
                    onClick = onTogglePhotoResolution
                ),
                QuickGridAction(
                    id = "watermark",
                    label = "Dấu chìm",
                    icon = Icons.Outlined.BrandingWatermark,
                    isActive = settings.cameraWatermarkEnabled,
                    onClick = onToggleWatermark
                ),
                QuickGridAction(
                    id = "filters",
                    label = if (activeFilter == PhotoFilterPreset.NATURAL) "Bộ lọc" else activeFilter.label,
                    icon = Icons.Outlined.FilterVintage,
                    isActive = activeFilter != PhotoFilterPreset.NATURAL,
                    onClick = {
                        onDismiss()
                        onOpenFilters()
                    }
                )
            )

            CameraMode.VIDEO -> listOf(
                QuickGridAction(
                    id = "video_res",
                    label = "Độ phân giải",
                    badgeText = settings.videoResolution.label.uppercase(),
                    isActive = settings.videoResolution.height >= 1080,
                    onClick = onCycleVideoRes
                ),
                QuickGridAction(
                    id = "video_fps",
                    label = "Tốc độ khung",
                    badgeText = "${settings.videoFps}FPS",
                    isActive = settings.videoFps >= 60,
                    onClick = onCycleVideoFps
                ),
                QuickGridAction(
                    id = "stab",
                    label = "Chống rung: ${settings.stabilizationMode.label}",
                    icon = Icons.Outlined.Vibration,
                    isActive = settings.stabilizationMode != StabilizationMode.OFF,
                    onClick = onCycleStab
                ),
                QuickGridAction(
                    id = "mic",
                    label = if (isAudioMuted) "Đang tắt Mic" else "Thu âm Mic",
                    icon = if (isAudioMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    isActive = !isAudioMuted,
                    onClick = onToggleMicMute
                ),
                QuickGridAction(
                    id = "bitrate",
                    label = "Bitrate: ${settings.videoBitrateTier.label}",
                    icon = Icons.Outlined.Hd,
                    isActive = settings.videoBitrateTier != com.example.model.VideoBitrateTier.STANDARD,
                    onClick = onCycleVideoBitrate
                ),
                QuickGridAction(
                    id = "grid",
                    label = if (settings.gridType == GridType.NONE) "Lưới" else "Lưới ${settings.gridType.label}",
                    icon = Icons.Outlined.GridOn,
                    isActive = settings.gridType != GridType.NONE,
                    onClick = onCycleGrid
                ),
                QuickGridAction(
                    id = "level",
                    label = "Thước thủy",
                    icon = Icons.Outlined.Straighten,
                    isActive = settings.electronicLevelEnabled,
                    onClick = onToggleLevel
                ),
                QuickGridAction(
                    id = "filters",
                    label = if (activeFilter == PhotoFilterPreset.NATURAL) "Bộ lọc Video" else activeFilter.label,
                    icon = Icons.Outlined.FilterVintage,
                    isActive = activeFilter != PhotoFilterPreset.NATURAL,
                    onClick = {
                        onDismiss()
                        onOpenFilters()
                    }
                )
            )

            CameraMode.PORTRAIT -> listOf(
                QuickGridAction(
                    id = "hdr",
                    label = "HDR",
                    badgeText = if (settings.hdrEnabled) "HDRᴀ" else "HDR",
                    isActive = settings.hdrEnabled,
                    onClick = onToggleHdr
                ),
                QuickGridAction(
                    id = "natural_plus",
                    label = "Da đẹp tự nhiên",
                    icon = Icons.Outlined.Palette,
                    isActive = settings.naturalColorBoostEnabled,
                    onClick = onToggleNaturalBoost
                ),
                QuickGridAction(
                    id = "aspect",
                    label = "Tỷ lệ khung",
                    badgeText = settings.aspectRatio.label,
                    isActive = false,
                    onClick = onCycleAspectRatio
                ),
                QuickGridAction(
                    id = "timer",
                    label = if (selfTimer == SelfTimerDuration.OFF) "Hẹn giờ" else "Hẹn ${selfTimer.label}",
                    icon = Icons.Outlined.Timer,
                    isActive = selfTimer != SelfTimerDuration.OFF,
                    onClick = onCycleSelfTimer
                ),
                QuickGridAction(
                    id = "grid",
                    label = if (settings.gridType == GridType.NONE) "Lưới" else "Lưới ${settings.gridType.label}",
                    icon = Icons.Outlined.GridOn,
                    isActive = settings.gridType != GridType.NONE,
                    onClick = onCycleGrid
                ),
                QuickGridAction(
                    id = "watermark",
                    label = "Dấu chìm",
                    icon = Icons.Outlined.BrandingWatermark,
                    isActive = settings.cameraWatermarkEnabled,
                    onClick = onToggleWatermark
                ),
                QuickGridAction(
                    id = "level",
                    label = "Thước thủy",
                    icon = Icons.Outlined.Straighten,
                    isActive = settings.electronicLevelEnabled,
                    onClick = onToggleLevel
                ),
                QuickGridAction(
                    id = "filters",
                    label = if (activeFilter == PhotoFilterPreset.NATURAL) "Bộ lọc" else activeFilter.label,
                    icon = Icons.Outlined.FilterVintage,
                    isActive = activeFilter != PhotoFilterPreset.NATURAL,
                    onClick = {
                        onDismiss()
                        onOpenFilters()
                    }
                )
            )

            CameraMode.PRO -> listOf(
                QuickGridAction(
                    id = "pro_type",
                    label = if (proCaptureType == ProCaptureType.PHOTO) "Pro Ảnh" else "Pro Video",
                    icon = if (proCaptureType == ProCaptureType.PHOTO) Icons.Outlined.CameraEnhance else Icons.Outlined.Videocam,
                    isActive = true,
                    onClick = onCycleProCaptureType
                ),
                QuickGridAction(
                    id = "raw",
                    label = "RAW / DNG",
                    icon = Icons.Outlined.RawOn,
                    isActive = proParams.captureRawDng && (activeLens?.supportsRaw == true),
                    onClick = onToggleProRaw
                ),
                QuickGridAction(
                    id = "peaking",
                    label = "Lấy nét đỉnh",
                    icon = Icons.Outlined.CenterFocusStrong,
                    isActive = proParams.focusPeakingEnabled,
                    onClick = onToggleFocusPeaking
                ),
                QuickGridAction(
                    id = "zebra",
                    label = "Cảnh báo cháy",
                    icon = Icons.Outlined.BrightnessHigh,
                    isActive = proParams.exposureWarningEnabled,
                    onClick = onToggleExposureWarning
                ),
                QuickGridAction(
                    id = "aspect",
                    label = "Tỷ lệ khung",
                    badgeText = if (proCaptureType == ProCaptureType.VIDEO) "16:9" else settings.aspectRatio.label,
                    isActive = false,
                    onClick = onCycleAspectRatio
                ),
                QuickGridAction(
                    id = "grid",
                    label = if (settings.gridType == GridType.NONE) "Lưới" else "Lưới ${settings.gridType.label}",
                    icon = Icons.Outlined.GridOn,
                    isActive = settings.gridType != GridType.NONE,
                    onClick = onCycleGrid
                ),
                QuickGridAction(
                    id = "level",
                    label = "Thước thủy",
                    icon = Icons.Outlined.Straighten,
                    isActive = settings.electronicLevelEnabled,
                    onClick = onToggleLevel
                )
            )

            CameraMode.SLOW_MOTION -> listOf(
                QuickGridAction(
                    id = "slowmo_fps",
                    label = slowMoOption?.label ?: "120/240fps",
                    icon = Icons.Outlined.SlowMotionVideo,
                    isActive = slowMoOption != null,
                    onClick = onCycleSlowMo
                ),
                QuickGridAction(
                    id = "grid",
                    label = if (settings.gridType == GridType.NONE) "Lưới" else "Lưới ${settings.gridType.label}",
                    icon = Icons.Outlined.GridOn,
                    isActive = settings.gridType != GridType.NONE,
                    onClick = onCycleGrid
                ),
                QuickGridAction(
                    id = "level",
                    label = "Thước thủy",
                    icon = Icons.Outlined.Straighten,
                    isActive = settings.electronicLevelEnabled,
                    onClick = onToggleLevel
                ),
                QuickGridAction(
                    id = "filters",
                    label = if (activeFilter == PhotoFilterPreset.NATURAL) "Bộ lọc" else activeFilter.label,
                    icon = Icons.Outlined.FilterVintage,
                    isActive = activeFilter != PhotoFilterPreset.NATURAL,
                    onClick = {
                        onDismiss()
                        onOpenFilters()
                    }
                )
            )

            CameraMode.TIMELAPSE -> listOf(
                QuickGridAction(
                    id = "timelapse_speed",
                    label = "Tốc độ ${timelapseSpeed.label}",
                    icon = Icons.Outlined.Speed,
                    isActive = true,
                    onClick = onCycleTimelapseSpeed
                ),
                QuickGridAction(
                    id = "grid",
                    label = if (settings.gridType == GridType.NONE) "Lưới" else "Lưới ${settings.gridType.label}",
                    icon = Icons.Outlined.GridOn,
                    isActive = settings.gridType != GridType.NONE,
                    onClick = onCycleGrid
                ),
                QuickGridAction(
                    id = "level",
                    label = "Thước thủy",
                    icon = Icons.Outlined.Straighten,
                    isActive = settings.electronicLevelEnabled,
                    onClick = onToggleLevel
                ),
                QuickGridAction(
                    id = "filters",
                    label = if (activeFilter == PhotoFilterPreset.NATURAL) "Bộ lọc" else activeFilter.label,
                    icon = Icons.Outlined.FilterVintage,
                    isActive = activeFilter != PhotoFilterPreset.NATURAL,
                    onClick = {
                        onDismiss()
                        onOpenFilters()
                    }
                )
            )
        }
    }

    val animEnabled = LocalAnimationsEnabled.current
    AnimatedVisibility(
        visible = visible,
        enter = HungCamMotion.panelEnter(animEnabled),
        exit = HungCamMotion.panelExit(animEnabled),
        modifier = modifier
    ) {
        val rows = remember(items) { items.chunked(4) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE60B0C0F))
                .border(0.75.dp, HairlineBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .testTag("camera_quick_control_grid")
        ) {
            rows.forEachIndexed { rowIdx, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (colIdx in 0 until 4) {
                        val action = rowItems.getOrNull(colIdx)
                        if (action != null) {
                            val tileInteraction = remember(action.id) { MutableInteractionSource() }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(78.dp)
                                    .springPressScale(tileInteraction, pressedScale = 0.92f)
                                    .clickable(interactionSource = tileInteraction, indication = null) {
                                        action.onClick()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag(action.testTag),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val iconTint = if (action.isActive) accent else PureWhite.copy(alpha = 0.78f)
                                Box(contentAlignment = Alignment.TopEnd) {
                                    if (action.badgeText != null) {
                                        Text(
                                            text = action.badgeText,
                                            style = MonoTelemetrySmall.copy(
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = iconTint,
                                            maxLines = 1
                                        )
                                    } else if (action.icon != null) {
                                        Icon(
                                            imageVector = action.icon,
                                            contentDescription = action.label,
                                            tint = iconTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    if (action.isActive) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = if (action.isActive) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (action.isActive) PureWhite else MutedSlate,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).height(78.dp))
                        }
                    }
                }
                if (rowIdx < rows.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp, color = HairlineBorder.copy(alpha = 0.45f))
                }
            }
        }
    }
}

private data class MoreDrawerItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun MoreModesFlagshipSheet(
    visible: Boolean,
    currentMode: CameraMode,
    hasLatestPhoto: Boolean,
    onSelectMode: (CameraMode) -> Unit,
    onOpenAiStudio: () -> Unit,
    onOpenVideoMerge: () -> Unit,
    onOpenCapabilityReport: () -> Unit,
    onOpenMemeEditor: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    val animEnabled = LocalAnimationsEnabled.current
    AnimatedVisibility(
        visible = visible,
        enter = HungCamMotion.sheetEnter(animEnabled),
        exit = HungCamMotion.sheetExit(animEnabled),
        modifier = modifier
    ) {
        val entries = remember(currentMode, hasLatestPhoto) {
            buildList {
                add(
                    MoreDrawerItem(
                        id = "pro",
                        title = "Chuyên nghiệp",
                        icon = Icons.Outlined.Tune,
                        selected = currentMode == CameraMode.PRO,
                        testTag = "more_mode_pro",
                        onClick = { onSelectMode(CameraMode.PRO); onDismiss() }
                    )
                )
                add(
                    MoreDrawerItem(
                        id = "slowmo",
                        title = "Chuyển động chậm",
                        icon = Icons.Outlined.SlowMotionVideo,
                        selected = currentMode == CameraMode.SLOW_MOTION,
                        testTag = "more_mode_slow_motion",
                        onClick = { onSelectMode(CameraMode.SLOW_MOTION); onDismiss() }
                    )
                )
                add(
                    MoreDrawerItem(
                        id = "timelapse",
                        title = "Chuyển động nhanh",
                        icon = Icons.Outlined.Speed,
                        selected = currentMode == CameraMode.TIMELAPSE,
                        testTag = "more_mode_timelapse",
                        onClick = { onSelectMode(CameraMode.TIMELAPSE); onDismiss() }
                    )
                )
                add(
                    MoreDrawerItem(
                        id = "ai_studio",
                        title = "AI Studio",
                        icon = Icons.Outlined.AutoAwesome,
                        selected = false,
                        testTag = "more_mode_ai_studio",
                        onClick = { onDismiss(); onOpenAiStudio() }
                    )
                )
                add(
                    MoreDrawerItem(
                        id = "video_merge",
                        title = "Ghép Video",
                        icon = Icons.Outlined.MergeType,
                        selected = false,
                        testTag = "more_mode_video_merge",
                        onClick = { onDismiss(); onOpenVideoMerge() }
                    )
                )
                add(
                    MoreDrawerItem(
                        id = "hardware",
                        title = "Phần cứng",
                        icon = Icons.Outlined.Memory,
                        selected = false,
                        testTag = "more_mode_hardware",
                        onClick = { onDismiss(); onOpenCapabilityReport() }
                    )
                )
                if (hasLatestPhoto) {
                    add(
                        MoreDrawerItem(
                            id = "meme",
                            title = "Sticker & Meme",
                            icon = Icons.Outlined.EmojiEmotions,
                            selected = false,
                            testTag = "more_mode_meme",
                            onClick = { onDismiss(); onOpenMemeEditor() }
                        )
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xF5090A0C))
                .border(
                    width = 0.75.dp,
                    color = HairlineBorder.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("more_modes_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MutedSlate.copy(alpha = 0.5f))
                    .clickable { onDismiss() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            val rows = entries.chunked(3)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (idx in 0 until 3) {
                        val item = rowItems.getOrNull(idx)
                        if (item != null) {
                            val tileInt = remember(item.id) { MutableInteractionSource() }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .springPressScale(tileInt, pressedScale = 0.90f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(interactionSource = tileInt, indication = null) {
                                        item.onClick()
                                    }
                                    .padding(vertical = 14.dp, horizontal = 6.dp)
                                    .testTag(item.testTag),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (item.selected) accent.copy(alpha = 0.16f) else CardSlate.copy(alpha = 0.45f)
                                        )
                                        .border(
                                            width = if (item.selected) 1.dp else 0.75.dp,
                                            color = if (item.selected) accent else HairlineBorder.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = if (item.selected) accent else PureWhite,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (item.selected) accent else PureWhite,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
