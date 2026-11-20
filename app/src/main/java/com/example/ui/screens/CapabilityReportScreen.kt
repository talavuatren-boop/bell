package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DeviceCapabilityReport
import com.example.ui.MainViewModel
import com.example.ui.components.formatShutterSpeed
import com.example.ui.theme.CardSlate
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LevelGreen
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

@Composable
fun CapabilityReportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val report by viewModel.capabilities.collectAsStateWithLifecycle()
    var copiedBanner by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("capability_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureWhite)
            }
            Text("Device Capability Report", style = MaterialTheme.typography.titleLarge, color = PureWhite)
            Row {
                IconButton(onClick = { viewModel.refreshCapabilities() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Re-probe Hardware", tint = PureWhite)
                }
                IconButton(
                    onClick = {
                        report?.let { r ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("HUNG CAM Report", buildTextReport(r)))
                            copiedBanner = true
                        }
                    },
                    modifier = Modifier.testTag("capability_copy_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report", tint = accent)
                }
            }
        }

        if (copiedBanner) {
            Text(
                text = "Capability report copied to clipboard",
                style = MonoTelemetrySmall,
                color = LevelGreen,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        val r = report
        if (r == null) {
            BoxLoadingMessage()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReportCard("Hardware & Thermal Telemetry") {
                    ReportLine("Device", "${r.manufacturer} ${r.deviceModel}")
                    ReportLine("Android", "${r.androidVersion} (API ${r.apiLevel})")
                    ReportLine("CPU Cores", "${r.cpuCores} cores")
                    ReportLine("Memory", "${r.availableRamMb} MB free / ${r.totalRamMb} MB total")
                    ReportLine("Thermal State", "${r.thermalStatusLabel} (Level ${r.thermalLevel})")
                    ReportLine("HEVC Hardware Encode", if (r.supportsHevcEncode) "Supported" else "Unavailable")
                }

                ReportCard("Hardware Video Encoders (${r.hardwareVideoEncoders.size})") {
                    if (r.hardwareVideoEncoders.isEmpty()) {
                        Text("No hardware video encoders reported", style = MonoTelemetrySmall, color = MutedSlate)
                    } else {
                        r.hardwareVideoEncoders.forEach { enc ->
                            Text("• $enc", style = MonoTelemetrySmall, color = PureWhite)
                        }
                    }
                }

                r.lenses.forEach { lens ->
                    val facingName = when (lens.lensFacing) {
                        CameraCharacteristics.LENS_FACING_BACK -> "Rear"
                        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                        else -> "External"
                    }
                    val idStr = if (lens.physicalId != null) "ID ${lens.cameraId} (Physical ${lens.physicalId})" else "ID ${lens.cameraId}"

                    ReportCard("$facingName • ${lens.displayLabel} [$idStr]") {
                        ReportLine("Hardware Level", "${lens.hardwareLevelLabel}${if (lens.isLogicalMultiCamera) " • Multi-Cam" else ""}")
                        ReportLine("Focal / 35mm Eq", "${lens.focalLengthMm}mm (${lens.equivalentFocalLengthMm}mm eq)  f/${lens.aperture}")
                        ReportLine("Sensor Physical Size", "${String.format("%.2f", lens.sensorPhysicalWidthMm)} × ${String.format("%.2f", lens.sensorPhysicalHeightMm)} mm")
                        ReportLine("Optical / Zoom Range", "Optic ${lens.opticalZoomRatio}x • ${lens.minZoom}x–${String.format("%.1fx", lens.maxZoom)}")
                        ReportLine("Sensor Orientation", "${lens.sensorOrientation}°")
                        ReportLine("Max Still Resolution", "${lens.maxPhotoWidth} × ${lens.maxPhotoHeight}")
                        ReportLine("Manual Sensor (ISO/SS)", if (lens.supportsManualSensor) "ISO ${lens.isoRange.first}–${lens.isoRange.last} • ${formatShutterSpeed(lens.exposureTimeRangeNs.first)}–${formatShutterSpeed(lens.exposureTimeRangeNs.last)}" else "Unsupported")
                        ReportLine("RAW (DNG)", if (lens.supportsRaw) "Supported" else "Unsupported")
                        ReportLine("HEIC Still", if (lens.supportsHeic) "Supported" else "Unsupported")
                        ReportLine("OIS / EIS / Flash", "OIS:${if (lens.supportsOis) "Yes" else "No"}  EIS:${if (lens.supportsEis) "Yes" else "No"}  Flash:${if (lens.hasFlash) "Yes" else "No"}")
                        ReportLine("Video Matrix", lens.supportedVideoProfiles.joinToString(" | ") { "${it.resolution.label}:${it.supportedFps.joinToString("/")}" })
                        ReportLine("High-Speed Slow-Mo", if (lens.supportedSlowMoModes.isEmpty()) "None" else lens.supportedSlowMoModes.joinToString(", ") { it.label })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BoxLoadingMessage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Probing Camera2 characteristics...", style = MonoTelemetrySmall, color = MutedSlate)
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSlate, RoundedCornerShape(14.dp))
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = LocalAccentColor.current)
        content()
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedSlate)
        Text(value, style = MonoTelemetrySmall, color = PureWhite)
    }
}

private fun buildTextReport(r: DeviceCapabilityReport): String = buildString {
    appendLine("HUNG CAM Device Capability Report")
    appendLine("Device: ${r.manufacturer} ${r.deviceModel} (Android ${r.androidVersion} / API ${r.apiLevel})")
    appendLine("CPU: ${r.cpuCores} cores | RAM: ${r.availableRamMb}/${r.totalRamMb} MB | Thermal: ${r.thermalStatusLabel}")
    appendLine("Encoders: ${r.hardwareVideoEncoders.joinToString()}")
    r.lenses.forEach { l ->
        appendLine("---")
        appendLine("Camera ${l.cameraId} phys=${l.physicalId} (${l.displayLabel}) Level=${l.hardwareLevelLabel}")
        appendLine("Focal=${l.focalLengthMm}mm f/${l.aperture} MaxPhoto=${l.maxPhotoWidth}x${l.maxPhotoHeight} RAW=${l.supportsRaw} HEIC=${l.supportsHeic} OIS=${l.supportsOis} EIS=${l.supportsEis}")
        appendLine("Video=${l.supportedVideoProfiles.joinToString { "${it.resolution.label}:${it.supportedFps}" }} SlowMo=${l.supportedSlowMoModes.map { it.label }}")
    }
}
