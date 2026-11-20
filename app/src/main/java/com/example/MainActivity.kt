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
