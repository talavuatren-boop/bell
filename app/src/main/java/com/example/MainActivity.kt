package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.model.MainNavDestination
import com.example.ui.HungCamApp
import com.example.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var activeTab: MainNavDestination = MainNavDestination.CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighRefreshRateDisplay()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        setContent {
            HungCamApp(
                viewModel = viewModel,
                onDestinationChanged = { dest -> activeTab = dest }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun requestHighRefreshRateDisplay() {
        runCatching {
            val activeDisplay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
            } ?: return
            val modes = activeDisplay.supportedModes
            val currentMode = activeDisplay.mode
            val bestMode = modes
                .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                .maxByOrNull { it.refreshRate }
            val attrs = window.attributes
            if (bestMode != null && bestMode.refreshRate >= 60f) {
                attrs.preferredDisplayModeId = bestMode.modeId
                attrs.preferredRefreshRate = bestMode.refreshRate
            } else {
                attrs.preferredRefreshRate = 60f
            }
            window.attributes = attrs
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (activeTab == MainNavDestination.CAMERA &&
            viewModel.settings.value.volumeShutterEnabled &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            if (event?.repeatCount == 0) {
                viewModel.triggerShutterAction()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        viewModel.cameraEngine.closeCamera()
    }

    override fun onResume() {
        super.onResume()
        if (activeTab == MainNavDestination.CAMERA) {
            viewModel.cameraEngine.openCameraIfReady()
        }
    }
}
