package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MediaAssetEntity
import com.example.model.MainNavDestination
import com.example.ui.components.springPressScale
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.CapabilityReportScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoEditorScreen
import com.example.ui.screens.VideoMergeScreen
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HungCamTheme
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalOnAccentColor
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

private sealed interface SubScreen {
    data object None : SubScreen
    data object Settings : SubScreen
    data class Viewer(val assets: List<MediaAssetEntity>, val index: Int) : SubScreen
    data class PhotoEdit(val asset: MediaAssetEntity, val openStickersMeme: Boolean = false) : SubScreen
    data class VideoEdit(val asset: MediaAssetEntity) : SubScreen
    data object VideoMerge : SubScreen
    data object CapabilityReport : SubScreen
}

@Composable
fun HungCamApp(
    viewModel: MainViewModel,
    onDestinationChanged: (MainNavDestination) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editorNotice by viewModel.editorExportNotice.collectAsStateWithLifecycle()
    val capturedMemeAsset by viewModel.capturedMemeAsset.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainNavDestination.CAMERA) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentTab, subScreen) {
        onDestinationChanged(if (subScreen == SubScreen.None) currentTab else MainNavDestination.GALLERY)
    }

    LaunchedEffect(capturedMemeAsset) {
        capturedMemeAsset?.let { asset ->
            subScreen = SubScreen.PhotoEdit(asset, openStickersMeme = true)
            viewModel.consumeCapturedMemeAsset()
        }
    }

    LaunchedEffect(editorNotice) {
        editorNotice?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearEditorNotice()
        }
    }

    HungCamTheme(
        darkTheme = settings.darkModeEnabled,
        accentPreset = settings.accentColorPreset
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.example.ui.components.LocalAnimationsEnabled provides settings.animationsEnabled
        ) {
            val accent = LocalAccentColor.current
            val onAccent = LocalOnAccentColor.current

            androidx.compose.animation.AnimatedContent(
                targetState = subScreen,
                transitionSpec = {
                    val forward = targetState != SubScreen.None
                    com.example.ui.components.HungCamMotion.screenTransition(
                        forward = forward,
                        animationsEnabled = settings.animationsEnabled
                    )
                },
                label = "subScreenTransition"
            ) { sub ->
                when (sub) {
                    is SubScreen.Viewer -> {
                        val activeAssets by viewModel.activeAssets.collectAsStateWithLifecycle()
                        val deletedAssets by viewModel.deletedAssets.collectAsStateWithLifecycle()
                        val viewerIds = remember(sub.assets) { sub.assets.map { it.id }.toSet() }
                        val fromTrash = remember(sub.assets) { sub.assets.firstOrNull()?.isDeleted == true }
                        val liveAssets = remember(activeAssets, deletedAssets, viewerIds, fromTrash) {
                            val pool = if (fromTrash) deletedAssets else activeAssets
                            val filtered = pool.filter { it.id in viewerIds }
                            filtered.ifEmpty { emptyList() }
                        }
                        val startId = remember(sub.assets, sub.index) {
                            sub.assets.getOrNull(sub.index)?.id
                        }
                        val syncedAssets = if (liveAssets.isEmpty() && activeAssets.isEmpty() && deletedAssets.isEmpty()) {
                            sub.assets
                        } else {
                            liveAssets
                        }
                        val resolvedIndex = syncedAssets.indexOfFirst { it.id == startId }
                            .takeIf { it >= 0 } ?: sub.index.coerceIn(0, (syncedAssets.size - 1).coerceAtLeast(0))

                        MediaViewerScreen(
                            assets = syncedAssets,
                            initialIndex = resolvedIndex,
                            viewModel = viewModel,
                            onBack = { subScreen = SubScreen.None },
                            onOpenPhotoEditor = { asset -> subScreen = SubScreen.PhotoEdit(asset) },
                            onOpenVideoEditor = { asset -> subScreen = SubScreen.VideoEdit(asset) },
                            onOpenAiStudio = { asset ->
                                viewModel.selectAssetForAiStudio(asset)
                                subScreen = SubScreen.None
                                currentTab = MainNavDestination.AI_STUDIO
                            }
                        )
                    }
                    is SubScreen.PhotoEdit -> {
                        PhotoEditorScreen(
                            asset = sub.asset,
                            viewModel = viewModel,
                            onBack = { subScreen = SubScreen.None },
                            openStickersMemeInitially = sub.openStickersMeme
                        )
                    }
                    is SubScreen.VideoEdit -> {
                        VideoEditorScreen(
                            asset = sub.asset,
                            viewModel = viewModel,
                            onBack = { subScreen = SubScreen.None }
                        )
                    }
                    is SubScreen.VideoMerge -> {
                        VideoMergeScreen(
                            viewModel = viewModel,
                            onBack = { subScreen = SubScreen.None }
                        )
                    }
                    is SubScreen.Settings -> {
                        androidx.activity.compose.BackHandler { subScreen = SubScreen.None }
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { subScreen = SubScreen.None },
                            onOpenCapabilityReport = { subScreen = SubScreen.CapabilityReport },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is SubScreen.CapabilityReport -> {
                        CapabilityReportScreen(
                            viewModel = viewModel,
                            onBack = { subScreen = SubScreen.Settings }
                        )
                    }
                    SubScreen.None -> {
                        androidx.activity.compose.BackHandler(enabled = currentTab != MainNavDestination.CAMERA) {
                            currentTab = MainNavDestination.CAMERA
                        }
                        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(PureBlack)) {
                            val isCameraTab = currentTab == MainNavDestination.CAMERA
                            val useRail = maxWidth >= 600.dp && !isCameraTab

                            Scaffold(
                                containerColor = PureBlack,
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                topBar = {
                                    if (!isCameraTab && !useRail) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(PureBlack)
                                                .statusBarsPadding()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(ObsidianSurface)
                                                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(18.dp))
                                                    .padding(4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                MainNavDestination.entries.forEach { dest ->
                                                    val selected = currentTab == dest
                                                    val interaction = remember(dest) { MutableInteractionSource() }
                                                    Row(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(46.dp)
                                                            .springPressScale(interaction, pressedScale = 0.92f)
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(
                                                                if (selected) accent else androidx.compose.ui.graphics.Color.Transparent
                                                            )
                                                            .clickable(
                                                                interactionSource = interaction,
                                                                indication = null
                                                            ) {
                                                                currentTab = dest
                                                                viewModel.performHapticTick()
                                                            }
                                                            .padding(horizontal = 8.dp)
                                                            .testTag("nav_${dest.route}"),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = navIconFor(dest, selected),
                                                            contentDescription = dest.label,
                                                            tint = if (selected) onAccent else MutedSlate,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = dest.label,
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = 12.5.sp,
                                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                            ),
                                                            color = if (selected) onAccent else PureWhite.copy(alpha = 0.8f),
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .then(if (useRail) Modifier.statusBarsPadding() else Modifier)
                                ) {
                                    if (useRail) {
                                        NavigationRail(
                                            containerColor = ObsidianSurface,
                                            contentColor = PureWhite,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .windowInsetsPadding(WindowInsets.navigationBars)
                                        ) {
                                            MainNavDestination.entries.forEach { dest ->
                                                val selected = currentTab == dest
                                                NavigationRailItem(
                                                    selected = selected,
                                                    onClick = {
                                                        currentTab = dest
                                                        viewModel.performHapticTick()
                                                    },
                                                    icon = {
                                                        Icon(
                                                            imageVector = navIconFor(dest, selected),
                                                            contentDescription = dest.label
                                                        )
                                                    },
                                                    label = { Text(dest.label) },
                                                    colors = NavigationRailItemDefaults.colors(
                                                        selectedIconColor = onAccent,
                                                        selectedTextColor = PureWhite,
                                                        indicatorColor = accent,
                                                        unselectedIconColor = MutedSlate,
                                                        unselectedTextColor = MutedSlate
                                                    ),
                                                    modifier = Modifier.testTag("nav_rail_${dest.route}")
                                                )
                                            }
                                        }
                                    }

                                    androidx.compose.animation.AnimatedContent(
                                        targetState = currentTab,
                                        transitionSpec = {
                                            val forward = targetState.ordinal >= initialState.ordinal
                                            com.example.ui.components.HungCamMotion.screenTransition(
                                                forward = forward,
                                                animationsEnabled = settings.animationsEnabled
                                            )
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        label = "mainTabTransition"
                                    ) { tab ->
                                        when (tab) {
                                            MainNavDestination.CAMERA -> CameraScreen(
                                                viewModel = viewModel,
                                                onOpenGallery = { currentTab = MainNavDestination.GALLERY },
                                                onOpenSettings = { subScreen = SubScreen.Settings },
                                                onOpenCapabilityReport = { subScreen = SubScreen.CapabilityReport },
                                                onOpenStickersMeme = { asset ->
                                                    subScreen = SubScreen.PhotoEdit(asset, openStickersMeme = true)
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            MainNavDestination.GALLERY -> GalleryScreen(
                                                viewModel = viewModel,
                                                onOpenViewer = { list, idx -> subScreen = SubScreen.Viewer(list, idx) },
                                                onGoToCamera = { currentTab = MainNavDestination.CAMERA },
                                                onOpenSettings = { subScreen = SubScreen.Settings },
                                                onOpenVideoMerge = { subScreen = SubScreen.VideoMerge },
                                                onOpenAiWithAsset = { asset ->
                                                    viewModel.selectAssetForAiStudio(asset)
                                                    currentTab = MainNavDestination.AI_STUDIO
                                                },
                                                onEditAsset = { asset ->
                                                    if (asset.typeEnum == com.example.model.MediaItemType.VIDEO) {
                                                        subScreen = SubScreen.VideoEdit(asset)
                                                    } else {
                                                        subScreen = SubScreen.PhotoEdit(asset)
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            MainNavDestination.AI_STUDIO -> AiStudioScreen(
                                                viewModel = viewModel,
                                                onOpenVideoMerge = { subScreen = SubScreen.VideoMerge },
                                                onOpenStickersMeme = { asset ->
                                                    subScreen = SubScreen.PhotoEdit(asset, openStickersMeme = true)
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun navIconFor(dest: MainNavDestination, selected: Boolean) = when (dest) {
    MainNavDestination.CAMERA -> if (selected) Icons.Filled.PhotoCamera else Icons.Outlined.PhotoCamera
    MainNavDestination.GALLERY -> if (selected) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary
    MainNavDestination.AI_STUDIO -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
}
