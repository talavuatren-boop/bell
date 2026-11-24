package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AccentColorPreset
import com.example.model.AiProcessingMode
import com.example.model.AspectRatioMode
import com.example.model.CameraMode
import com.example.model.GridType
import com.example.model.HdrMode
import com.example.model.LivePhotoDuration
import com.example.model.ModeColorStyle
import com.example.model.PhotoFormat
import com.example.model.PhotoResolutionTier
import com.example.model.StabilizationMode
import com.example.model.VideoBitrateTier
import com.example.model.VideoCodecOption
import com.example.ui.MainViewModel
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonLeica
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LevelGreen
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalOnAccentColor
import com.example.ui.theme.ModePhotoCrimson
import com.example.ui.theme.ModePortraitRose
import com.example.ui.theme.ModeProCyan
import com.example.ui.theme.ModeSlowMoEmerald
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.resolveAccentPreviewColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenCapabilityReport: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val activeLens by viewModel.activeLens.collectAsStateWithLifecycle()

    // Query available storage ONCE on background IO (never collect live 30-60fps telemetry in Settings)
    val freeStorageMb by produceState(initialValue = 0L) {
        value = withContext(Dispatchers.IO) { viewModel.mediaRepo.getAvailableStorageMb() }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateSettings { it.copy(locationMetadataEnabled = granted) }
    }

    val camGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Clean Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedObsidian)
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack)
                    .testTag("settings_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = PureWhite
                )
            }
            Column {
                Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, color = PureWhite)
                Text(
                    text = "HUNG CAM • Tối ưu cảm biến & hiệu năng",
                    style = MonoTelemetrySmall.copy(fontSize = 10.5.sp),
                    color = MutedSlate
                )
            }
        }

        // 2. Hardware Capability Hero Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(CardSlate, accent.copy(alpha = 0.14f))
                    )
                )
                .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenCapabilityReport)
                .padding(16.dp)
                .testTag("settings_capability_report_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElevatedObsidian)
                        .border(0.75.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Memory, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Phần cứng Camera2",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Text(
                        text = capabilities?.let {
                            "${it.manufacturer} ${it.deviceModel} • ${it.lenses.size} ống kính • ${it.totalRamMb}MB RAM"
                        } ?: "Đang kiểm tra cảm biến...",
                        style = MonoTelemetrySmall.copy(fontSize = 10.5.sp),
                        color = MutedSlate
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Xem chi tiết", tint = accent)
        }

        // 3. Dedicated Smooth Performance Card (Fixes lag on old/low-end phones)
        SettingsSectionCard(title = "Hiệu năng & Độ mượt", icon = Icons.Outlined.Speed) {
            SettingsToggleRow(
                title = "Chế độ Mượt cho Máy cũ / Yếu",
                subtitle = if (viewModel.isLowEndDevice) {
                    "Đã tự nhận diện máy yếu — giảm tải xem trước, Live Photo & nén bộ nhớ thumbnail"
                } else {
                    "Giảm độ phân giải luồng xem trước & bộ nhớ đệm để giữ 60fps trên mọi dòng máy"
                },
                checked = settings.lowEndOptimizedMode,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(lowEndOptimizedMode = v) } },
                tag = "settings_low_end_mode_switch"
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Hiệu ứng chuyển động 60fps",
                subtitle = "Chuyển cảnh mượt & phản hồi lò xo khi thao tác",
                checked = settings.animationsEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(animationsEnabled = v) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Rung phản hồi xúc giác",
                subtitle = "Nhịp rung nhẹ khi bấm chụp & xoay vòng Pro",
                checked = settings.hapticFeedbackEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(hapticFeedbackEnabled = v) } }
            )
        }

        // 4. Camera Engine & Photo Quality
        SettingsSectionCard(title = "Cảm biến & Chất lượng ảnh", icon = Icons.Outlined.CameraAlt) {
            SettingsToggleRow(
                title = "Ưu tiên Camera sau",
                subtitle = "Mở ống kính chính phía sau khi khởi động",
                checked = settings.defaultLensFacingBack,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(defaultLensFacingBack = v) } }
            )
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Chế độ mặc định",
                items = listOf(CameraMode.PHOTO, CameraMode.VIDEO, CameraMode.PORTRAIT, CameraMode.PRO),
                selected = settings.defaultCameraMode,
                itemLabel = { it.flagshipLabel() },
                onSelect = { m -> viewModel.updateSettings { it.copy(defaultCameraMode = m) } }
            )
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Độ phân giải ảnh",
                items = PhotoResolutionTier.entries,
                selected = settings.photoResolutionTier,
                itemLabel = { it.label },
                onSelect = { tier -> viewModel.updateSettings { it.copy(photoResolutionTier = tier) } }
            )
            val availableFormats = buildList {
                add(PhotoFormat.JPEG)
                if (activeLens?.supportsHeic == true) add(PhotoFormat.HEIC)
            }
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Định dạng ảnh",
                items = availableFormats,
                selected = if (availableFormats.contains(settings.photoFormat)) settings.photoFormat else PhotoFormat.JPEG,
                itemLabel = { it.label },
                onSelect = { fmt -> viewModel.updateSettings { it.copy(photoFormat = fmt) } }
            )
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Chế độ HDR",
                items = HdrMode.entries,
                selected = settings.hdrMode,
                itemLabel = { it.label },
                onSelect = { h -> viewModel.updateSettings { it.copy(hdrMode = h) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Siêu nét HD (Bảo toàn chi tiết cạnh)",
                subtitle = "Tăng độ sắc nét vi mô khi chụp không tạo viền giả",
                checked = settings.cameraAiSuperSharpEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(cameraAiSuperSharpEnabled = v) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Màu gốc rực rỡ tự nhiên (Natural+)",
                subtitle = "Cân bằng tương phản & tông da hài hòa khi chụp",
                checked = settings.naturalColorBoostEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(naturalColorBoostEnabled = v) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Dấu chìm HUNG CAM (Leica Watermark)",
                subtitle = "In thông số tiêu cự, tốc độ màn trập & ISO cuối ảnh",
                checked = settings.cameraWatermarkEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(cameraWatermarkEnabled = v) } }
            )
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Tỷ lệ khung hình",
                items = AspectRatioMode.entries,
                selected = settings.aspectRatio,
                itemLabel = { it.label },
                onSelect = { ar -> viewModel.updateSettings { it.copy(aspectRatio = ar) } }
            )
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Lưới bố cục",
                items = GridType.entries,
                selected = settings.gridType,
                itemLabel = { it.label },
                onSelect = { g -> viewModel.updateSettings { it.copy(gridType = g) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Thước thủy cân bằng",
                subtitle = "Đường căn ngang theo con quay hồi chuyển",
                checked = settings.electronicLevelEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(electronicLevelEnabled = v) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Chụp bằng nút Âm lượng",
                subtitle = "Nhấn Tăng/Giảm âm lượng để chụp hoặc quay nhanh",
                checked = settings.volumeShutterEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(volumeShutterEnabled = v) } }
            )
        }

        // 5. Video & Live Photo
        SettingsSectionCard(title = "Quay Video & Live Photo", icon = Icons.Outlined.Videocam) {
            SettingsChipGroup(
                label = "Bitrate Video",
                items = VideoBitrateTier.entries,
                selected = settings.videoBitrateTier,
                itemLabel = { it.label },
                onSelect = { b -> viewModel.updateSettings { it.copy(videoBitrateTier = b) } }
            )
            val codecs = buildList {
                add(VideoCodecOption.H264)
                if (capabilities?.supportsHevcEncode == true) add(VideoCodecOption.HEVC)
            }
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Chuẩn nén Video",
                items = codecs,
                selected = if (codecs.contains(settings.videoCodec)) settings.videoCodec else VideoCodecOption.H264,
                itemLabel = { it.label },
                onSelect = { c -> viewModel.updateSettings { it.copy(videoCodec = c) } }
            )
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Chống rung",
                items = StabilizationMode.entries,
                selected = settings.stabilizationMode,
                itemLabel = { it.label },
                onSelect = { st -> viewModel.updateSettings { it.copy(stabilizationMode = st) } }
            )
            SettingsRowDivider()
            SettingsChipGroup(
                label = "Thời lượng Live Photo mặc định",
                items = LivePhotoDuration.entries,
                selected = settings.livePhotoDefaultDuration,
                itemLabel = { it.label },
                onSelect = { d -> viewModel.updateSettings { it.copy(livePhotoDefaultDuration = d) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Thu âm Live Photo",
                subtitle = "Ghi đồng bộ âm thanh AAC quanh thời điểm chụp",
                checked = settings.livePhotoSoundEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(livePhotoSoundEnabled = v) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Live Photo Bitrate cao",
                subtitle = "Luồng 14 Mbps sắc nét hơn (máy yếu nên tắt để mượt)",
                checked = settings.livePhotoHighBitrate,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(livePhotoHighBitrate = v) } }
            )
            SettingsRowDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bộ nhớ trống khả dụng", style = MaterialTheme.typography.bodyMedium, color = MutedSlate)
                Text(
                    text = if (freeStorageMb > 0L) "$freeStorageMb MB" else "Đang kiểm tra...",
                    style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
        }

        // 6. AI Lab Processing
        SettingsSectionCard(title = "Phòng Lab AI", icon = Icons.Outlined.AutoAwesome) {
            SettingsChipGroup(
                label = "Chế độ chất lượng mặc định",
                items = AiProcessingMode.entries,
                selected = settings.aiDefaultMode,
                itemLabel = { it.label },
                onSelect = { m -> viewModel.updateSettings { it.copy(aiDefaultMode = m) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Chỉ xử lý cục bộ trên máy",
                subtitle = "Không gửi ảnh hay video ra ngoài thiết bị",
                checked = settings.aiLocalProcessingOnly,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiLocalProcessingOnly = v) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Chống rung AI mặc định",
                subtitle = "Bật ổn định gyro khi xử lý video trong AI Lab",
                checked = settings.aiAutoStabilize,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiAutoStabilize = v) } }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Khử nhiễu & tái tạo nét",
                subtitle = "Giảm nhiễu sáng tối và giữ chi tiết biên ảnh",
                checked = settings.aiAutoDenoise,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiAutoDenoise = v, aiAutoSharpen = v) } }
            )
        }

        // 7. Theme & Privacy
        SettingsSectionCard(title = "Giao diện & Quyền riêng tư", icon = Icons.Outlined.Palette) {
            SettingsModeColorSelector(
                selected = settings.modeColorStyle,
                onSelect = { style ->
                    viewModel.updateSettings { it.copy(modeColorStyle = style) }
                    viewModel.performHapticTick()
                }
            )
            SettingsRowDivider()
            SettingsAccentColorSelector(
                selected = settings.accentColorPreset,
                onSelect = { a ->
                    viewModel.updateSettings { it.copy(accentColorPreset = a) }
                    viewModel.performHapticTick()
                }
            )
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Nền đen OLED",
                subtitle = "Đen sâu tối giản tương phản cao",
                checked = settings.darkModeEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(darkModeEnabled = v) } }
            )
            SettingsRowDivider()
            PermissionRow("Quyền Camera", camGranted) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
            SettingsRowDivider()
            PermissionRow("Quyền Micro", micGranted) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
            SettingsRowDivider()
            SettingsToggleRow(
                title = "Lưu tọa độ vị trí vào EXIF",
                subtitle = "Gắn thông tin địa điểm chụp vào ảnh",
                checked = settings.locationMetadataEnabled,
                onCheckedChange = { enable ->
                    if (enable) {
                        val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLoc) {
                            viewModel.updateSettings { it.copy(locationMetadataEnabled = true) }
                        } else {
                            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    } else {
                        viewModel.updateSettings { it.copy(locationMetadataEnabled = false) }
                    }
                }
            )
            SettingsRowDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MutedSlate, modifier = Modifier.size(14.dp))
                Text(
                    text = "Truy cập thư viện bằng Android Photo Picker — không cần quyền bộ nhớ rộng.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                    color = MutedSlate
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = LocalAccentColor.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface, RoundedCornerShape(18.dp))
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp),
                color = PureWhite
            )
        }
        HorizontalDivider(thickness = 0.75.dp, color = HairlineBorder)
        content()
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = HairlineBorder.copy(alpha = 0.5f))
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String? = null
) {
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = PureWhite)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp), color = MutedSlate)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = onAccent,
                checkedTrackColor = accent,
                uncheckedThumbColor = PureWhite.copy(alpha = 0.85f),
                uncheckedTrackColor = ElevatedObsidian,
                uncheckedBorderColor = HairlineBorder
            )
        )
    }
}

@Composable
private fun <T> SettingsChipGroup(
    label: String,
    items: List<T>,
    selected: T,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = PureWhite.copy(alpha = 0.85f))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accent else ElevatedObsidian)
                        .border(0.75.dp, if (isSelected) accent else HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelect(item) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = itemLabel(item),
                        style = MonoTelemetrySmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) onAccent else PureWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsModeColorSelector(
    selected: ModeColorStyle,
    onSelect: (ModeColorStyle) -> Unit
) {
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Màu các chế độ Camera (Mode)",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = PureWhite.copy(alpha = 0.88f)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeColorStyle.entries.forEach { style ->
                val isSelected = style == selected
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accent else ElevatedObsidian)
                        .border(0.75.dp, if (isSelected) accent else HairlineBorder, RoundedCornerShape(12.dp))
                        .clickable { onSelect(style) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("settings_mode_color_${style.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    when (style) {
                        ModeColorStyle.DEFAULT_RED -> {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonLeica)
                                    .border(0.75.dp, PureWhite.copy(alpha = 0.7f), CircleShape)
                            )
                        }
                        ModeColorStyle.PER_MODE_VIBRANT -> {
                            Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                                listOf(ModeProCyan, ModePhotoCrimson, ModePortraitRose, ModeSlowMoEmerald).forEach { dotColor ->
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                            .border(0.6.dp, PureBlack, CircleShape)
                                    )
                                }
                            }
                        }
                        ModeColorStyle.MONOCHROME_WHITE -> {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(PureWhite)
                                    .border(0.75.dp, PureBlack.copy(alpha = 0.4f), CircleShape)
                            )
                        }
                    }
                    Text(
                        text = style.label,
                        style = MonoTelemetrySmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) onAccent else PureWhite
                    )
                }
            }
        }
        Text(
            text = selected.subtitle,
            style = MonoTelemetrySmall.copy(fontSize = 10.5.sp),
            color = MutedSlate
        )
    }
}

@Composable
private fun SettingsAccentColorSelector(
    selected: AccentColorPreset,
    onSelect: (AccentColorPreset) -> Unit
) {
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "Màu chủ đạo toàn app",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = PureWhite.copy(alpha = 0.88f)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            AccentColorPreset.entries.forEach { preset ->
                val isSelected = preset == selected
                val swatchColor: Color = resolveAccentPreviewColor(preset)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isSelected) accent else ElevatedObsidian)
                        .border(0.75.dp, if (isSelected) accent else HairlineBorder, RoundedCornerShape(11.dp))
                        .clickable { onSelect(preset) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("settings_accent_${preset.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = 0.75.dp,
                                color = if (swatchColor == PureWhite) PureBlack.copy(alpha = 0.45f) else PureWhite.copy(alpha = 0.65f),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = preset.label,
                        style = MonoTelemetrySmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) onAccent else PureWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PureWhite)
        Text(
            text = if (granted) "ĐÃ CẤP" else "CHẠM ĐỂ CẤP",
            style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold),
            color = if (granted) LevelGreen else ErrorCrimson
        )
    }
}
