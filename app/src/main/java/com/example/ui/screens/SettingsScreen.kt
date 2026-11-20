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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LevelGreen
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.WarningAmber

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenCapabilityReport: () -> Unit,
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = PureWhite)

        // Device Capability Report Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardSlate)
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenCapabilityReport)
                .padding(16.dp)
                .testTag("settings_capability_report_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                Column {
                    Text("Camera Capability Report", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Text(
                        text = capabilities?.let { "${it.manufacturer} ${it.deviceModel} • ${it.lenses.size} lenses probed" }
                            ?: "Probing hardware...",
                        style = MonoTelemetrySmall,
                        color = MutedSlate
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "View Report", tint = accent)
        }

        // 1. Camera Settings
        SettingsSectionCard("Camera Engine") {
            SettingsToggleRow(
                title = "Default to Rear Camera",
                subtitle = "Launch on main rear sensor instead of front",
                checked = settings.defaultLensFacingBack,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(defaultLensFacingBack = v) } }
            )
            SettingsChipGroup(
                label = "Default Mode",
                items = listOf(CameraMode.PHOTO, CameraMode.VIDEO, CameraMode.PORTRAIT, CameraMode.PRO),
                selected = settings.defaultCameraMode,
                itemLabel = { it.label },
                onSelect = { m -> viewModel.updateSettings { it.copy(defaultCameraMode = m) } }
            )
            SettingsChipGroup(
                label = "Photo Resolution",
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
                label = "Photo Format",
                items = availableFormats,
                selected = if (availableFormats.contains(settings.photoFormat)) settings.photoFormat else PhotoFormat.JPEG,
                itemLabel = { it.label },
                onSelect = { fmt -> viewModel.updateSettings { it.copy(photoFormat = fmt) } }
            )
            SettingsChipGroup(
                label = "Video Bitrate",
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
                label = "Video Codec",
                items = codecs,
                selected = if (codecs.contains(settings.videoCodec)) settings.videoCodec else VideoCodecOption.H264,
                itemLabel = { it.label },
                onSelect = { c -> viewModel.updateSettings { it.copy(videoCodec = c) } }
            )
            SettingsChipGroup(
                label = "Stabilization",
                items = StabilizationMode.entries,
                selected = settings.stabilizationMode,
                itemLabel = { it.label },
                onSelect = { st -> viewModel.updateSettings { it.copy(stabilizationMode = st) } }
            )
            SettingsChipGroup(
                label = "Aspect Ratio",
                items = AspectRatioMode.entries,
                selected = settings.aspectRatio,
                itemLabel = { it.label },
                onSelect = { ar -> viewModel.updateSettings { it.copy(aspectRatio = ar) } }
            )
            SettingsChipGroup(
                label = "Grid Overlay",
                items = GridType.entries,
                selected = settings.gridType,
                itemLabel = { it.label },
                onSelect = { g -> viewModel.updateSettings { it.copy(gridType = g) } }
            )
            SettingsToggleRow(
                title = "Electronic Horizon Level",
                subtitle = "Gyro/gravity roll indicator in viewfinder",
                checked = settings.electronicLevelEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(electronicLevelEnabled = v) } }
            )
            SettingsToggleRow(
                title = "Volume Button Shutter",
                subtitle = "Press Volume Up/Down to trigger shutter",
                checked = settings.volumeShutterEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(volumeShutterEnabled = v) } }
            )
        }

        // 2. Live Photo Settings
        SettingsSectionCard("Live Photo Engine") {
            SettingsChipGroup(
                label = "Default Duration",
                items = LivePhotoDuration.entries,
                selected = settings.livePhotoDefaultDuration,
                itemLabel = { it.label },
                onSelect = { d -> viewModel.updateSettings { it.copy(livePhotoDefaultDuration = d) } }
            )
            SettingsToggleRow(
                title = "Live Photo Audio",
                subtitle = "Record synchronized AAC audio track around shutter",
                checked = settings.livePhotoSoundEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(livePhotoSoundEnabled = v) } }
            )
            SettingsToggleRow(
                title = "High-Bitrate Ring Buffer",
                subtitle = "16 Mbps vs 10 Mbps H.264 stream",
                checked = settings.livePhotoHighBitrate,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(livePhotoHighBitrate = v) } }
            )
            Text(
                text = "Available Device Storage: ${telemetry.freeStorageMb} MB",
                style = MonoTelemetrySmall,
                color = MutedSlate
            )
        }

        // 3. AI Engine Settings
        SettingsSectionCard("AI Processing Engine") {
            SettingsChipGroup(
                label = "Default Quality Mode",
                items = AiProcessingMode.entries,
                selected = settings.aiDefaultMode,
                itemLabel = { it.label },
                onSelect = { m -> viewModel.updateSettings { it.copy(aiDefaultMode = m) } }
            )
            SettingsToggleRow(
                title = "Local On-Device Processing Only",
                subtitle = "Keep all media strictly on device; never upload",
                checked = settings.aiLocalProcessingOnly,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiLocalProcessingOnly = v) } }
            )
            SettingsToggleRow(
                title = "AI Stabilization Default",
                subtitle = "Pre-enable gyro + motion stabilization in AI Studio",
                checked = settings.aiAutoStabilize,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiAutoStabilize = v) } }
            )
            SettingsToggleRow(
                title = "AI Denoise & Sharpen Default",
                subtitle = "Edge-preserving YCbCr denoise + halo-free sharpen",
                checked = settings.aiAutoDenoise,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(aiAutoDenoise = v, aiAutoSharpen = v) } }
            )
        }

        // 4. Interface Settings
        SettingsSectionCard("Interface & Polish") {
            SettingsChipGroup(
                label = "Accent Color",
                items = AccentColorPreset.entries,
                selected = settings.accentColorPreset,
                itemLabel = { it.label },
                onSelect = { a -> viewModel.updateSettings { it.copy(accentColorPreset = a) } }
            )
            SettingsToggleRow(
                title = "OLED Deep Black Mode",
                subtitle = "Obsidian dark camera interface",
                checked = settings.darkModeEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(darkModeEnabled = v) } }
            )
            SettingsToggleRow(
                title = "Tactile Haptic Feedback",
                subtitle = "Vibration pulses on shutter, dials, and Live playback",
                checked = settings.hapticFeedbackEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(hapticFeedbackEnabled = v) } }
            )
            SettingsToggleRow(
                title = "UI Motion & Animations",
                subtitle = "Smooth transitions and spring reticles",
                checked = settings.animationsEnabled,
                onCheckedChange = { v -> viewModel.updateSettings { it.copy(animationsEnabled = v) } }
            )
        }

        // 5. Privacy & Permissions
        SettingsSectionCard("Privacy & Permissions") {
            PermissionRow("Camera Permission", camGranted) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
            PermissionRow("Microphone Permission", micGranted) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
            Text(
                text = "Media Access: Zero-permission Android Photo Picker + App-owned MediaStore collection (Google Play compliant).",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MutedSlate
            )
            SettingsToggleRow(
                title = "Optional Location Metadata",
                subtitle = "Attach coarse coordinates to photo EXIF",
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = LocalAccentColor.current)
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
            colors = SwitchDefaults.colors(checkedThumbColor = PureBlack, checkedTrackColor = accent)
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
                    Text(itemLabel(item), style = MonoTelemetrySmall, color = if (isSelected) PureBlack else PureWhite)
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
            text = if (granted) "GRANTED" else "TAP TO CONFIGURE",
            style = MonoTelemetrySmall,
            color = if (granted) LevelGreen else WarningAmber
        )
    }
}
