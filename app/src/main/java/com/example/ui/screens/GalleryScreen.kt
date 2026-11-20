package com.example.ui.screens

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.MediaAssetEntity
import com.example.model.GalleryCategory
import com.example.model.MediaItemType
import com.example.ui.MainViewModel
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onOpenViewer: (List<MediaAssetEntity>, Int) -> Unit,
    onGoToCamera: () -> Unit,
    onOpenVideoMerge: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val scope = rememberCoroutineScope()
    val category by viewModel.galleryCategory.collectAsStateWithLifecycle()
    val activeAssets by viewModel.activeAssets.collectAsStateWithLifecycle()
    val deletedAssets by viewModel.deletedAssets.collectAsStateWithLifecycle()

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importExternalUri(uri)
        }
    }

    val displayed = when (category) {
        GalleryCategory.ALL -> activeAssets
        GalleryCategory.PHOTOS -> activeAssets.filter {
            it.typeEnum == MediaItemType.PHOTO || it.typeEnum == MediaItemType.RAW_DNG
        }
        GalleryCategory.VIDEOS -> activeAssets.filter { it.typeEnum == MediaItemType.VIDEO }
        GalleryCategory.LIVE_PHOTOS -> activeAssets.filter { it.typeEnum == MediaItemType.LIVE_PHOTO }
        GalleryCategory.FAVORITES -> activeAssets.filter { it.isFavorite }
        GalleryCategory.RECENTLY_DELETED -> deletedAssets
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Gallery",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite
                )
                Text(
                    text = "${displayed.size} items • ${category.label}",
                    style = MonoTelemetrySmall,
                    color = MutedSlate
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenVideoMerge,
                    modifier = Modifier
                        .background(ElevatedObsidian, CircleShape)
                        .border(1.dp, HairlineBorder, CircleShape)
                        .testTag("gallery_merge_videos_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MergeType,
                        contentDescription = "Merge & Combine Videos",
                        tint = PureWhite
                    )
                }
                IconButton(
                    onClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier
                        .background(ElevatedObsidian, CircleShape)
                        .border(1.dp, HairlineBorder, CircleShape)
                        .testTag("gallery_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Import from Photo Picker",
                        tint = accent
                    )
                }
            }
        }

        // Categories Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GalleryCategory.entries.forEach { cat ->
                val selected = category == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) accent else ElevatedObsidian)
                        .border(1.dp, if (selected) accent else HairlineBorder, RoundedCornerShape(16.dp))
                        .clickable { viewModel.selectGalleryCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("gallery_cat_${cat.name.lowercase()}")
                ) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = if (selected) PureBlack else PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayed.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = MutedSlate,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (category == GalleryCategory.RECENTLY_DELETED) "Trash is empty" else "No captures in ${category.label}",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Capture photos, Live Photos, or videos with HUNG CAM, or import media using the Photo Picker.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (category != GalleryCategory.RECENTLY_DELETED) {
                    Button(
                        onClick = onGoToCamera,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = PureBlack)
                    ) {
                        Text("Open Camera")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(displayed, key = { _, item -> item.id }) { index, item ->
                    GalleryGridTile(
                        asset = item,
                        isTrash = category == GalleryCategory.RECENTLY_DELETED,
                        onClick = { onOpenViewer(displayed, index) },
                        onRestore = { scope.launch { viewModel.mediaRepo.restoreFromTrash(item) } },
                        onHardDelete = { scope.launch { viewModel.mediaRepo.permanentlyDelete(item) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryGridTile(
    asset: MediaAssetEntity,
    isTrash: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onHardDelete: () -> Unit
) {
    val accent = LocalAccentColor.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(CardSlate)
            .clickable(onClick = onClick)
            .testTag("gallery_item_${asset.id}")
    ) {
        AsyncImage(
            model = asset.mediaStoreUri,
            contentDescription = asset.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top-left type badge (LIVE / VIDEO / RAW)
        when (asset.typeEnum) {
            MediaItemType.LIVE_PHOTO -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.MotionPhotosOn, contentDescription = null, tint = accent, modifier = Modifier.size(11.dp))
                    Text("LIVE", style = MonoTelemetrySmall.copy(fontSize = 9.sp), color = accent)
                }
            }
            MediaItemType.VIDEO -> {
                val secs = (asset.durationMs / 1000L).toInt()
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = PureWhite, modifier = Modifier.size(11.dp))
                    Text(String.format("%d:%02d", secs / 60, secs % 60), style = MonoTelemetrySmall.copy(fontSize = 9.sp), color = PureWhite)
                }
            }
            MediaItemType.RAW_DNG -> {
                Text(
                    text = "DNG",
                    style = MonoTelemetrySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            else -> Unit
        }

        if (asset.aiEnhancementTag != null) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Enhanced",
                tint = accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(2.dp)
            )
        }

        if (asset.isFavorite && !isTrash) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = CrimsonShutter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(16.dp)
            )
        }

        if (isTrash) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onRestore, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore", tint = accent, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onHardDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Permanently", tint = CrimsonShutter, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
