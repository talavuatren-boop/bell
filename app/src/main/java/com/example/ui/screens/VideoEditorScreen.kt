package com.example.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.MediaAssetEntity
import com.example.model.VideoEditConfig
import com.example.model.VideoResolutionPreset
import com.example.ui.MainViewModel
import com.example.ui.theme.CardSlate
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

@Composable
fun VideoEditorScreen(
    asset: MediaAssetEntity,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val accent = LocalAccentColor.current
    val totalMs = asset.durationMs.coerceAtLeast(1000L)

    var config by remember {
        mutableStateOf(
            VideoEditConfig(
                trimStartMs = 0L,
                trimEndMs = totalMs,
                targetResolution = VideoResolutionPreset.RES_1080P,
                targetFps = asset.fps.takeIf { it in listOf(24, 30, 60) } ?: 30
            )
        )
    }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var exportPercent by remember { mutableStateOf(0) }
    var exportStage by remember { mutableStateOf("") }

    LaunchedEffect(config.muteAudio, config.audioVolume, player) {
        val v = if (config.muteAudio) 0f else config.audioVolume
        runCatching { player?.setVolume(v, v) }
    }

    DisposableEffect(asset.mediaStoreUri) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !isExporting, modifier = Modifier.testTag("video_editor_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureWhite)
            }
            Text("Video Editor", style = MaterialTheme.typography.titleLarge, color = PureWhite)
            Button(
                onClick = {
                    isExporting = true
                    viewModel.exportEditedVideo(
                        sourceAsset = asset,
                        config = config,
                        onProgress = { pct, stage ->
                            exportPercent = pct
                            exportStage = stage
                        },
                        onFinished = { msg ->
                            isExporting = false
                            exportStage = msg
                            onBack()
                        }
                    )
                },
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = PureBlack),
                modifier = Modifier.testTag("video_editor_export")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Export Copy")
            }
        }

        // Video Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ElevatedObsidian),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                val mp = MediaPlayer().apply {
                                    setDataSource(ctx, Uri.parse(asset.mediaStoreUri))
                                    setSurface(Surface(st))
                                    isLooping = true
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
            IconButton(
                onClick = {
                    val mp = player ?: return@IconButton
                    if (mp.isPlaying) {
                        mp.pause(); isPlaying = false
                    } else {
                        mp.start(); isPlaying = true
                    }
                },
                modifier = Modifier.background(PureBlack.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = PureWhite)
            }
        }

        if (isExporting) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("$exportStage ($exportPercent%)", style = MonoTelemetrySmall, color = accent)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (exportPercent / 100f).coerceIn(0f, 1f) },
                    color = accent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Trim Selection
            Text(
                text = "Trim (${config.trimStartMs / 1000f}s – ${config.trimEndMs / 1000f}s)",
                style = MonoTelemetrySmall,
                color = PureWhite
            )
            RangeSlider(
                value = config.trimStartMs.toFloat()..config.trimEndMs.toFloat(),
                onValueChange = { range ->
                    if (range.endInclusive - range.start >= 300f) {
                        config = config.copy(
                            trimStartMs = range.start.toLong(),
                            trimEndMs = range.endInclusive.toLong()
                        )
                    }
                },
                valueRange = 0f..totalMs.toFloat(),
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                modifier = Modifier.testTag("video_trim_slider")
            )

            // Resolution & FPS Export Settings
            Text("Export Resolution & Frame Rate", style = MaterialTheme.typography.titleMedium, color = PureWhite)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoResolutionPreset.entries.forEach { res ->
                    val selected = config.targetResolution == res
                    Text(
                        text = res.label,
                        style = MonoTelemetrySmall,
                        color = if (selected) PureBlack else PureWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accent else CardSlate)
                            .clickable { config = config.copy(targetResolution = res) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                listOf(24, 30, 60).forEach { fps ->
                    val selected = config.targetFps == fps
                    Text(
                        text = "${fps}fps",
                        style = MonoTelemetrySmall,
                        color = if (selected) PureBlack else PureWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accent else CardSlate)
                            .clickable { config = config.copy(targetFps = fps) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            // Audio Volume & Mute
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { config = config.copy(muteAudio = !config.muteAudio) }) {
                    Icon(
                        imageVector = if (config.muteAudio) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute",
                        tint = if (config.muteAudio) MutedSlate else accent
                    )
                }
                Slider(
                    value = if (config.muteAudio) 0f else config.audioVolume,
                    onValueChange = { config = config.copy(muteAudio = false, audioVolume = it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                    modifier = Modifier.weight(1f)
                )
            }

            // Color Adjustment Sliders
            Text("Color & Exposure", style = MaterialTheme.typography.titleMedium, color = PureWhite)
            LabeledVideoSlider("Brightness", config.brightness, -0.4f..0.4f) { config = config.copy(brightness = it) }
            LabeledVideoSlider("Contrast", config.contrast, 0.7f..1.5f) { config = config.copy(contrast = it) }
            LabeledVideoSlider("Saturation", config.saturation, 0f..1.8f) { config = config.copy(saturation = it) }
            LabeledVideoSlider("Temp", config.temperature, -0.6f..0.6f) { config = config.copy(temperature = it) }

            // AI Stabilization, Denoise, Sharpen, Color Toggles
            Text("AI Processing Pipeline", style = MaterialTheme.typography.titleMedium, color = PureWhite)
            AiCheckboxRow("AI Gyro + Motion Stabilization", config.applyStabilization) {
                config = config.copy(applyStabilization = it)
            }
            AiCheckboxRow("AI Temporal Denoise", config.applyDenoise) {
                config = config.copy(applyDenoise = it)
            }
            AiCheckboxRow("AI Edge Sharpening", config.applySharpen) {
                config = config.copy(applySharpen = it)
            }
            AiCheckboxRow("AI Color Correction", config.applyAiColor) {
                config = config.copy(applyAiColor = it)
            }
        }
    }
}

@Composable
private fun LabeledVideoSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val accent = LocalAccentColor.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MonoTelemetrySmall, color = MutedSlate, modifier = Modifier.size(width = 80.dp, height = 18.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AiCheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val accent = LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardSlate)
            .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PureWhite)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = accent, checkmarkColor = PureBlack)
        )
    }
}
