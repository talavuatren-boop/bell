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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.model.LivePhotoDuration
import com.example.model.PhotoFormat
import com.example.model.PhotoResolutionTier
import com.example.model.StabilizationMode
import com.example.model.VideoBitrateTier
import com.example.model.VideoCodecOption
import com.example.ui.MainViewModel
import com.example.ui.theme.CardSlate
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LevelGreen
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalOnAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

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
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(ElevatedObsidian)
                    .border(1.dp, HairlineBorder, CircleShape)
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
            Text("Cài đặt Camera", style = MaterialTheme.typography.headlineMedium, color = PureWhite)
        }

        // Device Capability Report Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardSlate)
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
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
                Icon(Icons.Default.Memory, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                Column {
                    Text("Thông số phần cứng Camera", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Text(
                        text = capabilities?.let { "${it.manufacturer} ${it.deviceModel} • ${it.lenses.size} ống kính" }
                            ?: "Đang kiểm tra cảm biến...",
                        style = MonoTelemetrySmall,
                        color = MutedSlate
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Xem chi tiết", tint = accent)
        }

        // 1. Camera Settings
        SettingsSectionCard("Động cơ Camera") {
            SettingsToggleRow(
                title = "Ưu tiên Camera sau",
                subtitle = "Mở cảm biến chính phía sau khi khởi động ứng dụng",
                checked = settings.defaultLensFacingBack,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(defaultLensFacingBack = v) } }
            )
            SettingsChipGroup(
                label = "Chế độ mặc định",
                items = listOf(CameraMode.PHOTO, CameraMode.VIDEO, CameraMode.PORTRAIT, CameraMode.PRO),
                selected = settings.defaultCameraMode,
                itemLabel = { it.flagshipLabel() },
                onSelect = { m -> viewModel.updateSettings { it.copy(defaultCameraMode = m) } }
            )
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
            SettingsChipGroup(
                label = "Định dạng ảnh",
                items = availableFormats,
                selected = if (availableFormats.contains(settings.photoFormat)) settings.photoFormat else PhotoFormat.JPEG,
                itemLabel = { it.label },
                onSelect = { fmt -> viewModel.updateSettings { it.copy(photoFormat = fmt) } }
            )
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
            SettingsChipGroup(
                label = "Chuẩn nén Video",
                items = codecs,
                selected = if (codecs.contains(settings.videoCodec)) settings.videoCodec else VideoCodecOption.H264,
                itemLabel = { it.label },
                onSelect = { c -> viewModel.updateSettings { it.copy(videoCodec = c) } }
            )
            SettingsChipGroup(
                label = "Chống rung",
                items = StabilizationMode.entries,
                selected = settings.stabilizationMode,
                itemLabel = { it.label },
                onSelect = { st -> viewModel.updateSettings { it.copy(stabilizationMode = st) } }
            )
            SettingsChipGroup(
                label = "Tỷ lệ khung hình",
                items = AspectRatioMode.entries,
                selected = settings.aspectRatio,
                itemLabel = { it.label },
                onSelect = { ar -> viewModel.updateSettings { it.copy(aspectRatio = ar) } }
            )
            SettingsChipGroup(
                label = "Lưới bố cục",
                items = GridType.entries,
                selected = settings.gridType,
                itemLabel = { it.label },
                onSelect = { g -> viewModel.updateSettings { it.copy(gridType = g) } }
            )
            SettingsToggleRow(
                title = "Thước thủy cân bằng",
                subtitle = "Hiển thị đường căn ngang theo con quay hồi chuyển",
                checked = settings.electronicLevelEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(electronicLevelEnabled = v) } }
            )
            SettingsToggleRow(
                title = "Chụp bằng nút Âm lượng",
                subtitle = "Nhấn nút Tăng/Giảm âm lượng để bấm máy nhanh",
                checked = settings.volumeShutterEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(volumeShutterEnabled = v) } }
            )
            SettingsToggleRow(
                title = "Màu gốc rực rỡ tự nhiên (Natural+)",
                subtitle = "Cân bằng tương phản & dải động da người khi chụp ảnh gốc",
                checked = settings.naturalColorBoostEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(naturalColorBoostEnabled = v) } }
            )
            SettingsToggleRow(
                title = "Dấu chìm HUNG CAM (Leica Watermark)",
                subtitle = "Thêm dải thông số tiêu cự, tốc độ màn trập & ISO cuối ảnh",
                checked = settings.cameraWatermarkEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(cameraWatermarkEnabled = v) } }
            )
        }

        // 2. Live Photo Settings
        SettingsSectionCard("Động cơ Live Photo") {
            SettingsChipGroup(
                label = "Thời lượng mặc định",
                items = LivePhotoDuration.entries,
                selected = settings.livePhotoDefaultDuration,
                itemLabel = { it.label },
                onSelect = { d -> viewModel.updateSettings { it.copy(livePhotoDefaultDuration = d) } }
            )
            SettingsToggleRow(
                title = "Thu âm Live Photo",
                subtitle = "Ghi đồng bộ âm thanh AAC xung quanh thời điểm bấm máy",
                checked = settings.livePhotoSoundEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(livePhotoSoundEnabled = v) } }
            )
            SettingsToggleRow(
                title = "Ring Buffer Bitrate cao",
                subtitle = "Luồng H.264 16 Mbps sắc nét (so với tiêu chuẩn 10 Mbps)",
                checked = settings.livePhotoHighBitrate,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(livePhotoHighBitrate = v) } }
            )
            Text(
                text = "Bộ nhớ khả dụng: ${telemetry.freeStorageMb} MB",
                style = MonoTelemetrySmall,
                color = MutedSlate
            )
        }

        // 3. AI Engine Settings
        SettingsSectionCard("Xử lý AI trên thiết bị") {
            SettingsChipGroup(
                label = "Chế độ chất lượng mặc định",
                items = AiProcessingMode.entries,
                selected = settings.aiDefaultMode,
                itemLabel = { it.label },
                onSelect = { m -> viewModel.updateSettings { it.copy(aiDefaultMode = m) } }
            )
            SettingsToggleRow(
                title = "Chỉ xử lý cục bộ trên máy",
                subtitle = "Giữ toàn bộ ảnh và video trên thiết bị, không tải lên mạng",
                checked = settings.aiLocalProcessingOnly,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiLocalProcessingOnly = v) } }
            )
            SettingsToggleRow(
                title = "Chống rung AI mặc định",
                subtitle = "Tự động bật chống rung gyro trong AI Studio",
                checked = settings.aiAutoStabilize,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiAutoStabilize = v) } }
            )
            SettingsToggleRow(
                title = "Khử nhiễu & tăng nét tự động",
                subtitle = "Khử nhiễu YCbCr bảo toàn chi tiết biên không quầng sáng",
                checked = settings.aiAutoDenoise,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiAutoDenoise = v, aiAutoSharpen = v) } }
            )
        }

        // 4. Interface Settings
        SettingsSectionCard("Giao diện & Chuyển động") {
            SettingsChipGroup(
                label = "Màu chủ đạo",
                items = AccentColorPreset.entries,
                selected = settings.accentColorPreset,
                itemLabel = { it.label },
                onSelect = { a -> viewModel.updateSettings { it.copy(accentColorPreset = a) } }
            )
            SettingsToggleRow(
                title = "Nền đen OLED",
                subtitle = "Giao diện tối sâu tương phản cao cho màn hình AMOLED",
                checked = settings.darkModeEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(darkModeEnabled = v) } }
            )
            SettingsToggleRow(
                title = "Rung phản hồi xúc giác",
                subtitle = "Nhịp rung khi bấm màn trập, xoay vòng Pro & phát Live Photo",
                checked = settings.hapticFeedbackEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(hapticFeedbackEnabled = v) } }
            )
            SettingsToggleRow(
                title = "Hiệu ứng chuyển động 60fps",
                subtitle = "Chuyển cảnh và con trỏ lấy nét lò xo mượt mà",
                checked = settings.animationsEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(animationsEnabled = v) } }
            )
        }

        // 5. Privacy & Permissions
        SettingsSectionCard("Quyền & Riêng tư") {
            PermissionRow("Quyền Camera", camGranted) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
            PermissionRow("Quyền Micro", micGranted) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
            Text(
                text = "Truy cập thư viện: Sử dụng Android Photo Picker bảo mật không cần quyền bộ nhớ rộng (tuân thủ Google Play).",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MutedSlate
            )
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
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSlate, RoundedCornerShape(16.dp))
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = LocalAccentColor.current
            )
            content()
        }
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), color = PureWhite)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = MutedSlate)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedSlate)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accent else ElevatedObsidian)
                        .border(1.dp, if (isSelected) accent else HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelect(item) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
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
private fun PermissionRow(label: String, granted: Boolean, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings)
            .padding(vertical = 4.dp),
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
