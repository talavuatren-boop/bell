package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraEngine
import com.example.camera.CameraRuntimeState
import com.example.camera.FocusPeakingOverlayData
import com.example.camera.HardwareCapabilityDetector
import com.example.camera.SensorTelemetryManager
import com.example.data.HungCamDatabase
import com.example.data.HungCamSettings
import com.example.data.MediaAssetEntity
import com.example.data.MediaStoreRepository
import com.example.data.SettingsDataStore
import com.example.model.AiProcessingMode
import com.example.model.AiProcessingProgress
import com.example.model.AiToolType
import com.example.model.AspectRatioMode
import com.example.model.CameraLensInfo
import com.example.model.CameraMode
import com.example.model.DeviceCapabilityReport
import com.example.model.FlashMode
import com.example.model.GalleryCategory
import com.example.model.GridType
import com.example.model.HdrMode
import com.example.model.LivePhotoDuration
import com.example.model.LiveTelemetryState
import com.example.model.MediaItemType
import com.example.model.PhotoFilterPreset
import com.example.model.ProCameraParameters
import com.example.model.ProCaptureType
import com.example.model.SelfTimerDuration
import com.example.model.SlowMoOption
import com.example.model.TimelapseSpeed
import com.example.model.VideoEditConfig
import com.example.model.VideoMergeLayout
import com.example.model.VideoResolutionPreset
import com.example.processing.ImageProcessResult
import com.example.processing.ImageProcessor
import com.example.processing.ThermalAndMemoryGuard
import com.example.processing.VideoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AiStudioOutputState(
    val selectedAsset: MediaAssetEntity? = null,
    val originalPreviewBitmap: Bitmap? = null,
    val enhancedPreviewBitmap: Bitmap? = null,
    val imageResult: ImageProcessResult? = null,
    val enhancedVideoUri: Uri? = null,
    val processingTimeMs: Long = 0L,
    val savedCopySuccess: Boolean = false
)

data class VideoMergeProgressState(
    val isRunning: Boolean = false,
    val percent: Int = 0,
    val stage: String = "",
    val savedUri: Uri? = null,
    val summary: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HungCamDatabase.getInstance(application)
    val mediaRepo = MediaStoreRepository(application, db.mediaAssetDao())
    val settingsStore = SettingsDataStore(application)
    private val capabilityDetector = HardwareCapabilityDetector(application)
    val sensorTelemetry = SensorTelemetryManager(application)
    val cameraEngine = CameraEngine(application, mediaRepo, sensorTelemetry)
    private val thermalGuard = ThermalAndMemoryGuard(application)
    private val videoProcessor = VideoProcessor(application)

    val settings: StateFlow<HungCamSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, HungCamSettings())

    val activeAssets: StateFlow<List<MediaAssetEntity>> = mediaRepo.activeAssets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedAssets: StateFlow<List<MediaAssetEntity>> = mediaRepo.deletedAssets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cameraRuntime: StateFlow<CameraRuntimeState> = cameraEngine.runtimeState
    val focusPeakingData: StateFlow<FocusPeakingOverlayData?> = cameraEngine.focusPeaking
    val capturedMemeAsset: StateFlow<MediaAssetEntity?> = cameraEngine.capturedMemeAsset

    private val _capabilities = MutableStateFlow<DeviceCapabilityReport?>(null)
    val capabilities: StateFlow<DeviceCapabilityReport?> = _capabilities.asStateFlow()

    private val _activeLens = MutableStateFlow<CameraLensInfo?>(null)
    val activeLens: StateFlow<CameraLensInfo?> = _activeLens.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.PHOTO)
    val cameraMode: StateFlow<CameraMode> = _cameraMode.asStateFlow()

    private val _flashMode = MutableStateFlow(FlashMode.OFF)
    val flashMode: StateFlow<FlashMode> = _flashMode.asStateFlow()

    private val _liveDuration = MutableStateFlow(LivePhotoDuration.THREE_SEC)
    val liveDuration: StateFlow<LivePhotoDuration> = _liveDuration.asStateFlow()

    private val _selfTimer = MutableStateFlow(SelfTimerDuration.OFF)
    val selfTimer: StateFlow<SelfTimerDuration> = _selfTimer.asStateFlow()

    private val _countdownRemaining = MutableStateFlow(0)
    val countdownRemaining: StateFlow<Int> = _countdownRemaining.asStateFlow()

    private val _proParams = MutableStateFlow(ProCameraParameters())
    val proParams: StateFlow<ProCameraParameters> = _proParams.asStateFlow()

    private val _proCaptureType = MutableStateFlow(ProCaptureType.PHOTO)
    val proCaptureType: StateFlow<ProCaptureType> = _proCaptureType.asStateFlow()

    private val _portraitBokehStrength = MutableStateFlow(0.55f)
    val portraitBokehStrength: StateFlow<Float> = _portraitBokehStrength.asStateFlow()

    private val _timelapseSpeed = MutableStateFlow(TimelapseSpeed.X5)
    val timelapseSpeed: StateFlow<TimelapseSpeed> = _timelapseSpeed.asStateFlow()

    private val _slowMoOption = MutableStateFlow<SlowMoOption?>(null)
    val slowMoOption: StateFlow<SlowMoOption?> = _slowMoOption.asStateFlow()

    private val _cameraFilter = MutableStateFlow(PhotoFilterPreset.NATURAL)
    val cameraFilter: StateFlow<PhotoFilterPreset> = _cameraFilter.asStateFlow()

    private val _cameraFilterIntensity = MutableStateFlow(0.88f)
    val cameraFilterIntensity: StateFlow<Float> = _cameraFilterIntensity.asStateFlow()

    private val _isMemeCaptureMode = MutableStateFlow(false)
    val isMemeCaptureMode: StateFlow<Boolean> = _isMemeCaptureMode.asStateFlow()

    private val _galleryCategory = MutableStateFlow(GalleryCategory.ALL)
    val galleryCategory: StateFlow<GalleryCategory> = _galleryCategory.asStateFlow()

    private val _aiProgress = MutableStateFlow(AiProcessingProgress())
    val aiProgress: StateFlow<AiProcessingProgress> = _aiProgress.asStateFlow()

    private val _aiOutput = MutableStateFlow(AiStudioOutputState())
    val aiOutput: StateFlow<AiStudioOutputState> = _aiOutput.asStateFlow()

    private val _editorExportNotice = MutableStateFlow<String?>(null)
    val editorExportNotice: StateFlow<String?> = _editorExportNotice.asStateFlow()

    private val _mergeProgress = MutableStateFlow(VideoMergeProgressState())
    val mergeProgress: StateFlow<VideoMergeProgressState> = _mergeProgress.asStateFlow()

    val telemetry: StateFlow<LiveTelemetryState> = combine(
        cameraEngine.histogramBins,
        cameraEngine.clippingStats,
        sensorTelemetry.orientation,
        cameraEngine.liveMeteringInfo
    ) { bins, clips, orient, metering ->
        LiveTelemetryState(
            luminanceBins = bins,
            highlightClipPercent = clips.first,
            shadowClipPercent = clips.second,
            rollDegrees = orient.rollDegrees,
            pitchDegrees = orient.pitchDegrees,
            isLevelAligned = orient.isLevelAligned,
            activeIso = metering.first,
            activeExposureNs = metering.second,
            livePhotoBufferHealth = cameraEngine.getLiveBufferHealth(),
            freeStorageMb = mediaRepo.getAvailableStorageMb()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), LiveTelemetryState())

    private var timerJob: Job? = null
    private var burstJob: Job? = null
    private var aiJob: Job? = null
    private var mergeJob: Job? = null
    private var clockJob: Job? = null
    private var initializedFromSettings = false

    init {
        refreshCapabilities()
        startRecordingClockLoop()
        viewModelScope.launch {
            settings.collect { s ->
                if (!initializedFromSettings) {
                    initializedFromSettings = true
                    _cameraMode.value = s.defaultCameraMode
                    _liveDuration.value = s.livePhotoDefaultDuration
                }
                syncSession()
            }
        }
    }

    fun refreshCapabilities() {
        viewModelScope.launch {
            val report = capabilityDetector.detectCapabilities()
            _capabilities.value = report
            if (_activeLens.value == null && report.lenses.isNotEmpty()) {
                val preferBack = settings.value.defaultLensFacingBack
                val defaultLens = report.lenses.firstOrNull {
                    it.lensFacing == if (preferBack) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
                } ?: report.lenses.first()
                selectLens(defaultLens)
            }
        }
    }

    fun selectLens(lens: CameraLensInfo) {
        _activeLens.value = lens
        _slowMoOption.value = lens.supportedSlowMoModes.firstOrNull()
        // Validate video resolution/FPS against actual hardware capabilities
        val validProfile = lens.supportedVideoProfiles.firstOrNull { it.resolution == settings.value.videoResolution }
            ?: lens.supportedVideoProfiles.firstOrNull()
        if (validProfile != null) {
            val validFps = if (validProfile.supportedFps.contains(settings.value.videoFps)) {
                settings.value.videoFps
            } else {
                validProfile.supportedFps.first()
            }
            viewModelScope.launch {
                settingsStore.update { it.copy(videoResolution = validProfile.resolution, videoFps = validFps) }
            }
        }
        // Reset PRO focus/ISO safely into supported sensor bounds
        _proParams.value = _proParams.value.copy(
            iso = _proParams.value.iso.coerceIn(lens.isoRange.first, lens.isoRange.last),
            exposureTimeNs = _proParams.value.exposureTimeNs.coerceIn(lens.exposureTimeRangeNs.first, lens.exposureTimeRangeNs.last),
            evSteps = _proParams.value.evSteps.coerceIn(lens.evRange.first, lens.evRange.last),
            focusDistance = _proParams.value.focusDistance.coerceIn(0f, lens.minFocusDistance.coerceAtLeast(0.01f))
        )
        performHapticTick()
        syncSession()
    }

    fun selectZoomStop(stop: com.example.model.ZoomStopPreset) {
        val active = _activeLens.value
        val sameLens = active != null &&
            active.cameraId == stop.backingLens.cameraId &&
            active.physicalId == stop.backingLens.physicalId
        if (sameLens) {
            cameraEngine.smoothZoomTo(stop.targetZoomRatio)
            performHapticTick()
        } else {
            if (cameraRuntime.value.isRecording) {
                // Mid-recording: smoothly zoom on active camera stream without interrupting recording
                cameraEngine.smoothZoomTo(stop.targetZoomRatio)
                performHapticTick()
            } else {
                selectLens(stop.backingLens)
                if (kotlin.math.abs(stop.targetZoomRatio - 1.0f) > 0.02f) {
                    cameraEngine.setZoomRatio(stop.targetZoomRatio)
                }
            }
        }
    }

    fun flipFrontBackCamera() {
        val lenses = _capabilities.value?.lenses ?: return
        val cur = _activeLens.value ?: return
        val targetFacing = if (cur.lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val next = lenses.firstOrNull { it.lensFacing == targetFacing && it.physicalId == null }
            ?: lenses.firstOrNull { it.lensFacing == targetFacing }
            ?: return
        selectLens(next)
    }

    fun setCameraMode(mode: CameraMode) {
        if (cameraRuntime.value.isRecording) return
        _cameraMode.value = mode
        if (mode == CameraMode.VIDEO || mode == CameraMode.SLOW_MOTION || mode == CameraMode.TIMELAPSE) {
            _isMemeCaptureMode.value = false
        }
        performHapticTick()
        syncSession()
    }

    fun setProCaptureType(type: ProCaptureType) {
        if (cameraRuntime.value.isRecording) return
        if (_proCaptureType.value == type) return
        _proCaptureType.value = type
        if (type == ProCaptureType.VIDEO) {
            _isMemeCaptureMode.value = false
            if (_flashMode.value == FlashMode.AUTO || _flashMode.value == FlashMode.ON) {
                _flashMode.value = FlashMode.OFF
            }
        }
        performHapticTick()
        syncSession()
    }

    fun setMemeCaptureMode(enabled: Boolean) {
        if (cameraRuntime.value.isRecording) return
        if (enabled && isVideoCaptureSelected()) {
            _cameraMode.value = CameraMode.PHOTO
            syncSession()
        }
        _isMemeCaptureMode.value = enabled
        performHapticTick()
    }

    fun consumeCapturedMemeAsset() {
        cameraEngine.consumeCapturedMemeAsset()
        _isMemeCaptureMode.value = false
    }

    fun cycleHdrMode() {
        val order = listOf(HdrMode.ALWAYS, HdrMode.AUTO, HdrMode.OFF)
        val cur = settings.value.hdrMode
        val next = order[(order.indexOf(cur) + 1) % order.size]
        viewModelScope.launch { settingsStore.update { it.copy(hdrMode = next) } }
        performHapticTick()
    }

    fun toggleCameraAiSuperSharp() {
        viewModelScope.launch {
            settingsStore.update { it.copy(cameraAiSuperSharpEnabled = !it.cameraAiSuperSharpEnabled) }
        }
        performHapticTick()
    }

    fun setPortraitBokehStrength(strength: Float) {
        _portraitBokehStrength.value = strength.coerceIn(0f, 1f)
        syncSession()
    }

    fun updatePortraitFocusPoint(normX: Float, normY: Float) {
        cameraEngine.updatePortraitFocusPoint(normX, normY)
    }

    fun isVideoCaptureSelected(): Boolean {
        val mode = _cameraMode.value
        return mode == CameraMode.VIDEO ||
            mode == CameraMode.SLOW_MOTION ||
            mode == CameraMode.TIMELAPSE ||
            (mode == CameraMode.PRO && _proCaptureType.value == ProCaptureType.VIDEO)
    }

    fun cycleFlashMode() {
        val lens = _activeLens.value
        if (lens?.hasFlash != true) return
        val order = if (isVideoCaptureSelected()) {
            listOf(FlashMode.OFF, FlashMode.TORCH)
        } else {
            listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON, FlashMode.TORCH)
        }
        val next = order[(order.indexOf(_flashMode.value) + 1) % order.size]
        _flashMode.value = next
        cameraEngine.updateFlashMode(next)
        performHapticTick()
    }

    fun cycleLiveDuration() {
        val order = LivePhotoDuration.entries
        val next = order[(order.indexOf(_liveDuration.value) + 1) % order.size]
        _liveDuration.value = next
        performHapticTick()
        syncSession()
    }

    fun cycleSelfTimer() {
        val order = SelfTimerDuration.entries
        _selfTimer.value = order[(order.indexOf(_selfTimer.value) + 1) % order.size]
        performHapticTick()
    }

    fun cycleAspectRatio() {
        val order = AspectRatioMode.entries
        val cur = settings.value.aspectRatio
        val next = order[(order.indexOf(cur) + 1) % order.size]
        viewModelScope.launch { settingsStore.update { it.copy(aspectRatio = next) } }
        performHapticTick()
    }

    fun cycleGridType() {
        val order = GridType.entries
        val cur = settings.value.gridType
        val next = order[(order.indexOf(cur) + 1) % order.size]
        viewModelScope.launch { settingsStore.update { it.copy(gridType = next) } }
        performHapticTick()
    }

    fun updateProParams(transform: (ProCameraParameters) -> ProCameraParameters) {
        val next = transform(_proParams.value)
        _proParams.value = next
        cameraEngine.updateProParameters(next)
    }

    fun setTimelapseSpeed(speed: TimelapseSpeed) {
        _timelapseSpeed.value = speed
        performHapticTick()
    }

    fun setSlowMoOption(option: SlowMoOption) {
        _slowMoOption.value = option
        performHapticTick()
        syncSession()
    }

    fun setCameraFilter(filter: PhotoFilterPreset) {
        _cameraFilter.value = filter
        cameraEngine.updateCameraFilter(filter, _cameraFilterIntensity.value)
        performHapticTick()
    }

    fun setCameraFilterIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.15f, 1f)
        _cameraFilterIntensity.value = clamped
        cameraEngine.updateCameraFilter(_cameraFilter.value, clamped)
    }

    fun toggleRecordingMicMute() {
        cameraEngine.toggleRecordingAudioMute()
        performHapticTick()
    }

    fun triggerShutterAction() {
        if (isVideoCaptureSelected()) {
            if (cameraRuntime.value.isRecording) {
                cameraEngine.stopVideoRecording()
            } else {
                cameraEngine.startVideoRecording()
            }
            performHapticTick(heavy = true)
            return
        }

        // Photo / Portrait / PRO Photo (with optional self-timer)
        if (timerJob?.isActive == true) {
            timerJob?.cancel()
            _countdownRemaining.value = 0
            return
        }

        val timerSecs = _selfTimer.value.seconds
        val memeShot = _isMemeCaptureMode.value
        if (timerSecs > 0) {
            timerJob = viewModelScope.launch {
                for (sec in timerSecs downTo 1) {
                    _countdownRemaining.value = sec
                    performHapticTick()
                    delay(1000L)
                }
                _countdownRemaining.value = 0
                performHapticTick(heavy = true)
                cameraEngine.capturePhoto(isMemeShot = memeShot)
            }
        } else {
            performHapticTick(heavy = true)
            cameraEngine.capturePhoto(isMemeShot = memeShot)
        }
    }

    fun startBurstCapture() {
        if (isVideoCaptureSelected()) return
        if (_cameraMode.value != CameraMode.PHOTO && _cameraMode.value != CameraMode.PRO) return
        if (burstJob?.isActive == true) return
        burstJob = viewModelScope.launch {
            repeat(6) {
                cameraEngine.capturePhoto(isBurstShot = true)
                performHapticTick()
                delay(280L)
            }
        }
    }

    fun stopBurstCapture() {
        burstJob?.cancel()
        burstJob = null
    }

    fun selectGalleryCategory(cat: GalleryCategory) {
        _galleryCategory.value = cat
    }

    fun importExternalUri(uri: Uri, onImported: (MediaAssetEntity) -> Unit = {}) {
        viewModelScope.launch {
            mediaRepo.importExternalMediaUri(uri).onSuccess { entity ->
                onImported(entity)
            }
        }
    }

    fun selectAssetForAiStudio(asset: MediaAssetEntity) {
        viewModelScope.launch {
            val previewBmp = if (asset.typeEnum != MediaItemType.VIDEO) {
                loadScaledBitmap(Uri.parse(asset.mediaStoreUri), 1600)
            } else null
            _aiOutput.value = AiStudioOutputState(
                selectedAsset = asset,
                originalPreviewBitmap = previewBmp
            )
        }
    }

    fun runAiStudioProcessing(tool: AiToolType, requestedMode: AiProcessingMode) {
        val asset = _aiOutput.value.selectedAsset ?: return
        if (cameraRuntime.value.isRecording) {
            _aiProgress.value = AiProcessingProgress(errorMessage = "Paused: camera recording is active")
            return
        }

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val health = thermalGuard.inspect(requestedMode)
            val effectiveMode = health.advisedMode
            val advisedDiffers = effectiveMode != requestedMode

            _aiProgress.value = AiProcessingProgress(
                isRunning = true,
                tool = tool,
                mode = effectiveMode,
                percent = 2,
                stage = "Preparing local pipeline...",
                estimatedStatus = if (advisedDiffers) {
                    "Thermal guard adapted to ${effectiveMode.label} (${health.statusLabel})"
                } else {
                    "Local device processing (${health.statusLabel})"
                },
                thermalAdvisedMode = if (advisedDiffers) effectiveMode else null
            )

            try {
                if (asset.typeEnum == MediaItemType.VIDEO || tool.isVideoOnly && asset.typeEnum == MediaItemType.VIDEO) {
                    val config = VideoEditConfig(
                        targetResolution = settings.value.videoResolution,
                        targetFps = settings.value.videoFps,
                        applyStabilization = tool == AiToolType.VIDEO_STABILIZE || tool == AiToolType.ENHANCE_ALL,
                        applyDenoise = tool == AiToolType.DENOISE || tool == AiToolType.ENHANCE_ALL,
                        applySharpen = tool == AiToolType.SHARPEN || tool == AiToolType.ENHANCE_ALL,
                        applyAiColor = tool == AiToolType.COLOR_CORRECTION || tool == AiToolType.ENHANCE_ALL
                    )
                    val res = videoProcessor.processVideo(
                        sourceUri = Uri.parse(asset.mediaStoreUri),
                        config = config,
                        mode = effectiveMode,
                        gyroTrackPath = asset.gyroTrackPath,
                        interpolateTo60Fps = tool == AiToolType.FRAME_INTERPOLATE,
                        applyFrameEnhance = tool == AiToolType.FRAME_ENHANCE || tool == AiToolType.ENHANCE_ALL
                    ) { pct, stage ->
                        _aiProgress.value = _aiProgress.value.copy(percent = pct, stage = stage)
                    }

                    val savedUri = mediaRepo.saveVideoFileToMediaStore(
                        sourceFile = res.outputFile,
                        width = res.width,
                        height = res.height,
                        fps = res.fps,
                        bitrateBps = res.bitrateBps,
                        lensLabel = "AI ${tool.label}",
                        aiTag = "${tool.label} (${effectiveMode.label})",
                        originalAssetId = asset.id
                    ).getOrNull()
                    runCatching { res.outputFile.delete() }

                    _aiOutput.value = _aiOutput.value.copy(
                        enhancedVideoUri = savedUri,
                        processingTimeMs = res.processingTimeMs,
                        savedCopySuccess = savedUri != null
                    )
                    _aiProgress.value = _aiProgress.value.copy(isRunning = false, percent = 100, stage = "Saved enhanced video")
                } else {
                    val maxDim = when (effectiveMode) {
                        AiProcessingMode.FAST -> 1440
                        AiProcessingMode.BALANCED -> 2048
                        AiProcessingMode.QUALITY -> 3000
                    }
                    val srcBmp = loadScaledBitmap(Uri.parse(asset.mediaStoreUri), maxDim)
                        ?: throw IllegalStateException("Unable to decode selected image")

                    val res = ImageProcessor.runAiTool(srcBmp, tool, effectiveMode) { pct, stage ->
                        _aiProgress.value = _aiProgress.value.copy(percent = pct, stage = stage)
                    }
                    _aiOutput.value = _aiOutput.value.copy(
                        originalPreviewBitmap = srcBmp,
                        enhancedPreviewBitmap = res.outputBitmap,
                        imageResult = res,
                        processingTimeMs = res.processingTimeMs,
                        savedCopySuccess = false
                    )
                    _aiProgress.value = _aiProgress.value.copy(isRunning = false, percent = 100, stage = "Ready to compare & save")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _aiProgress.value = AiProcessingProgress(isRunning = false, stage = "Cancelled by user")
            } catch (e: Exception) {
                _aiProgress.value = AiProcessingProgress(
                    isRunning = false,
                    errorMessage = e.localizedMessage ?: "AI processing failed"
                )
            }
        }
    }

    fun cancelAiProcessing() {
        aiJob?.cancel()
        aiJob = null
        _aiProgress.value = AiProcessingProgress(isRunning = false, stage = "Cancelled")
    }

    fun saveAiEnhancedPhotoCopy() {
        val out = _aiOutput.value
        val bmp = out.enhancedPreviewBitmap ?: return
        val res = out.imageResult
        val orig = out.selectedAsset
        viewModelScope.launch {
            mediaRepo.saveBitmapAsImage(
                bitmap = bmp,
                aiTag = res?.summaryLabel ?: "AI Enhanced",
                originalAssetId = orig?.id
            ).onSuccess {
                _aiOutput.value = _aiOutput.value.copy(savedCopySuccess = true)
                performHapticTick()
            }
        }
    }

    fun saveEditedPhotoCopy(bitmap: Bitmap, originalAssetId: Long?) {
        viewModelScope.launch {
            mediaRepo.saveBitmapAsImage(
                bitmap = bitmap,
                aiTag = "Edited",
                originalAssetId = originalAssetId
            ).onSuccess {
                _editorExportNotice.value = "Saved edited copy to Gallery"
                performHapticTick()
            }.onFailure {
                _editorExportNotice.value = "Failed: ${it.localizedMessage}"
            }
        }
    }

    fun exportEditedVideo(
        sourceAsset: MediaAssetEntity,
        config: VideoEditConfig,
        onProgress: (Int, String) -> Unit,
        onFinished: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = videoProcessor.processVideo(
                    sourceUri = Uri.parse(sourceAsset.mediaStoreUri),
                    config = config,
                    mode = settings.value.aiDefaultMode,
                    gyroTrackPath = sourceAsset.gyroTrackPath,
                    onProgress = onProgress
                )
                val saveRes = mediaRepo.saveVideoFileToMediaStore(
                    sourceFile = res.outputFile,
                    width = res.width,
                    height = res.height,
                    fps = res.fps,
                    bitrateBps = res.bitrateBps,
                    lensLabel = "Video Editor",
                    aiTag = buildString {
                        append("Edited")
                        if (config.applyStabilization) append(" + Stab")
                        if (config.applyDenoise) append(" + Denoise")
                        if (config.applySharpen) append(" + Sharpen")
                    },
                    originalAssetId = sourceAsset.id
                )
                runCatching { res.outputFile.delete() }
                if (saveRes.isSuccess) {
                    onFinished("Exported copy (${res.width}x${res.height} @ ${res.fps}fps)")
                } else {
                    onFinished("Export failed: ${saveRes.exceptionOrNull()?.localizedMessage}")
                }
            } catch (e: Exception) {
                onFinished("Error: ${e.localizedMessage}")
            }
        }
    }

    fun clearEditorNotice() {
        _editorExportNotice.value = null
    }

    fun mergeVideos(
        sourceUris: List<Uri>,
        layout: VideoMergeLayout,
        resolution: VideoResolutionPreset,
        fps: Int,
        includeAudio: Boolean
    ) {
        if (sourceUris.size < 2) {
            _mergeProgress.value = VideoMergeProgressState(errorMessage = "Select at least 2 videos to merge")
            return
        }
        if (cameraRuntime.value.isRecording) {
            _mergeProgress.value = VideoMergeProgressState(errorMessage = "Paused: camera recording is active")
            return
        }
        mergeJob?.cancel()
        mergeJob = viewModelScope.launch {
            _mergeProgress.value = VideoMergeProgressState(
                isRunning = true,
                percent = 2,
                stage = "Preparing ${sourceUris.size} clips (${layout.label})..."
            )
            try {
                val res = videoProcessor.mergeVideos(
                    sourceUris = sourceUris,
                    layout = layout,
                    targetResolution = resolution,
                    targetFps = fps,
                    includeAudio = includeAudio
                ) { pct, stage ->
                    _mergeProgress.value = _mergeProgress.value.copy(percent = pct, stage = stage)
                }
                val savedUri = mediaRepo.saveVideoFileToMediaStore(
                    sourceFile = res.outputFile,
                    width = res.width,
                    height = res.height,
                    fps = res.fps,
                    bitrateBps = res.bitrateBps,
                    lensLabel = "Video Merge",
                    aiTag = "Merged (${layout.label})"
                ).getOrNull()
                runCatching { res.outputFile.delete() }
                if (savedUri != null) {
                    val msg = "Merged ${res.width}x${res.height} @ ${res.fps}fps (${res.processingTimeMs} ms)"
                    _mergeProgress.value = VideoMergeProgressState(
                        isRunning = false,
                        percent = 100,
                        stage = "Saved to Gallery",
                        savedUri = savedUri,
                        summary = msg
                    )
                    _editorExportNotice.value = "Video merged & saved to Gallery"
                    performHapticTick(heavy = true)
                } else {
                    _mergeProgress.value = VideoMergeProgressState(
                        isRunning = false,
                        errorMessage = "Failed to save merged video to MediaStore"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _mergeProgress.value = VideoMergeProgressState(isRunning = false, stage = "Merge cancelled")
            } catch (e: Exception) {
                _mergeProgress.value = VideoMergeProgressState(
                    isRunning = false,
                    errorMessage = e.localizedMessage ?: "Video merge failed"
                )
            }
        }
    }

    fun cancelVideoMerge() {
        mergeJob?.cancel()
        mergeJob = null
        _mergeProgress.value = VideoMergeProgressState(isRunning = false, stage = "Cancelled")
    }

    fun resetMergeState() {
        if (!_mergeProgress.value.isRunning) {
            _mergeProgress.value = VideoMergeProgressState()
        }
    }

    fun updateSettings(transform: (HungCamSettings) -> HungCamSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    private fun syncSession() {
        val lens = _activeLens.value ?: return
        cameraEngine.configureSession(
            lens = lens,
            mode = _cameraMode.value,
            settings = settings.value,
            flash = _flashMode.value,
            liveDuration = _liveDuration.value,
            proParams = _proParams.value,
            proCaptureType = _proCaptureType.value,
            portraitBokeh = _portraitBokehStrength.value,
            timelapseSpeed = _timelapseSpeed.value,
            slowMoOption = _slowMoOption.value
        )
    }

    private fun startRecordingClockLoop() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (true) {
                cameraEngine.tickRecordingClock()
                delay(200L)
            }
        }
    }

    fun performHapticTick(heavy: Boolean = false) {
        if (!settings.value.hapticFeedbackEnabled) return
        val app = getApplication<Application>()
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (app.getSystemService(Application.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Application.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        if (heavy) 24L else 10L,
                        if (heavy) 160 else 80
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (heavy) 20L else 10L)
            }
        }
    }

    suspend fun loadScaledBitmap(uri: Uri, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val cr = getApplication<Application>().contentResolver
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            val longest = maxOf(opts.outWidth, opts.outHeight)
            while (longest / sample > maxDim) sample *= 2
            val decOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decOpts) }
                ?: return@runCatching null
            val exifOrient = cr.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            val matrix = android.graphics.Matrix().apply {
                when (exifOrient) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                    android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
                }
            }
            if (!matrix.isIdentity) {
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true).also {
                    if (it !== bmp) bmp.recycle()
                }
            } else {
                bmp
            }
        }.getOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        cameraEngine.closeCamera()
        sensorTelemetry.stop()
        clockJob?.cancel()
        aiJob?.cancel()
        mergeJob?.cancel()
    }
}
