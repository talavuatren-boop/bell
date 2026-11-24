package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.MediaAssetEntity
import com.example.model.GalleryCategory
import com.example.model.MediaItemType
import com.example.ui.MainViewModel
import com.example.ui.components.HungCamMotion
import com.example.ui.components.formatShutterSpeed
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonShutter
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalOnAccentColor
import com.example.ui.theme.MonoTelemetrySmall
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onOpenViewer: (List<MediaAssetEntity>, Int) -> Unit,
    onGoToCamera: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenVideoMerge: () -> Unit = {},
    onOpenAiWithAsset: (MediaAssetEntity) -> Unit = {},
    onEditAsset: (MediaAssetEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val category by viewModel.galleryCategory.collectAsStateWithLifecycle()
    val activeAssets by viewModel.activeAssets.collectAsStateWithLifecycle()
    val deletedAssets by viewModel.deletedAssets.collectAsStateWithLifecycle()
    var pendingHardDeleteAsset by remember { mutableStateOf<MediaAssetEntity?>(null) }

    val smoothLowEnd = viewModel.isLowEndDevice || settings.lowEndOptimizedMode

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importExternalUri(uri)
        }
    }

    val displayed = remember(category, activeAssets, deletedAssets) {
        when (category) {
            GalleryCategory.ALL -> activeAssets
            GalleryCategory.PHOTOS -> activeAssets.filter {
                it.typeEnum == MediaItemType.PHOTO || it.typeEnum == MediaItemType.RAW_DNG
            }
            GalleryCategory.VIDEOS -> activeAssets.filter { it.typeEnum == MediaItemType.VIDEO }
            GalleryCategory.LIVE_PHOTOS -> activeAssets.filter { it.typeEnum == MediaItemType.LIVE_PHOTO }
            GalleryCategory.FAVORITES -> activeAssets.filter { it.isFavorite }
            GalleryCategory.RECENTLY_DELETED -> deletedAssets
        }
    }

    val mediaSummary = remember(activeAssets) {
        val photos = activeAssets.count { it.typeEnum == MediaItemType.PHOTO || it.typeEnum == MediaItemType.RAW_DNG }
        val videos = activeAssets.count { it.typeEnum == MediaItemType.VIDEO }
        val lives = activeAssets.count { it.typeEnum == MediaItemType.LIVE_PHOTO }
        buildList {
            if (photos > 0) add("$photos ảnh")
            if (videos > 0) add("$videos video")
            if (lives > 0) add("$lives live")
        }.joinToString(" • ").ifEmpty { "Chưa có phương tiện" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .navigationBarsPadding()
    ) {
        // 1. Editorial Header & Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Thư viện",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
                Text(
                    text = if (category == GalleryCategory.RECENTLY_DELETED) {
                        "${deletedAssets.size} mục trong thùng rác"
                    } else {
                        mediaSummary
                    },
                    style = MonoTelemetrySmall.copy(fontSize = 11.sp),
                    color = MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(ElevatedObsidian)
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(13.dp))
                        .clickable(onClick = onOpenVideoMerge)
                        .padding(horizontal = 10.dp)
                        .testTag("gallery_merge_videos_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MergeType,
                        contentDescription = "Ghép Video",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Ghép",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = PureWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .border(0.75.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(13.dp))
                        .clickable {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                        .testTag("gallery_import_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Nhập từ máy",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(ElevatedObsidian)
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(13.dp))
                        .clickable(onClick = onOpenSettings)
                        .testTag("gallery_settings_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Cài đặt",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Category Pill Tabs
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) accent else ObsidianSurface)
                        .border(
                            width = 0.75.dp,
                            color = if (selected) accent else HairlineBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.selectGalleryCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("gallery_cat_${cat.name.lowercase()}")
                ) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) onAccent else PureWhite.copy(alpha = 0.84f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Content Area: Hero Spotlight + Dense Adaptive Grid OR Polished Empty State
        if (displayed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(ObsidianSurface, CardSlate.copy(alpha = 0.6f))
                            )
                        )
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.2.dp, accent.copy(alpha = 0.38f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (category == GalleryCategory.RECENTLY_DELETED) {
                                Icons.Default.DeleteOutline
                            } else {
                                Icons.Default.PhotoCamera
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (category == GalleryCategory.RECENTLY_DELETED) {
                            "Thùng rác đang trống"
                        } else {
                            "Chưa có mục nào trong ${category.label}"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (category == GalleryCategory.RECENTLY_DELETED) {
                            "Ảnh và video đã xóa sẽ xuất hiện tại đây trước khi bị gỡ vĩnh viễn."
                        } else {
                            "Chụp ảnh HDR, Live Photo 10s hoặc nhập phương tiện sẵn có từ thiết bị."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate,
                        textAlign = TextAlign.Center
                    )
                    if (category != GalleryCategory.RECENTLY_DELETED) {
                        Spacer(modifier = Modifier.height(22.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onGoToCamera,
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(46.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mở Camera", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    pickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(46.dp)
                            ) {
                                Text("Nhập ảnh", color = PureWhite, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            val showHeroSpotlight = category == GalleryCategory.ALL && displayed.isNotEmpty()
            val gridItems = if (showHeroSpotlight) displayed.drop(1) else displayed
            val indexOffset = if (showHeroSpotlight) 1 else 0

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = if (maxWidth >= 600.dp) GridCells.Adaptive(minSize = 140.dp) else GridCells.Fixed(3)
                LazyVerticalGrid(
                    columns = columns,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (showHeroSpotlight) {
                        val featured = displayed.first()
                        item(key = "hero_${featured.id}", span = { GridItemSpan(maxLineSpan) }) {
                            GalleryFeaturedHeroCard(
                                asset = featured,
                                onOpen = { onOpenViewer(displayed, 0) },
                                onEdit = { onEditAsset(featured) },
                                onOpenAi = { onOpenAiWithAsset(featured) }
                            )
                        }
                        if (gridItems.isNotEmpty()) {
                            item(key = "section_all", span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Tất cả khoảnh khắc",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PureWhite
                                    )
                                    Text(
                                        text = "${displayed.size} mục",
                                        style = MonoTelemetrySmall,
                                        color = MutedSlate
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(gridItems, key = { _, item -> item.id }) { idx, item ->
                        val itemMod = if (!smoothLowEnd && settings.animationsEnabled) {
                            Modifier.animateItem(
                                fadeInSpec = tween(180, easing = HungCamMotion.EmphasizedDecelerate),
                                fadeOutSpec = tween(140, easing = HungCamMotion.EmphasizedDecelerate)
                            )
                        } else {
                            Modifier
                        }
                        GalleryGridTile(
                            asset = item,
                            isTrash = category == GalleryCategory.RECENTLY_DELETED,
                            onClick = { onOpenViewer(displayed, idx + indexOffset) },
                            onRestore = { scope.launch { viewModel.mediaRepo.restoreFromTrash(item) } },
                            onHardDelete = { pendingHardDeleteAsset = item },
                            modifier = itemMod
                        )
                    }
                }
            }
        }
    }

    pendingHardDeleteAsset?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingHardDeleteAsset = null },
            containerColor = ElevatedObsidian,
            titleContentColor = PureWhite,
            textContentColor = MutedSlate,
            title = { Text("Bạn có chắc muốn xóa vĩnh viễn?") },
            text = { Text("Mục \"${target.displayName}\" sẽ bị xóa hoàn toàn và không thể khôi phục.") },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = target
                        pendingHardDeleteAsset = null
                        scope.launch { viewModel.mediaRepo.permanentlyDelete(toDelete) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonShutter, contentColor = PureWhite),
                    modifier = Modifier.testTag("gallery_confirm_hard_delete_button")
                ) {
                    Text("Xóa vĩnh viễn")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingHardDeleteAsset = null },
                    modifier = Modifier.testTag("gallery_cancel_hard_delete_button")
                ) {
                    Text("Hủy", color = PureWhite)
                }
            }
        )
    }
}

@Composable
private fun GalleryFeaturedHeroCard(
    asset: MediaAssetEntity,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onOpenAi: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val onAccent = LocalOnAccentColor.current
    val heroRequest = remember(asset.mediaStoreUri, context) {
        ImageRequest.Builder(context)
            .data(asset.mediaStoreUri)
            .size(900)
            .precision(Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build()
    }

    val exifLine = remember(asset) {
        buildList {
            when (asset.typeEnum) {
                MediaItemType.LIVE_PHOTO -> add("LIVE ${asset.liveDurationSec}s")
                MediaItemType.VIDEO -> {
                    val s = (asset.durationMs / 1000L).toInt()
                    add(String.format("VIDEO %d:%02d", s / 60, s % 60))
                }
                MediaItemType.RAW_DNG -> add("RAW DNG")
                MediaItemType.PHOTO -> add("ẢNH")
            }
            if (asset.width > 0 && asset.height > 0) add("${asset.width}×${asset.height}")
            asset.iso?.let { add("ISO $it") }
            asset.exposureTimeNs?.let { add(formatShutterSpeed(it)) }
        }.joinToString(" • ")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(20.dp))
            .background(CardSlate)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .testTag("gallery_item_${asset.id}")
    ) {
        AsyncImage(
            model = heroRequest,
            contentDescription = asset.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle atmospheric dark gradient at top & bottom for crisp readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.35f),
                        0.38f to Color.Transparent,
                        0.62f to Color.Black.copy(alpha = 0.25f),
                        1.0f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )

        // Top-left Spotlight Tag
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.68f))
                .border(0.6.dp, HairlineBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = "GẦN ĐÂY NHẤT",
                style = MonoTelemetrySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = PureWhite
            )
        }

        // Bottom Metadata + Quick Edit / AI Shortcuts
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = asset.lensLabel.ifBlank { "HUNG CAM" },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = exifLine,
                    style = MonoTelemetrySmall.copy(fontSize = 10.5.sp),
                    color = PureWhite.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(11.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Chỉnh sửa", tint = PureWhite, modifier = Modifier.size(14.dp))
                    Text("Sửa", style = MonoTelemetrySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = PureWhite)
                }

                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent)
                        .clickable(onClick = onOpenAi)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Làm nét AI", tint = onAccent, modifier = Modifier.size(14.dp))
                    Text("AI Nét", style = MonoTelemetrySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = onAccent)
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
    onHardDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val thumbRequest = remember(asset.mediaStoreUri, context) {
        ImageRequest.Builder(context)
            .data(asset.mediaStoreUri)
            .size(340)
            .precision(Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(CardSlate)
            .clickable(onClick = onClick)
            .testTag("gallery_item_${asset.id}")
    ) {
        AsyncImage(
            model = thumbRequest,
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
                        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.MotionPhotosOn, contentDescription = null, tint = accent, modifier = Modifier.size(11.dp))
                    Text("LIVE", style = MonoTelemetrySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = accent)
                }
            }
            MediaItemType.VIDEO -> {
                val secs = (asset.durationMs / 1000L).toInt()
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PureWhite, modifier = Modifier.size(12.dp))
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
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(7.dp))
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
                    .size(17.dp)
                    .background(Color.Black.copy(alpha = 0.68f), CircleShape)
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
                    .background(Color.Black.copy(alpha = 0.76f)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onRestore, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.RestoreFromTrash, contentDescription = "Khôi phục", tint = accent, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onHardDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa vĩnh viễn", tint = CrimsonShutter, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
