package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CameraLensInfo
import com.example.model.ProCameraParameters
import com.example.model.ProCaptureType
import com.example.model.WhiteBalancePreset
import com.example.ui.theme.CardSlate
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalOnAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureWhite
import kotlin.math.roundToInt

private enum class ProControlTab(val label: String) {
    ISO("ISO"),
    SHUTTER("SHUTTER"),
    EV("EV"),
    WB("WB"),
    FOCUS("FOCUS")
}

@Composable
fun ProControlDeck(
    lens: CameraLensInfo?,
    params: ProCameraParameters,
    captureType: ProCaptureType,
    onSelectCaptureType: (ProCaptureType) -> Unit,
    liveIso: Int?,
    liveExposureNs: Long?,
    onUpdate: ((ProCameraParameters) -> ProCameraParameters) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var selectedTab by remember { mutableStateOf(ProControlTab.ISO) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
            .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Live telemetry readout + PRO Photo/Video switch + capability-gated assist chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProToggleChip(
                label = "ẢNH",
                active = captureType == ProCaptureType.PHOTO,
                onClick = { onSelectCaptureType(ProCaptureType.PHOTO) },
                tag = "pro_mode_photo_chip"
            )
            ProToggleChip(
                label = "VIDEO",
                active = captureType == ProCaptureType.VIDEO,
                onClick = { onSelectCaptureType(ProCaptureType.VIDEO) },
                tag = "pro_mode_video_chip"
            )
            val dispIso = if (params.autoExposure) (liveIso ?: params.iso) else params.iso
            val dispExp = if (params.autoExposure) (liveExposureNs ?: params.exposureTimeNs) else params.exposureTimeNs
            Text(
                text = "ISO $dispIso • ${formatShutterSpeed(dispExp)}",
                style = MonoTelemetrySmall,
                color = accent,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            ProToggleChip(
                label = "PEAK",
                active = params.focusPeakingEnabled,
                onClick = { onUpdate { it.copy(focusPeakingEnabled = !it.focusPeakingEnabled) } },
                tag = "pro_focus_peaking_chip"
            )
            ProToggleChip(
                label = "WARN",
                active = params.exposureWarningEnabled,
                onClick = { onUpdate { it.copy(exposureWarningEnabled = !it.exposureWarningEnabled) } },
                tag = "pro_exposure_warning_chip"
            )
            if (captureType == ProCaptureType.PHOTO && lens?.supportsRaw == true) {
                ProToggleChip(
                    label = "RAW",
                    active = params.captureRawDng,
                    onClick = { onUpdate { it.copy(captureRawDng = !it.captureRawDng) } },
                    tag = "pro_raw_chip"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Parameter dial selector tabs (only expose hardware-supported controls)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ProControlTab.entries.forEach { tab ->
                val enabled = when (tab) {
                    ProControlTab.ISO, ProControlTab.SHUTTER -> lens?.supportsManualSensor == true
                    ProControlTab.FOCUS -> (lens?.minFocusDistance ?: 0f) > 0f
                    else -> true
                }
                val isSelected = selectedTab == tab
                val valueStr = when (tab) {
                    ProControlTab.ISO -> if (params.autoExposure) "AUTO" else "${params.iso}"
                    ProControlTab.SHUTTER -> if (params.autoExposure) "AUTO" else formatShutterSpeed(params.exposureTimeNs)
                    ProControlTab.EV -> String.format("%+.1f", params.evSteps * (lens?.evStep ?: 0.33f))
                    ProControlTab.WB -> if (params.whiteBalance == WhiteBalancePreset.MANUAL) "${params.manualKelvin}K" else params.whiteBalance.label
                    ProControlTab.FOCUS -> if (params.autoFocus) "AF" else String.format("%.2fd", params.focusDistance)
                }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CardSlate else Color.Transparent)
                        .clickable(enabled = enabled) { selectedTab = tab }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab.label,
                        style = MonoTelemetrySmall.copy(fontSize = 9.sp),
                        color = if (!enabled) MutedSlate.copy(alpha = 0.4f) else if (isSelected) accent else MutedSlate
                    )
                    Text(
                        text = if (enabled) valueStr else "N/A",
                        style = MonoTelemetrySmall.copy(fontSize = 11.sp),
                        color = if (!enabled) MutedSlate.copy(alpha = 0.4f) else PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Active slider/preset panel
        when (selectedTab) {
            ProControlTab.ISO -> {
                val range = lens?.isoRange ?: 100..3200
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProToggleChip(
                        label = "AUTO",
                        active = params.autoExposure,
                        onClick = { onUpdate { it.copy(autoExposure = !it.autoExposure) } },
                        tag = "pro_ae_toggle"
                    )
                    Slider(
                        value = params.iso.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
                        onValueChange = { v ->
                            onUpdate { it.copy(autoExposure = false, iso = v.roundToInt()) }
                        },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier.weight(1f).testTag("pro_iso_slider")
                    )
                }
            }
            ProControlTab.SHUTTER -> {
                val minNs = (lens?.exposureTimeRangeNs?.first ?: 125_000L).coerceAtLeast(100_000L)
                val maxNs = (lens?.exposureTimeRangeNs?.last ?: 1_000_000_000L).coerceAtMost(2_000_000_000L)
                val logMin = kotlin.math.ln(minNs.toDouble()).toFloat()
                val logMax = kotlin.math.ln(maxNs.toDouble()).toFloat()
                val currentLog = kotlin.math.ln(params.exposureTimeNs.coerceIn(minNs, maxNs).toDouble()).toFloat()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProToggleChip(
                        label = "AUTO",
                        active = params.autoExposure,
                        onClick = { onUpdate { it.copy(autoExposure = !it.autoExposure) } },
                        tag = "pro_shutter_ae_toggle"
                    )
                    Slider(
                        value = currentLog,
                        onValueChange = { lv ->
                            val ns = kotlin.math.exp(lv.toDouble()).toLong().coerceIn(minNs, maxNs)
                            onUpdate { it.copy(autoExposure = false, exposureTimeNs = ns) }
                        },
                        valueRange = logMin..logMax,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier.weight(1f).testTag("pro_shutter_slider")
                    )
                }
            }
            ProControlTab.EV -> {
                val evRange = lens?.evRange ?: -6..6
                Slider(
                    value = params.evSteps.toFloat().coerceIn(evRange.first.toFloat(), evRange.last.toFloat()),
                    onValueChange = { v -> onUpdate { it.copy(autoExposure = true, evSteps = v.roundToInt()) } },
                    valueRange = evRange.first.toFloat()..evRange.last.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                    modifier = Modifier.fillMaxWidth().testTag("pro_ev_slider")
                )
            }
            ProControlTab.WB -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WhiteBalancePreset.entries.forEach { preset ->
                            ProToggleChip(
                                label = preset.label,
                                active = params.whiteBalance == preset,
                                onClick = { onUpdate { it.copy(whiteBalance = preset) } },
                                tag = "pro_wb_${preset.name.lowercase()}"
                            )
                        }
                    }
                    if (params.whiteBalance == WhiteBalancePreset.MANUAL) {
                        Slider(
                            value = params.manualKelvin.toFloat(),
                            onValueChange = { k -> onUpdate { it.copy(manualKelvin = (k / 100f).roundToInt() * 100) } },
                            valueRange = 2300f..8500f,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                            modifier = Modifier.fillMaxWidth().testTag("pro_kelvin_slider")
                        )
                    }
                }
            }
            ProControlTab.FOCUS -> {
                val maxDiopters = (lens?.minFocusDistance ?: 10f).coerceAtLeast(0.5f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProToggleChip(
                        label = "AF",
                        active = params.autoFocus,
                        onClick = { onUpdate { it.copy(autoFocus = !it.autoFocus) } },
                        tag = "pro_af_toggle"
                    )
                    Slider(
                        value = params.focusDistance.coerceIn(0f, maxDiopters),
                        onValueChange = { d -> onUpdate { it.copy(autoFocus = false, focusDistance = d) } },
                        valueRange = 0f..maxDiopters,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier.weight(1f).testTag("pro_focus_slider")
                    )
                }
            }
        }
    }
}

@Composable
private fun ProToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accent else ElevatedObsidian)
            .border(0.75.dp, if (active) accent else HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MonoTelemetrySmall.copy(fontSize = 10.sp),
            color = if (active) onAccent else PureWhite
        )
    }
}

fun formatShutterSpeed(ns: Long): String {
    if (ns <= 0L) return "Auto"
    val sec = ns / 1_000_000_000.0
    return if (sec >= 0.4) {
        String.format("%.1fs", sec)
    } else {
        val denom = (1.0 / sec).roundToInt().coerceAtLeast(1)
        "1/${denom}s"
    }
}
