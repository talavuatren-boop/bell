package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MediaAssetEntity
import com.example.model.MainNavDestination
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.CapabilityReportScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoEditorScreen
import com.example.ui.screens.VideoMergeScreen
import com.example.ui.theme.HungCamTheme
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

private sealed interface SubScreen {
    data object None : SubScreen
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

    var currentTab by remember { mutableStateOf(MainNavDestination.CAMERA) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentTab, subScreen) {
        onDestinationChanged(if (subScreen == SubScreen.None) currentTab else MainNavDestination.GALLERY)
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
        val accent = LocalAccentColor.current

        when (val sub = subScreen) {
            is SubScreen.Viewer -> {
                MediaViewerScreen(
                    assets = sub.assets,
                    initialIndex = sub.index,
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
            is SubScreen.CapabilityReport -> {
                CapabilityReportScreen(
                    viewModel = viewModel,
                    onBack = { subScreen = SubScreen.None }
                )
            }
            SubScreen.None -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(PureBlack)) {
                    val useRail = maxWidth >= 600.dp

                    Scaffold(
                        containerColor = PureBlack,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = ObsidianSurface,
                                    contentColor = PureWhite,
                                    windowInsets = WindowInsets.navigationBars
                                ) {
                                    MainNavDestination.entries.forEach { dest ->
                                        val selected = currentTab == dest
                                        NavigationBarItem(
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
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = PureBlack,
                                                selectedTextColor = accent,
                                                indicatorColor = accent,
                                                unselectedIconColor = MutedSlate,
                                                unselectedTextColor = MutedSlate
                                            ),
                                            modifier = Modifier.testTag("nav_${dest.route}")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
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
                                                selectedIconColor = PureBlack,
                                                selectedTextColor = accent,
                                                indicatorColor = accent,
                                                unselectedIconColor = MutedSlate,
                                                unselectedTextColor = MutedSlate
                                            ),
                                            modifier = Modifier.testTag("nav_rail_${dest.route}")
                                        )
                                    }
                                }
                            }

                            when (currentTab) {
                                MainNavDestination.CAMERA -> CameraScreen(
                                    viewModel = viewModel,
                                    onOpenGallery = { currentTab = MainNavDestination.GALLERY },
                                    onOpenStickersMeme = { asset ->
                                        subScreen = SubScreen.PhotoEdit(asset, openStickersMeme = true)
                                    },
                                    onOpenVideoMerge = { subScreen = SubScreen.VideoMerge },
                                    modifier = Modifier.fillMaxSize()
                                )
                                MainNavDestination.GALLERY -> GalleryScreen(
                                    viewModel = viewModel,
                                    onOpenViewer = { list, idx -> subScreen = SubScreen.Viewer(list, idx) },
                                    onGoToCamera = { currentTab = MainNavDestination.CAMERA },
                                    onOpenVideoMerge = { subScreen = SubScreen.VideoMerge },
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
                                MainNavDestination.SETTINGS -> SettingsScreen(
                                    viewModel = viewModel,
                                    onOpenCapabilityReport = { subScreen = SubScreen.CapabilityReport },
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

private fun navIconFor(dest: MainNavDestination, selected: Boolean) = when (dest) {
    MainNavDestination.CAMERA -> if (selected) Icons.Filled.PhotoCamera else Icons.Outlined.PhotoCamera
    MainNavDestination.GALLERY -> if (selected) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary
    MainNavDestination.AI_STUDIO -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    MainNavDestination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
