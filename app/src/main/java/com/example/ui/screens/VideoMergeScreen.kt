package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.MediaAssetEntity
import com.example.model.MediaItemType
import com.example.model.VideoMergeLayout
import com.example.model.VideoResolutionPreset
import com.example.ui.MainViewModel
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LevelGreen
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalOnAccentColor
import com.example.ui.theme.MonoTelemetryLarge
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

private data class MergeClipItem(
    val uri: Uri,
    val label: String,
    val durationMs: Long
)

@Composable
fun VideoMergeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler {
        viewModel.resetMergeState()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetMergeState() }
    }

    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    val assets by viewModel.activeAssets.collectAsStateWithLifecycle()
    val mergeProgress by viewModel.mergeProgress.collectAsStateWithLifecycle()
    val galleryVideos = remember(assets) {
        assets.filter { it.typeEnum == MediaItemType.VIDEO }
    }

    val selectedClips = remember { mutableStateListOf<MergeClipItem>() }
    var selectedLayout by remember { mutableStateOf(VideoMergeLayout.SEQUENTIAL) }
    var targetResolution by remember { mutableStateOf(VideoResolutionPreset.RES_1080P) }
    var targetFps by remember { mutableIntStateOf(30) }
    var includeAudio by remember { mutableStateOf(true) }

    val multiVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        uris.forEachIndexed { idx, uri ->
            if (selectedClips.none { it.uri == uri }) {
                selectedClips.add(
                    MergeClipItem(
                        uri = uri,
                        label = "External Clip ${selectedClips.size + idx + 1}",
                        durationMs = 0L
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(ObsidianSurface, PureBlack)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        viewModel.resetMergeState()
                        onBack()
                    },
                    modifier = Modifier.testTag("video_merge_back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureWhite)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text("Video Merge Studio", style = MaterialTheme.typography.titleLarge, color = PureWhite)
                    Text(
                        text = "Sequential Join & Dual Split-Screen Combine",
                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                        color = accent
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    multiVideoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                enabled = !mergeProgress.isRunning,
                modifier = Modifier.testTag("video_merge_pick_external")
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick Clips")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Gallery quick-add video strip
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Add from HUNG CAM Gallery",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                if (galleryVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedObsidian)
                            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "No videos in HUNG CAM Gallery yet — use 'Pick Clips' above to import videos from your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        galleryVideos.forEach { vid ->
                            GalleryVideoPickTile(
                                asset = vid,
                                isAdded = selectedClips.any { it.uri.toString() == vid.mediaStoreUri },
                                onTap = {
                                    val uri = Uri.parse(vid.mediaStoreUri)
                                    val existingIdx = selectedClips.indexOfFirst { it.uri == uri }
                                    if (existingIdx >= 0) {
                                        selectedClips.removeAt(existingIdx)
                                    } else {
                                        selectedClips.add(
                                            MergeClipItem(
                                                uri = uri,
                                                label = vid.displayName,
                                                durationMs = vid.durationMs
                                            )
                                        )
                                    }
                                    viewModel.performHapticTick()
                                }
                            )
                        }
                    }
                }
            }

            // Layout selector (Sequential, Side-by-Side, Top & Bottom)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Merge & Combine Mode", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                VideoMergeLayout.entries.forEach { layout ->
                    val selected = selectedLayout == layout
                    val icon = when (layout) {
                        VideoMergeLayout.SEQUENTIAL -> Icons.Default.MergeType
                        VideoMergeLayout.SIDE_BY_SIDE -> Icons.Default.ViewColumn
                        VideoMergeLayout.TOP_BOTTOM -> Icons.Default.ViewAgenda
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) accent.copy(alpha = 0.14f) else CardSlate)
                            .border(1.dp, if (selected) accent else HairlineBorder, RoundedCornerShape(14.dp))
                            .clickable(enabled = !mergeProgress.isRunning) {
                                selectedLayout = layout
                                viewModel.performHapticTick()
                            }
                            .padding(12.dp)
                            .testTag("merge_layout_${layout.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) accent else ElevatedObsidian),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) onAccent else accent
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = layout.label,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                color = if (selected) accent else PureWhite
                            )
                            Text(
                                text = layout.subtitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MutedSlate
                            )
                        }
                    }
                }
            }

            // Selected Timeline / Dual Slot Queue
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLayout == VideoMergeLayout.SEQUENTIAL) {
                            "Clip Sequence (${selectedClips.size} selected)"
                        } else {
                            "Dual Frame Slots (${selectedClips.size.coerceAtMost(2)}/2 required)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite
                    )
                    if (selectedClips.isNotEmpty() && !mergeProgress.isRunning) {
                        Text(
                            text = "Clear All",
                            style = MonoTelemetrySmall,
                            color = CrimsonShutter,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedClips.clear() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (selectedClips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElevatedObsidian)
                            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Splitscreen, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Select at least 2 videos to merge or combine",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSlate
                            )
                        }
                    }
                } else {
                    selectedClips.forEachIndexed { idx, clip ->
                        val slotBadge = when {
                            selectedLayout == VideoMergeLayout.SEQUENTIAL -> "#${idx + 1}"
                            idx == 0 -> if (selectedLayout == VideoMergeLayout.SIDE_BY_SIDE) "LEFT" else "TOP"
                            idx == 1 -> if (selectedLayout == VideoMergeLayout.SIDE_BY_SIDE) "RIGHT" else "BOTTOM"
                            else -> "EXTRA (IGNORED)"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardSlate)
                                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElevatedObsidian)
                            ) {
                                AsyncImage(
                                    model = clip.uri,
                                    contentDescription = clip.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = slotBadge,
                                    style = MonoTelemetrySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = if (selectedLayout != VideoMergeLayout.SEQUENTIAL && idx >= 2) MutedSlate else accent
                                )
                                Text(
                                    text = clip.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (clip.durationMs > 0) {
                                    val secs = clip.durationMs / 1000L
                                    Text(
                                        text = String.format("%d:%02d", secs / 60, secs % 60),
                                        style = MonoTelemetrySmall.copy(fontSize = 10.sp),
                                        color = MutedSlate
                                    )
                                }
                            }
                            if (!mergeProgress.isRunning) {
                                if (idx > 0) {
                                    IconButton(
                                        onClick = {
                                            val item = selectedClips.removeAt(idx)
                                            selectedClips.add(idx - 1, item)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = PureWhite, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (idx < selectedClips.lastIndex) {
                                    IconButton(
                                        onClick = {
                                            val item = selectedClips.removeAt(idx)
                                            selectedClips.add(idx + 1, item)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = PureWhite, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { selectedClips.removeAt(idx) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Clip", tint = CrimsonShutter, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Export Settings (Resolution, FPS, Audio)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSlate)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Export Configuration", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoResolutionPreset.entries.forEach { res ->
                        val selected = targetResolution == res
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) accent else ElevatedObsidian)
                                .clickable(enabled = !mergeProgress.isRunning) { targetResolution = res }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(res.label, style = MonoTelemetrySmall, color = if (selected) onAccent else PureWhite)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(24, 30, 60).forEach { fps ->
                        val selected = targetFps == fps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) accent else ElevatedObsidian)
                                .clickable(enabled = !mergeProgress.isRunning) { targetFps = fps }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${fps} FPS", style = MonoTelemetrySmall, color = if (selected) onAccent else PureWhite)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Preserve Primary Audio", style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                        Text("Muxes original audio stream when present", style = MonoTelemetrySmall.copy(fontSize = 10.sp), color = MutedSlate)
                    }
                    Switch(
                        checked = includeAudio,
                        onCheckedChange = { if (!mergeProgress.isRunning) includeAudio = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccent, checkedTrackColor = accent)
                    )
                }
            }

            // Progress / Result Status Card
            if (mergeProgress.isRunning || mergeProgress.errorMessage != null || mergeProgress.summary != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardSlate)
                        .border(
                            1.dp,
                            when {
                                mergeProgress.errorMessage != null -> CrimsonShutter
                                mergeProgress.summary != null -> LevelGreen
                                else -> accent
                            },
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mergeProgress.errorMessage ?: mergeProgress.summary ?: mergeProgress.stage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                mergeProgress.errorMessage != null -> CrimsonShutter
                                mergeProgress.summary != null -> LevelGreen
                                else -> PureWhite
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (mergeProgress.isRunning) {
                            Text("${mergeProgress.percent}%", style = MonoTelemetryLarge, color = accent)
                        } else if (mergeProgress.summary != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LevelGreen)
                        }
                    }
                    if (mergeProgress.isRunning) {
                        LinearProgressIndicator(
                            progress = { (mergeProgress.percent / 100f).coerceIn(0f, 1f) },
                            color = accent,
                            trackColor = ElevatedObsidian,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        )
                        OutlinedButton(
                            onClick = { viewModel.cancelVideoMerge() },
                            modifier = Modifier.align(Alignment.End).testTag("video_merge_cancel")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Bottom Export CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    val uris = if (selectedLayout == VideoMergeLayout.SEQUENTIAL) {
                        selectedClips.map { it.uri }
                    } else {
                        selectedClips.take(2).map { it.uri }
                    }
                    viewModel.mergeVideos(
                        sourceUris = uris,
                        layout = selectedLayout,
                        resolution = targetResolution,
                        fps = targetFps,
                        includeAudio = includeAudio
                    )
                },
                enabled = selectedClips.size >= 2 && !mergeProgress.isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("video_merge_export_button")
            ) {
                Icon(Icons.Default.MovieCreation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedClips.size < 2) {
                        "Select 2+ Clips to Merge"
                    } else {
                        "Export ${selectedLayout.label}"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun GalleryVideoPickTile(
    asset: MediaAssetEntity,
    isAdded: Boolean,
    onTap: () -> Unit
) {
    val accent = LocalAccentColor.current
    val secs = (asset.durationMs / 1000L).toInt()
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 84.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isAdded) 2.dp else 1.dp,
                color = if (isAdded) accent else HairlineBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onTap)
            .testTag("merge_gallery_pick_${asset.id}")
    ) {
        AsyncImage(
            model = asset.mediaStoreUri,
            contentDescription = asset.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isAdded) accent.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.25f))
        )
        Text(
            text = String.format("%d:%02d", secs / 60, secs % 60),
            style = MonoTelemetrySmall.copy(fontSize = 9.sp),
            color = PureWhite,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        if (isAdded) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .background(PureBlack, CircleShape)
            )
        }
    }
}
