package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.CamcorderProfile
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import com.example.data.HungCamSettings
import com.example.data.MediaStoreRepository
import com.example.model.CameraLensInfo
import com.example.model.CameraMode
import com.example.model.FlashMode
import com.example.model.LivePhotoDuration
import com.example.model.PhotoFormat
import com.example.model.PhotoResolutionTier
import com.example.model.ProCameraParameters
import com.example.model.ProCaptureType
import com.example.model.SlowMoOption
import com.example.model.StabilizationMode
import com.example.model.TimelapseSpeed
import com.example.model.VideoBitrateTier
import com.example.model.VideoCodecOption
import com.example.model.VideoResolutionPreset
import com.example.model.WhiteBalancePreset
import com.example.processing.ImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class FocusPeakingOverlayData(
    val cols: Int,
    val rows: Int,
    val intensities: FloatArray
)

data class CameraRuntimeState(
    val isCameraOpen: Boolean = false,
    val isPreviewActive: Boolean = false,
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val isCapturing: Boolean = false,
    val isAudioMuted: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val activeZoom: Float = 1.0f,
    val livePhotoFallbackNotice: String? = null,
    val statusBanner: String? = null,
    val errorMessage: String? = null
)

class CameraEngine(
    private val context: Context,
    private val mediaRepo: MediaStoreRepository,
    private val sensorTelemetry: SensorTelemetryManager
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val sessionExecutor = Executors.newSingleThreadExecutor()
    private val cameraOpenCloseLock = Semaphore(1)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var activeLens: CameraLensInfo? = null
    private var activeCharacteristics: CameraCharacteristics? = null

    private var textureView: TextureView? = null
    private var previewSurface: Surface? = null
    private var previewSurfaceTexture: SurfaceTexture? = null
    private var previewSize: Size = Size(1280, 720)
    private var stillImageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null
    private var analysisImageReader: ImageReader? = null

    private var livePhotoBuffer: LivePhotoRingBuffer? = null
    private var capLiveStillTo1080p = false
    private var useLivePhotoFallbackTwoSurface = false
    private var useMinimalTwoSurfaceFallback = false
    private var cameraRecoveryAttempts = 0
    private val livePairLinkMutex = Mutex()
    private var captureWatchdogJob: Job? = null

    private var mediaRecorder: MediaRecorder? = null
    private var activeVideoOutputFile: File? = null
    private var activeGyroOutputFile: File? = null
    private var activeVideoWidth: Int = 1920
    private var activeVideoHeight: Int = 1080
    private var activeVideoFps: Int = 30
    private var activeVideoBitrate: Int = 35_000_000
    private var recordingStartMs: Long = 0L
    private var pausedAccumulatedMs: Long = 0L
    private var pauseStartMs: Long = 0L

    private var currentMode: CameraMode = CameraMode.PHOTO
    private var currentProCaptureType: ProCaptureType = ProCaptureType.PHOTO
    private var currentPortraitBokeh: Float = 0.55f
    private var portraitFocusX: Float = 0.5f
    private var portraitFocusY: Float = 0.42f
    private var currentSettings: HungCamSettings = HungCamSettings()
    private var currentFlash: FlashMode = FlashMode.OFF
    private var currentLiveDuration: LivePhotoDuration = LivePhotoDuration.OFF
    private var currentProParams: ProCameraParameters = ProCameraParameters()
    private var currentTimelapseSpeed: TimelapseSpeed = TimelapseSpeed.X5
    private var currentSlowMoOption: SlowMoOption? = null
    private var currentZoomRatio: Float = 1.0f
    private var displayRotationDegrees: Int = 0

    @Volatile
    private var lastAnalysisTimestampMs: Long = 0L
    @Volatile
    private var lastCaptureResult: TotalCaptureResult? = null
    @Volatile
    private var pendingRawCaptureResult: TotalCaptureResult? = null
    @Volatile
    private var pendingRawImage: Image? = null

    private val _runtimeState = MutableStateFlow(CameraRuntimeState())
    val runtimeState: StateFlow<CameraRuntimeState> = _runtimeState.asStateFlow()

    private val _histogramBins = MutableStateFlow(List(32) { 0f })
    val histogramBins: StateFlow<List<Float>> = _histogramBins.asStateFlow()

    private val _clippingStats = MutableStateFlow(0f to 0f) // highlight%, shadow%
    val clippingStats: StateFlow<Pair<Float, Float>> = _clippingStats.asStateFlow()

    private val _focusPeaking = MutableStateFlow<FocusPeakingOverlayData?>(null)
    val focusPeaking: StateFlow<FocusPeakingOverlayData?> = _focusPeaking.asStateFlow()

    private val _liveMeteringInfo = MutableStateFlow<Pair<Int?, Long?>>(null to null)
    val liveMeteringInfo: StateFlow<Pair<Int?, Long?>> = _liveMeteringInfo.asStateFlow()

    fun attachTextureView(tv: TextureView, rotationDegrees: Int) {
        textureView = tv
        displayRotationDegrees = rotationDegrees
        if (tv.isAvailable) {
            configureTextureTransform(tv.width, tv.height)
            openCameraIfReady()
        } else {
            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    configureTextureTransform(width, height)
                    openCameraIfReady()
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    configureTextureTransform(width, height)
                }
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    closeCamera()
                    previewSurface?.release()
                    previewSurface = null
                    previewSurfaceTexture = null
                    return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
    }

    fun updateDisplayRotation(rotationDegrees: Int) {
        displayRotationDegrees = rotationDegrees
        refreshTextureTransform()
    }

    fun refreshTextureTransform() {
        textureView?.let { tv ->
            if (tv.isAvailable && tv.width > 0 && tv.height > 0) {
                configureTextureTransform(tv.width, tv.height)
            }
        }
    }

    fun configureSession(
        lens: CameraLensInfo,
        mode: CameraMode,
        settings: HungCamSettings,
        flash: FlashMode,
        liveDuration: LivePhotoDuration,
        proParams: ProCameraParameters,
        proCaptureType: ProCaptureType = ProCaptureType.PHOTO,
        portraitBokeh: Float = 0.55f,
        timelapseSpeed: TimelapseSpeed,
        slowMoOption: SlowMoOption?
    ) {
        val lensChanged = activeLens?.cameraId != lens.cameraId || activeLens?.physicalId != lens.physicalId
        val modeChanged = currentMode != mode
        val proTypeChanged = (mode == CameraMode.PRO) && (currentProCaptureType != proCaptureType)
        val liveChanged = (currentLiveDuration == LivePhotoDuration.OFF) != (liveDuration == LivePhotoDuration.OFF)
        val aspectChanged = currentSettings.aspectRatio != settings.aspectRatio
        val formatChanged = currentSettings.photoFormat != settings.photoFormat ||
            currentSettings.photoResolutionTier != settings.photoResolutionTier ||
            currentSettings.videoResolution != settings.videoResolution ||
            currentSettings.videoFps != settings.videoFps ||
            currentSettings.livePhotoSoundEnabled != settings.livePhotoSoundEnabled

        activeLens = lens
        currentMode = mode
        currentProCaptureType = proCaptureType
        currentPortraitBokeh = portraitBokeh.coerceIn(0f, 1f)
        currentSettings = settings
        currentFlash = flash
        currentLiveDuration = liveDuration
        currentProParams = proParams
        currentTimelapseSpeed = timelapseSpeed
        currentSlowMoOption = slowMoOption

        if (lensChanged) {
            capLiveStillTo1080p = false
            useLivePhotoFallbackTwoSurface = false
            useMinimalTwoSurfaceFallback = false
            cameraRecoveryAttempts = 0
            currentZoomRatio = 1.0f.coerceIn(lens.minZoom, lens.maxZoom)
            _runtimeState.value = _runtimeState.value.copy(activeZoom = currentZoomRatio)
            closeCamera()
            openCameraIfReady()
        } else if (modeChanged || proTypeChanged || liveChanged || formatChanged || aspectChanged) {
            if (cameraDevice != null && textureView?.isAvailable == true) {
                backgroundHandler?.post { createPreviewSession() }
            }
        } else {
            applyRepeatingRequestChanges()
        }
    }

    fun updatePortraitFocusPoint(normX: Float, normY: Float) {
        portraitFocusX = normX.coerceIn(0.15f, 0.85f)
        portraitFocusY = normY.coerceIn(0.15f, 0.85f)
    }

    private fun isVideoFamilyMode(): Boolean {
        return currentMode == CameraMode.VIDEO ||
            currentMode == CameraMode.SLOW_MOTION ||
            currentMode == CameraMode.TIMELAPSE ||
            (currentMode == CameraMode.PRO && currentProCaptureType == ProCaptureType.VIDEO)
    }

    fun setZoomRatio(zoom: Float) {
        val lens = activeLens ?: return
        currentZoomRatio = zoom.coerceIn(lens.minZoom, lens.maxZoom)
        _runtimeState.value = _runtimeState.value.copy(activeZoom = currentZoomRatio)
        applyRepeatingRequestChanges()
    }

    fun updateProParameters(proParams: ProCameraParameters) {
        val rawToggled = currentProParams.captureRawDng != proParams.captureRawDng
        currentProParams = proParams
        if (!proParams.focusPeakingEnabled) {
            _focusPeaking.value = null
        }
        if (rawToggled && currentMode == CameraMode.PRO) {
            backgroundHandler?.post { createPreviewSession() }
        } else {
            applyRepeatingRequestChanges()
        }
    }

    fun updateFlashMode(flash: FlashMode) {
        currentFlash = flash
        applyRepeatingRequestChanges()
    }

    fun toggleRecordingAudioMute(): Boolean {
        val next = !_runtimeState.value.isAudioMuted
        _runtimeState.value = _runtimeState.value.copy(isAudioMuted = next)
        return next
    }

    fun getLiveBufferHealth(): String {
        return livePhotoBuffer?.currentBufferStatusLabel() ?: "Off"
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("HungCamCamera2").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        runCatching { backgroundThread?.join(500) }
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission")
    fun openCameraIfReady() {
        val tv = textureView ?: return
        val lens = activeLens ?: return
        if (!tv.isAvailable) return
        if (cameraDevice != null) return

        startBackgroundThread()
        val handler = backgroundHandler ?: return

        scope.launch(Dispatchers.IO) {
            if (!cameraOpenCloseLock.tryAcquire(1500, TimeUnit.MILLISECONDS)) return@launch
            try {
                activeCharacteristics = cameraManager.getCameraCharacteristics(lens.cameraId)
                previewSize = chooseOptimalPreviewSize(activeCharacteristics, tv.width, tv.height)
                withContext(Dispatchers.Main) {
                    configureTextureTransform(tv.width, tv.height)
                }
                val lockReleased = java.util.concurrent.atomic.AtomicBoolean(false)
                fun releaseOpenLockOnce() {
                    if (lockReleased.compareAndSet(false, true)) {
                        cameraOpenCloseLock.release()
                    }
                }
                try {
                    cameraManager.openCamera(lens.cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            releaseOpenLockOnce()
                            cameraDevice = camera
                            _runtimeState.value = _runtimeState.value.copy(isCameraOpen = true, errorMessage = null)
                            backgroundHandler?.post { createPreviewSession() }
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            releaseOpenLockOnce()
                            runCatching { captureSession?.close() }
                            captureSession = null
                            livePhotoBuffer?.stop()
                            livePhotoBuffer = null
                            runCatching { camera.close() }
                            cameraDevice = null
                            _runtimeState.value = _runtimeState.value.copy(
                                isCameraOpen = false,
                                isPreviewActive = false,
                                isCapturing = false
                            )
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            releaseOpenLockOnce()
                            val hadLiveBuffer = livePhotoBuffer != null
                            runCatching { captureSession?.close() }
                            captureSession = null
                            livePhotoBuffer?.stop()
                            livePhotoBuffer = null
                            runCatching { camera.close() }
                            cameraDevice = null

                            if (cameraRecoveryAttempts < 3 && (hadLiveBuffer || !useMinimalTwoSurfaceFallback)) {
                                cameraRecoveryAttempts++
                                if (hadLiveBuffer && !capLiveStillTo1080p) {
                                    capLiveStillTo1080p = true
                                } else if (hadLiveBuffer && !useLivePhotoFallbackTwoSurface) {
                                    useLivePhotoFallbackTwoSurface = true
                                } else {
                                    useMinimalTwoSurfaceFallback = true
                                }
                                _runtimeState.value = _runtimeState.value.copy(
                                    isCameraOpen = false,
                                    isPreviewActive = false,
                                    isCapturing = false,
                                    livePhotoFallbackNotice = "Adjusted Camera2 stream profile (code $error)"
                                )
                                backgroundHandler?.postDelayed({ openCameraIfReady() }, 250L)
                            } else {
                                _runtimeState.value = _runtimeState.value.copy(
                                    isCameraOpen = false,
                                    isPreviewActive = false,
                                    isCapturing = false,
                                    errorMessage = "Camera2 hardware error code $error"
                                )
                            }
                        }
                    }, handler)
                } catch (e: SecurityException) {
                    releaseOpenLockOnce()
                    _runtimeState.value = _runtimeState.value.copy(errorMessage = "Camera permission required")
                } catch (e: Exception) {
                    releaseOpenLockOnce()
                    _runtimeState.value = _runtimeState.value.copy(errorMessage = e.localizedMessage ?: "Failed to open camera")
                }
            } catch (e: SecurityException) {
                cameraOpenCloseLock.release()
                _runtimeState.value = _runtimeState.value.copy(errorMessage = "Camera permission required")
            } catch (e: Exception) {
                cameraOpenCloseLock.release()
                _runtimeState.value = _runtimeState.value.copy(errorMessage = e.localizedMessage ?: "Failed to open camera")
            }
        }
    }

    fun closeCamera() {
        runCatching {
            cameraOpenCloseLock.acquire()
            if (_runtimeState.value.isRecording) {
                runCatching { mediaRecorder?.stop() }
                runCatching { mediaRecorder?.release() }
                mediaRecorder = null
            }
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            stillImageReader?.close()
            stillImageReader = null
            rawImageReader?.close()
            rawImageReader = null
            analysisImageReader?.close()
            analysisImageReader = null
            livePhotoBuffer?.stop()
            livePhotoBuffer = null
            _runtimeState.value = _runtimeState.value.copy(
                isCameraOpen = false,
                isPreviewActive = false,
                isRecording = false,
                isRecordingPaused = false
            )
        }
        cameraOpenCloseLock.release()
        stopBackgroundThread()
    }

    private fun getOrCreatePreviewSurface(st: SurfaceTexture): Surface {
        st.setDefaultBufferSize(previewSize.width, previewSize.height)
        val existing = previewSurface
        if (existing != null && existing.isValid && previewSurfaceTexture === st) {
            return existing
        }
        existing?.release()
        return Surface(st).also {
            previewSurface = it
            previewSurfaceTexture = st
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val tv = textureView ?: return
        val st = tv.surfaceTexture ?: return
        val lens = activeLens ?: return
        val handler = backgroundHandler ?: return

        runCatching { captureSession?.close() }
        captureSession = null
        livePhotoBuffer?.stop()
        livePhotoBuffer = null

        val map = activeCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        previewSize = chooseOptimalPreviewSize(activeCharacteristics, tv.width, tv.height)
        scope.launch {
            if (tv.isAvailable) configureTextureTransform(tv.width, tv.height)
        }
        val activePreviewSurface = getOrCreatePreviewSurface(st)

        // Setup Still ImageReader (JPEG or HEIC) from actual supported sizes matching aspect ratio when available
        val imageFormat = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            currentSettings.photoFormat == PhotoFormat.HEIC &&
            lens.supportsHeic
        ) {
            ImageFormat.HEIC
        } else {
            ImageFormat.JPEG
        }
        val allStillSizes = map?.getOutputSizes(imageFormat)
            ?.sortedByDescending { it.width * it.height }
            .orEmpty()
        val desiredRatio = when (currentSettings.aspectRatio) {
            com.example.model.AspectRatioMode.RATIO_16_9 -> 16f / 9f
            com.example.model.AspectRatioMode.RATIO_4_3 -> 4f / 3f
            com.example.model.AspectRatioMode.RATIO_1_1 -> 1f
            com.example.model.AspectRatioMode.FULL_SCREEN -> 16f / 9f
        }
        val ratioMatchedStillSizes = allStillSizes.filter {
            val r = it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f)
            abs(r - desiredRatio) < 0.08f
        }.ifEmpty { allStillSizes }

        val wantLivePhoto = (currentMode == CameraMode.PHOTO && currentLiveDuration != LivePhotoDuration.OFF)
        val capStillForLiveStream = wantLivePhoto &&
            !useLivePhotoFallbackTwoSurface &&
            !useMinimalTwoSurfaceFallback &&
            lens.hardwareLevelLabel != "LEGACY"

        val chosenStillSize = when {
            (useMinimalTwoSurfaceFallback || capLiveStillTo1080p) && ratioMatchedStillSizes.isNotEmpty() -> {
                ratioMatchedStillSizes.firstOrNull { it.width <= 1920 && it.height <= 1440 }
                    ?: ratioMatchedStillSizes.last()
            }
            capStillForLiveStream && lens.hardwareLevelLabel == "LIMITED" && ratioMatchedStillSizes.isNotEmpty() -> {
                ratioMatchedStillSizes.firstOrNull { it.width <= 2560 && it.height <= 1920 }
                    ?: ratioMatchedStillSizes.last()
            }
            (capStillForLiveStream || currentSettings.photoResolutionTier != PhotoResolutionTier.MAX) && ratioMatchedStillSizes.isNotEmpty() -> {
                // Keep <= 12MP (4096x3072) when Live Photo MediaCodec stream is attached to prevent 50MP/200MP HAL overflow
                ratioMatchedStillSizes.firstOrNull { it.width <= 4096 && it.height <= 3072 }
                    ?: ratioMatchedStillSizes.last()
            }
            currentSettings.photoResolutionTier == PhotoResolutionTier.MAX && ratioMatchedStillSizes.isNotEmpty() -> {
                ratioMatchedStillSizes.first()
            }
            else -> Size(lens.standardPhotoWidth, lens.standardPhotoHeight)
        }

        stillImageReader?.close()
        stillImageReader = ImageReader.newInstance(
            chosenStillSize.width,
            chosenStillSize.height,
            imageFormat,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                handleStillImageCaptured(image, imageFormat)
            }, handler)
        }

        // Optional RAW_SENSOR reader in PRO Photo mode
        rawImageReader?.close()
        rawImageReader = null
        if (currentMode == CameraMode.PRO &&
            currentProCaptureType == ProCaptureType.PHOTO &&
            currentProParams.captureRawDng &&
            lens.supportsRaw &&
            !useMinimalTwoSurfaceFallback
        ) {
            val rawSize = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width * it.height }
            if (rawSize != null) {
                rawImageReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                    setOnImageAvailableListener({ reader ->
                        val img = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                        pendingRawImage = img
                        tryFinalizeRawCapture()
                    }, handler)
                }
            }
        }

        // Real-time YUV analysis reader ONLY in PRO mode using supported YUV_420_888 size
        analysisImageReader?.close()
        analysisImageReader = null
        if (currentMode == CameraMode.PRO && !useMinimalTwoSurfaceFallback) {
            val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
            val anaSize = yuvSizes
                .filter { it.width in 160..640 && it.height in 120..480 }
                .minByOrNull { it.width * it.height }
                ?: yuvSizes.minByOrNull { it.width * it.height }
            if (anaSize != null) {
                analysisImageReader = ImageReader.newInstance(anaSize.width, anaSize.height, ImageFormat.YUV_420_888, 2).apply {
                    setOnImageAvailableListener({ reader ->
                        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                        analyzeYuvFrame(image)
                        image.close()
                    }, handler)
                }
            }
        }

        // Live Photo rolling encoder surface (requires non-LEGACY hardware for concurrent 3-surface capture)
        val canAttachLiveSurface = capStillForLiveStream

        if (canAttachLiveSurface) {
            val codecSizes = map?.getOutputSizes(android.media.MediaCodec::class.java)?.toList().orEmpty()
                .ifEmpty { map?.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty() }
            val prefer16By9 = currentSettings.aspectRatio == com.example.model.AspectRatioMode.RATIO_16_9 ||
                currentSettings.aspectRatio == com.example.model.AspectRatioMode.FULL_SCREEN
            val liveSize = if (prefer16By9) {
                codecSizes.firstOrNull { it.width == 1280 && it.height == 720 }
                    ?: codecSizes.firstOrNull { it.width == 1280 && it.height == 960 }
                    ?: codecSizes.firstOrNull { it.width <= 1280 && it.height <= 960 && it.width >= 640 }
                    ?: Size(1280, 720)
            } else {
                codecSizes.firstOrNull { it.width == 1280 && it.height == 960 }
                    ?: codecSizes.firstOrNull { it.width == 960 && it.height == 720 }
                    ?: codecSizes.firstOrNull { it.width == 1280 && it.height == 720 }
                    ?: codecSizes.firstOrNull { it.width <= 1280 && it.height <= 960 && it.width >= 640 }
                    ?: Size(640, 480)
            }

            val buf = LivePhotoRingBuffer(
                width = liveSize.width,
                height = liveSize.height,
                bitrateBps = if (currentSettings.livePhotoHighBitrate) 16_000_000 else 10_000_000,
                fps = 30
            )
            if (buf.start(enableAudio = currentSettings.livePhotoSoundEnabled)) {
                livePhotoBuffer = buf
            }
        }

        val surfaces = mutableListOf(activePreviewSurface)
        stillImageReader?.surface?.let { surfaces.add(it) }
        rawImageReader?.surface?.let { surfaces.add(it) }
        analysisImageReader?.surface?.let { surfaces.add(it) }
        if (canAttachLiveSurface) {
            livePhotoBuffer?.getInputSurface()?.let { surfaces.add(it) }
        }

        val template = if (isVideoFamilyMode()) {
            CameraDevice.TEMPLATE_RECORD
        } else {
            CameraDevice.TEMPLATE_PREVIEW
        }

        val builder = device.createCaptureRequest(template).apply {
            addTarget(activePreviewSurface)
            analysisImageReader?.surface?.let { addTarget(it) }
            if (canAttachLiveSurface) {
                livePhotoBuffer?.getInputSurface()?.let { addTarget(it) }
            }
        }
        previewRequestBuilder = builder
        applyRequestParameters(builder)

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (cameraDevice == null) return
                captureSession = session
                cameraRecoveryAttempts = 0
                val isFallback = wantLivePhoto && !canAttachLiveSurface
                _runtimeState.value = _runtimeState.value.copy(
                    isPreviewActive = true,
                    errorMessage = null,
                    livePhotoFallbackNotice = if (isFallback) {
                        "2-surface preview mode (${lens.hardwareLevelLabel} stream limit)"
                    } else null
                )
                applyRepeatingRequestChanges()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                if (canAttachLiveSurface && !capLiveStillTo1080p) {
                    capLiveStillTo1080p = true
                    handler.post { createPreviewSession() }
                } else if (canAttachLiveSurface) {
                    useLivePhotoFallbackTwoSurface = true
                    handler.post { createPreviewSession() }
                } else if (!useMinimalTwoSurfaceFallback) {
                    useMinimalTwoSurfaceFallback = true
                    handler.post { createPreviewSession() }
                } else {
                    _runtimeState.value = _runtimeState.value.copy(
                        errorMessage = "Camera session configuration failed on this lens"
                    )
                }
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                lens.physicalId != null &&
                !useMinimalTwoSurfaceFallback
            ) {
                val outputConfigs = surfaces.map { surface ->
                    OutputConfiguration(surface).apply {
                        runCatching { setPhysicalCameraId(lens.physicalId) }
                    }
                }
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    sessionExecutor,
                    callback
                )
                runCatching { device.createCaptureSession(sessionConfig) }
                    .onFailure {
                        @Suppress("DEPRECATION")
                        device.createCaptureSession(surfaces, callback, handler)
                    }
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(surfaces, callback, handler)
            }
        }.onFailure {
            if (canAttachLiveSurface && !capLiveStillTo1080p) {
                capLiveStillTo1080p = true
                handler.post { createPreviewSession() }
            } else if (canAttachLiveSurface) {
                useLivePhotoFallbackTwoSurface = true
                handler.post { createPreviewSession() }
            } else if (!useMinimalTwoSurfaceFallback) {
                useMinimalTwoSurfaceFallback = true
                handler.post { createPreviewSession() }
            } else {
                _runtimeState.value = _runtimeState.value.copy(
                    errorMessage = it.localizedMessage ?: "Failed to create Camera2 session"
                )
            }
        }
    }

    private fun applyRepeatingRequestChanges() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return
        applyRequestParameters(builder, isStillCapture = false)
        runCatching {
            session.setRepeatingRequest(builder.build(), repeatingCaptureCallback, handler)
        }
    }

    private fun applyRequestParameters(builder: CaptureRequest.Builder, isStillCapture: Boolean = false) {
        val lens = activeLens ?: return
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        // Zoom (API 30+ CONTROL_ZOOM_RATIO or SCALER_CROP_REGION fallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoomRatio)
        } else if (currentZoomRatio > 1.0f) {
            val activeArray = activeCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (activeArray != null) {
                val cropW = (activeArray.width() / currentZoomRatio).toInt()
                val cropH = (activeArray.height() / currentZoomRatio).toInt()
                val left = (activeArray.width() - cropW) / 2
                val top = (activeArray.height() - cropH) / 2
                builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + cropW, top + cropH))
            }
        }

        // Flash / Torch
        when (currentFlash) {
            FlashMode.OFF -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            FlashMode.TORCH -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            FlashMode.ON, FlashMode.AUTO -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }

        // Stabilization (OIS / EIS)
        val stab = currentSettings.stabilizationMode
        if (lens.supportsOis && (stab == StabilizationMode.OIS || stab == StabilizationMode.HYBRID)) {
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        } else {
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
        }
        if (lens.supportsEis && (stab == StabilizationMode.EIS || stab == StabilizationMode.HYBRID)) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        } else {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        }

        // Exposure & ISO & Shutter
        if (currentMode == CameraMode.PRO && !currentProParams.autoExposure && lens.supportsManualSensor) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(
                CaptureRequest.SENSOR_SENSITIVITY,
                currentProParams.iso.coerceIn(lens.isoRange.first, lens.isoRange.last)
            )
            val maxExpNs = if (isVideoFamilyMode()) {
                min(lens.exposureTimeRangeNs.last, 1_000_000_000L / currentSettings.videoFps.coerceAtLeast(24))
                    .coerceAtLeast(lens.exposureTimeRangeNs.first)
            } else {
                lens.exposureTimeRangeNs.last
            }
            builder.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                currentProParams.exposureTimeNs.coerceIn(lens.exposureTimeRangeNs.first, maxExpNs)
            )
        } else {
            val aeMode = when (currentFlash) {
                FlashMode.ON -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                FlashMode.AUTO -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                else -> CaptureRequest.CONTROL_AE_MODE_ON
            }
            builder.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
            builder.set(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                currentProParams.evSteps.coerceIn(lens.evRange.first, lens.evRange.last)
            )
        }

        // White Balance & Manual Kelvin
        if (currentMode == CameraMode.PRO && currentProParams.whiteBalance == WhiteBalancePreset.MANUAL) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToRggbGains(currentProParams.manualKelvin))
            builder.set(
                CaptureRequest.COLOR_CORRECTION_TRANSFORM,
                ColorSpaceTransform(intArrayOf(1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
            )
        } else if (currentMode == CameraMode.PRO) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, currentProParams.whiteBalance.camera2Mode)
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }

        // Autofocus vs Manual Focus
        if (currentMode == CameraMode.PRO && !currentProParams.autoFocus && lens.minFocusDistance > 0f) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                currentProParams.focusDistance.coerceIn(0f, lens.minFocusDistance)
            )
        } else {
            val afMode = if (isVideoFamilyMode()) {
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            } else {
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            }
            builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
        }

        // HDR Scene Mode requires CONTROL_MODE_USE_SCENE_MODE; apply only on still captures without Live stream or fallback
        if (isStillCapture &&
            currentMode == CameraMode.PHOTO &&
            currentSettings.hdrEnabled &&
            lens.supportsHdrScene &&
            livePhotoBuffer == null &&
            !useMinimalTwoSurfaceFallback &&
            currentFlash == FlashMode.OFF
        ) {
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
        }

        // Video target FPS
        if (currentMode == CameraMode.VIDEO || (currentMode == CameraMode.PRO && currentProCaptureType == ProCaptureType.VIDEO)) {
            val fps = currentSettings.videoFps
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
        }
    }

    private val repeatingCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            lastCaptureResult = result
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            if (iso != null || exp != null) {
                _liveMeteringInfo.value = iso to exp
            }
        }
    }

    fun triggerTapToFocus(normX: Float, normY: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val chars = activeCharacteristics ?: return
        val handler = backgroundHandler ?: return
        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

        val focusPointX = (normX.coerceIn(0f, 1f) * activeArray.width()).toInt()
        val focusPointY = (normY.coerceIn(0f, 1f) * activeArray.height()).toInt()
        val halfSize = 140
        val meteringRect = MeteringRectangle(
            max(0, focusPointX - halfSize),
            max(0, focusPointY - halfSize),
            min(activeArray.width(), halfSize * 2),
            min(activeArray.height(), halfSize * 2),
            MeteringRectangle.METERING_WEIGHT_MAX - 1
        )

        runCatching {
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRect))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRect))
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, res: TotalCaptureResult) {
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
                    applyRepeatingRequestChanges()
                }
            }, handler)
        }
    }

    fun capturePhoto(isBurstShot: Boolean = false) {
        val device = cameraDevice
        val session = captureSession
        val stillReader = stillImageReader
        val handler = backgroundHandler
        val lens = activeLens
        if (device == null || session == null || stillReader == null || handler == null || lens == null) {
            _runtimeState.value = _runtimeState.value.copy(
                isCapturing = false,
                statusBanner = "Reconnecting camera..."
            )
            openCameraIfReady()
            return
        }

        if (!mediaRepo.hasSufficientStorage(25L)) {
            _runtimeState.value = _runtimeState.value.copy(errorMessage = "Insufficient storage (< 25 MB free)")
            return
        }

        if (_runtimeState.value.isCapturing && !isBurstShot) return

        _runtimeState.value = _runtimeState.value.copy(isCapturing = true, errorMessage = null)
        captureWatchdogJob?.cancel()
        captureWatchdogJob = scope.launch {
            delay(5000L)
            if (_runtimeState.value.isCapturing) {
                _runtimeState.value = _runtimeState.value.copy(isCapturing = false)
            }
        }
        val shutterMonotonicUs = SystemClock.elapsedRealtimeNanos() / 1000L

        val capturedOrientation = getJpegOrientation(lens)

        val captureBuilder = runCatching {
            device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(stillReader.surface)
                rawImageReader?.surface?.let { addTarget(it) }
                applyRequestParameters(this, isStillCapture = true)
                set(CaptureRequest.JPEG_ORIENTATION, capturedOrientation)
                set(CaptureRequest.JPEG_QUALITY, 97.toByte())
                if (currentFlash == FlashMode.ON) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                }
            }
        }.getOrElse { err ->
            captureWatchdogJob?.cancel()
            _runtimeState.value = _runtimeState.value.copy(
                isCapturing = false,
                errorMessage = err.localizedMessage ?: "Failed to build still capture request"
            )
            return
        }

        val wantLive = !isBurstShot && currentMode == CameraMode.PHOTO && currentLiveDuration != LivePhotoDuration.OFF
        val isPortraitCapture = currentMode == CameraMode.PORTRAIT

        val pairId = if (wantLive && livePhotoBuffer != null) UUID.randomUUID().toString() else null
        val tv = textureView
        val isPortraitDisplay = displayRotationDegrees == 0 || displayRotationDegrees == 180
        val targetUprightAspect = when (currentSettings.aspectRatio) {
            com.example.model.AspectRatioMode.RATIO_1_1 -> 1f
            com.example.model.AspectRatioMode.RATIO_4_3 -> if (isPortraitDisplay) 3f / 4f else 4f / 3f
            com.example.model.AspectRatioMode.RATIO_16_9 -> if (isPortraitDisplay) 9f / 16f else 16f / 9f
            com.example.model.AspectRatioMode.FULL_SCREEN -> {
                if (tv != null && tv.width > 0 && tv.height > 0) {
                    tv.width.toFloat() / tv.height.toFloat()
                } else if (isPortraitDisplay) 9f / 19.5f else 19.5f / 9f
            }
        }
        pendingStillCaptures.add(
            PendingStillCaptureMeta(
                pairId = pairId,
                targetUprightAspect = targetUprightAspect,
                isPortraitBokeh = isPortraitCapture,
                portraitBokehStrength = currentPortraitBokeh,
                portraitFocusX = portraitFocusX,
                portraitFocusY = portraitFocusY
            )
        )

        runCatching {
            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    lastCaptureResult = result
                    pendingRawCaptureResult = result
                    tryFinalizeRawCapture()
                    if (pairId != null) {
                        finalizeLivePhotoClip(pairId, shutterMonotonicUs, capturedOrientation)
                    }
                }

                override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, failure: CaptureFailure) {
                    pendingStillCaptures.poll()
                    captureWatchdogJob?.cancel()
                    _runtimeState.value = _runtimeState.value.copy(
                        isCapturing = false,
                        errorMessage = "Still capture failed (reason ${failure.reason})"
                    )
                }
            }, handler)
        }.onFailure { err ->
            pendingStillCaptures.poll()
            captureWatchdogJob?.cancel()
            _runtimeState.value = _runtimeState.value.copy(
                isCapturing = false,
                errorMessage = err.localizedMessage ?: "Camera2 capture rejected"
            )
        }
    }

    private fun finalizeLivePhotoClip(pairId: String, shutterMonotonicUs: Long, orientationHint: Int) {
        val ring = livePhotoBuffer ?: return
        val durSec = if (currentLiveDuration == LivePhotoDuration.OFF) 3 else currentLiveDuration.seconds
        val tempFile = File(context.cacheDir, "live_$pairId.mp4")

        scope.launch(Dispatchers.IO) {
            val clipRes = ring.captureClipAroundShutter(durSec, shutterMonotonicUs, tempFile, orientationHint)
            clipRes.onSuccess { clip ->
                val uriRes = mediaRepo.saveVideoFileToMediaStore(
                    sourceFile = clip.file,
                    width = clip.width,
                    height = clip.height,
                    fps = 30,
                    bitrateBps = ring.bitrateBps,
                    lensLabel = activeLens?.displayLabel ?: "Main",
                    isLiveCompanion = true,
                    livePairId = pairId
                )
                uriRes.onSuccess { videoUri ->
                    livePairLinkMutex.withLock {
                        val db = com.example.data.HungCamDatabase.getInstance(context).mediaAssetDao()
                        val existing = db.getByLivePairId(pairId)
                        if (existing != null) {
                            db.update(
                                existing.copy(
                                    mediaType = com.example.model.MediaItemType.LIVE_PHOTO.name,
                                    liveVideoUri = videoUri.toString(),
                                    liveDurationSec = durSec,
                                    liveHasAudio = clip.hasAudio,
                                    shutterOffsetMs = clip.shutterOffsetMs
                                )
                            )
                            _runtimeState.value = _runtimeState.value.copy(
                                statusBanner = "Saved Live Photo (${String.format("%.1fs", clip.totalDurationMs / 1000f)})"
                            )
                        } else {
                            pendingLiveClips[pairId] = PendingLiveClipMeta(
                                videoUri = videoUri.toString(),
                                durationSec = durSec,
                                hasAudio = clip.hasAudio,
                                shutterOffsetMs = clip.shutterOffsetMs
                            )
                        }
                    }
                }
                runCatching { tempFile.delete() }
            }.onFailure { err ->
                _runtimeState.value = _runtimeState.value.copy(
                    statusBanner = "Live clip notice: ${err.message}"
                )
                runCatching { tempFile.delete() }
            }
        }
    }

    private data class PendingLiveClipMeta(
        val videoUri: String,
        val durationSec: Int,
        val hasAudio: Boolean,
        val shutterOffsetMs: Long
    )

    private data class PendingStillCaptureMeta(
        val pairId: String?,
        val targetUprightAspect: Float,
        val isPortraitBokeh: Boolean = false,
        val portraitBokehStrength: Float = 0.55f,
        val portraitFocusX: Float = 0.5f,
        val portraitFocusY: Float = 0.42f
    )

    private val pendingLiveClips = java.util.concurrent.ConcurrentHashMap<String, PendingLiveClipMeta>()
    private val pendingStillCaptures = java.util.concurrent.ConcurrentLinkedQueue<PendingStillCaptureMeta>()

    private fun handleStillImageCaptured(image: Image, format: Int) {
        val rawBytes = ByteArrayOutputStream().use { out ->
            val buf = image.planes[0].buffer
            val data = ByteArray(buf.remaining())
            buf.get(data)
            out.write(data)
            out.toByteArray()
        }
        val w = image.width
        val h = image.height
        image.close()

        val result = lastCaptureResult
        val iso = result?.get(CaptureResult.SENSOR_SENSITIVITY)
        val exp = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val lens = activeLens

        val captureMeta = pendingStillCaptures.poll()
        val queuedPairId = captureMeta?.pairId?.takeIf { it.isNotBlank() }
        val targetUprightAspect = captureMeta?.targetUprightAspect

        val rawExt = if (format == ImageFormat.HEIC) "heic" else "jpg"
        val rawMime = if (format == ImageFormat.HEIC) "image/heic" else "image/jpeg"

        scope.launch(Dispatchers.IO) {
            val postProcessed = processStillBytesForAspectAndPortrait(
                bytes = rawBytes,
                targetUprightAspect = targetUprightAspect,
                isPortraitBokeh = captureMeta?.isPortraitBokeh == true,
                bokehStrength = captureMeta?.portraitBokehStrength ?: 0f,
                focusX = captureMeta?.portraitFocusX ?: 0.5f,
                focusY = captureMeta?.portraitFocusY ?: 0.42f
            )
            val finalBytes = postProcessed?.first ?: rawBytes
            val finalW = postProcessed?.second ?: w
            val finalH = postProcessed?.third ?: h
            val ext = if (postProcessed != null) "jpg" else rawExt
            val mime = if (postProcessed != null) "image/jpeg" else rawMime
            val isPortrait = captureMeta?.isPortraitBokeh == true

            val saveRes = livePairLinkMutex.withLock {
                val pendingMeta = queuedPairId?.let { pendingLiveClips.remove(it) }
                mediaRepo.saveCapturedPhotoBytes(
                    bytes = finalBytes,
                    mimeType = mime,
                    extension = ext,
                    width = finalW,
                    height = finalH,
                    lensLabel = if (isPortrait) "${lens?.displayLabel ?: "Main"} • Portrait" else (lens?.displayLabel ?: "Main"),
                    iso = iso,
                    exposureTimeNs = exp,
                    focalLengthMm = lens?.focalLengthMm,
                    livePairId = queuedPairId,
                    liveVideoUri = pendingMeta?.videoUri,
                    liveDurationSec = pendingMeta?.durationSec ?: 0,
                    liveHasAudio = pendingMeta?.hasAudio ?: false,
                    shutterOffsetMs = pendingMeta?.shutterOffsetMs ?: 0L,
                    aiTag = if (isPortrait) "Portrait Bokeh" else null
                ) to pendingMeta
            }
            captureWatchdogJob?.cancel()
            saveRes.first.onSuccess { saved ->
                val pendingMeta = saveRes.second
                _runtimeState.value = _runtimeState.value.copy(
                    isCapturing = false,
                    statusBanner = when {
                        pendingMeta != null -> "Saved Live Photo (${pendingMeta.durationSec}s)"
                        isPortrait -> "Saved Portrait (${saved.width}x${saved.height})"
                        else -> "Captured ${saved.width}x${saved.height} ${ext.uppercase()}"
                    }
                )
            }.onFailure { err ->
                _runtimeState.value = _runtimeState.value.copy(
                    isCapturing = false,
                    errorMessage = err.localizedMessage
                )
            }
        }
    }

    private fun processStillBytesForAspectAndPortrait(
        bytes: ByteArray,
        targetUprightAspect: Float?,
        isPortraitBokeh: Boolean,
        bokehStrength: Float,
        focusX: Float,
        focusY: Float
    ): Triple<ByteArray, Int, Int>? {
        return runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val bw = bounds.outWidth
            val bh = bounds.outHeight
            if (bw <= 0 || bh <= 0) return null

            val exifOrient = android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                .getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            val swapped = exifOrient == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                exifOrient == android.media.ExifInterface.ORIENTATION_ROTATE_270 ||
                exifOrient == android.media.ExifInterface.ORIENTATION_TRANSPOSE ||
                exifOrient == android.media.ExifInterface.ORIENTATION_TRANSVERSE
            val uprightW = if (swapped) bh else bw
            val uprightH = if (swapped) bw else bh
            val actualAspect = uprightW.toFloat() / uprightH.toFloat().coerceAtLeast(1f)
            val needsAspectCrop = targetUprightAspect != null && targetUprightAspect > 0f &&
                abs(actualAspect - targetUprightAspect) > 0.04f
            if (!needsAspectCrop && !isPortraitBokeh) {
                return null
            }

            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val orientMatrix = Matrix().apply {
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
            val uprightBmp = if (!orientMatrix.isIdentity) {
                android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, orientMatrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val cropped = if (needsAspectCrop && targetUprightAspect != null) {
                centerCropBitmapToAspect(uprightBmp, targetUprightAspect).also {
                    if (it !== uprightBmp) uprightBmp.recycle()
                }
            } else {
                uprightBmp
            }

            val finalBmp = if (isPortraitBokeh) {
                ImageProcessor.applyPortraitBokehEffect(
                    source = cropped,
                    bokehStrength = bokehStrength,
                    focusNormX = focusX,
                    focusNormY = focusY
                ).also {
                    if (it !== cropped) cropped.recycle()
                }
            } else {
                cropped
            }

            val out = ByteArrayOutputStream()
            finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 96, out)
            val outBytes = out.toByteArray()
            val outW = finalBmp.width
            val outH = finalBmp.height
            finalBmp.recycle()
            Triple(outBytes, outW, outH)
        }.getOrNull()
    }

    private fun tryFinalizeRawCapture() {
        val img = pendingRawImage ?: return
        val res = pendingRawCaptureResult ?: return
        val chars = activeCharacteristics ?: return
        pendingRawImage = null
        pendingRawCaptureResult = null

        scope.launch(Dispatchers.IO) {
            runCatching {
                val out = ByteArrayOutputStream()
                DngCreator(chars, res).use { dng ->
                    dng.writeImage(out, img)
                }
                val w = img.width
                val h = img.height
                img.close()
                mediaRepo.saveCapturedPhotoBytes(
                    bytes = out.toByteArray(),
                    mimeType = "image/x-adobe-dng",
                    extension = "dng",
                    width = w,
                    height = h,
                    lensLabel = activeLens?.displayLabel ?: "PRO RAW",
                    iso = res.get(CaptureResult.SENSOR_SENSITIVITY),
                    exposureTimeNs = res.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                    focalLengthMm = activeLens?.focalLengthMm,
                    isRawDng = true
                )
            }.onFailure {
                runCatching { img.close() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording() {
        val device = cameraDevice ?: return
        val tv = textureView ?: return
        val st = tv.surfaceTexture ?: return
        val lens = activeLens ?: return
        val handler = backgroundHandler ?: return

        if (_runtimeState.value.isRecording) return
        if (!mediaRepo.hasSufficientStorage(50L)) {
            _runtimeState.value = _runtimeState.value.copy(errorMessage = "Insufficient storage (< 50 MB free)")
            return
        }

        val isSlowMo = currentMode == CameraMode.SLOW_MOTION && currentSlowMoOption != null
        val isTimelapse = currentMode == CameraMode.TIMELAPSE

        val targetW: Int
        val targetH: Int
        val targetFps: Int
        if (isSlowMo) {
            val sm = currentSlowMoOption!!
            targetW = sm.width
            targetH = sm.height
            targetFps = sm.fps
        } else {
            val resPreset = currentSettings.videoResolution
            targetW = resPreset.width
            targetH = resPreset.height
            targetFps = currentSettings.videoFps
        }

        val bitrate = currentSettings.videoBitrateTier.bitsPerSecond
        val videoFile = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
        val gyroFile = File(context.cacheDir, "gyro_${System.currentTimeMillis()}.csv")

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        val includeAudio = !isSlowMo && !isTimelapse && !_runtimeState.value.isAudioMuted

        runCatching {
            if (includeAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(videoFile.absolutePath)
            recorder.setVideoEncodingBitRate(bitrate)
            recorder.setVideoFrameRate(targetFps)
            recorder.setVideoSize(targetW, targetH)
            val encoder = if (currentSettings.videoCodec == VideoCodecOption.HEVC) {
                MediaRecorder.VideoEncoder.HEVC
            } else {
                MediaRecorder.VideoEncoder.H264
            }
            recorder.setVideoEncoder(encoder)
            if (includeAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44100)
            }
            if (isTimelapse) {
                val captureFps = (30.0 / currentTimelapseSpeed.multiplier.toDouble()).coerceAtLeast(0.5)
                recorder.setCaptureRate(captureFps)
            }
            recorder.setOrientationHint(getJpegOrientation(lens))
            recorder.prepare()
        }.onFailure { err ->
            runCatching { recorder.release() }
            _runtimeState.value = _runtimeState.value.copy(errorMessage = "Recorder setup failed: ${err.localizedMessage}")
            return
        }

        mediaRecorder = recorder
        activeVideoOutputFile = videoFile
        activeGyroOutputFile = gyroFile
        activeVideoWidth = targetW
        activeVideoHeight = targetH
        activeVideoFps = targetFps
        activeVideoBitrate = bitrate

        runCatching { captureSession?.close() }
        captureSession = null

        val activePreviewSurface = getOrCreatePreviewSurface(st)
        val recSurface = recorder.surface

        if (isSlowMo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hsBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(activePreviewSurface)
                addTarget(recSurface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(targetFps, targetFps))
            }
            @Suppress("DEPRECATION")
            device.createConstrainedHighSpeedCaptureSession(
                listOf(activePreviewSurface, recSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        val hsSession = session as android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
                        val burstList = hsSession.createHighSpeedRequestList(hsBuilder.build())
                        hsSession.setRepeatingBurst(burstList, null, handler)
                        beginRecorderStart()
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        abortFailedRecording()
                    }
                },
                handler
            )
        } else {
            val recBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(activePreviewSurface)
                addTarget(recSurface)
                applyRequestParameters(this)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(targetFps, targetFps))
            }
            previewRequestBuilder = recBuilder
            @Suppress("DEPRECATION")
            device.createCaptureSession(
                listOf(activePreviewSurface, recSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(recBuilder.build(), repeatingCaptureCallback, handler)
                        beginRecorderStart()
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        abortFailedRecording()
                    }
                },
                handler
            )
        }
    }

    private fun beginRecorderStart() {
        runCatching {
            mediaRecorder?.start()
            sensorTelemetry.startGyroRecording()
            recordingStartMs = SystemClock.elapsedRealtime()
            pausedAccumulatedMs = 0L
            _runtimeState.value = _runtimeState.value.copy(
                isRecording = true,
                isRecordingPaused = false,
                recordingElapsedMs = 0L,
                statusBanner = "Recording ${activeVideoHeight}p @ ${activeVideoFps}fps"
            )
        }.onFailure { err ->
            abortFailedRecording(err.localizedMessage)
        }
    }

    private fun abortFailedRecording(reason: String? = null) {
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        activeVideoOutputFile?.delete()
        activeVideoOutputFile = null
        _runtimeState.value = _runtimeState.value.copy(
            isRecording = false,
            errorMessage = reason ?: "Could not configure video recording stream"
        )
        backgroundHandler?.post { createPreviewSession() }
    }

    fun pauseOrResumeVideoRecording() {
        val rec = mediaRecorder ?: return
        if (!_runtimeState.value.isRecording) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        if (_runtimeState.value.isRecordingPaused) {
            runCatching {
                rec.resume()
                pausedAccumulatedMs += SystemClock.elapsedRealtime() - pauseStartMs
                _runtimeState.value = _runtimeState.value.copy(isRecordingPaused = false)
            }
        } else {
            runCatching {
                rec.pause()
                pauseStartMs = SystemClock.elapsedRealtime()
                _runtimeState.value = _runtimeState.value.copy(isRecordingPaused = true)
            }
        }
    }

    fun tickRecordingClock() {
        val state = _runtimeState.value
        if (state.isRecording && !state.isRecordingPaused) {
            val elapsed = SystemClock.elapsedRealtime() - recordingStartMs - pausedAccumulatedMs
            _runtimeState.value = state.copy(recordingElapsedMs = elapsed.coerceAtLeast(0L))
        }
    }

    fun stopVideoRecording() {
        if (!_runtimeState.value.isRecording) return
        val rec = mediaRecorder
        val videoFile = activeVideoOutputFile
        val gyroFile = activeGyroOutputFile

        mediaRecorder = null
        activeVideoOutputFile = null
        activeGyroOutputFile = null

        val gyroPath = gyroFile?.let { sensorTelemetry.stopAndSaveGyroTrack(it) }

        _runtimeState.value = _runtimeState.value.copy(
            isRecording = false,
            isRecordingPaused = false,
            recordingElapsedMs = 0L
        )

        runCatching { rec?.stop() }
        runCatching { rec?.release() }

        backgroundHandler?.post { createPreviewSession() }

        if (videoFile != null && videoFile.exists() && videoFile.length() > 1024L) {
            scope.launch(Dispatchers.IO) {
                val res = mediaRepo.saveVideoFileToMediaStore(
                    sourceFile = videoFile,
                    width = activeVideoWidth,
                    height = activeVideoHeight,
                    fps = activeVideoFps,
                    bitrateBps = activeVideoBitrate,
                    lensLabel = activeLens?.displayLabel ?: "Video",
                    gyroTrackPath = gyroPath
                )
                runCatching { videoFile.delete() }
                res.onSuccess {
                    _runtimeState.value = _runtimeState.value.copy(
                        statusBanner = "Video saved (${activeVideoHeight}p ${activeVideoFps}fps)"
                    )
                }.onFailure { err ->
                    _runtimeState.value = _runtimeState.value.copy(errorMessage = err.localizedMessage)
                }
            }
        } else {
            runCatching { videoFile?.delete() }
            _runtimeState.value = _runtimeState.value.copy(errorMessage = "Recording was too short or interrupted")
        }
    }

    private fun analyzeYuvFrame(image: Image) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalysisTimestampMs < 130L) return
        lastAnalysisTimestampMs = now

        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val w = image.width
        val h = image.height

        val bins = IntArray(32)
        var totalSamples = 0
        var highlightClipped = 0
        var shadowClipped = 0

        val stepX = 4
        val stepY = 4
        for (y in 0 until h step stepY) {
            val rowBase = y * rowStride
            for (x in 0 until w step stepX) {
                val idx = rowBase + x * pixelStride
                if (idx < yBuffer.limit()) {
                    val lum = yBuffer.get(idx).toInt() and 0xFF
                    bins[(lum shr 3).coerceIn(0, 31)]++
                    if (lum >= 248) highlightClipped++
                    if (lum <= 8) shadowClipped++
                    totalSamples++
                }
            }
        }

        if (totalSamples > 0) {
            val maxBin = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
            _histogramBins.value = bins.map { it.toFloat() / maxBin.toFloat() }
            _clippingStats.value = (highlightClipped * 100f / totalSamples) to (shadowClipped * 100f / totalSamples)
        }

        if (currentProParams.focusPeakingEnabled) {
            val gridCols = 40
            val gridRows = 30
            val cellW = (w / gridCols).coerceAtLeast(2)
            val cellH = (h / gridRows).coerceAtLeast(2)
            val edgeGrid = FloatArray(gridCols * gridRows)
            for (gr in 0 until gridRows) {
                val py = (gr * cellH + cellH / 2).coerceIn(1, h - 2)
                for (gc in 0 until gridCols) {
                    val px = (gc * cellW + cellW / 2).coerceIn(1, w - 2)
                    val cIdx = py * rowStride + px * pixelStride
                    val rIdx = py * rowStride + (px + 1) * pixelStride
                    val dIdx = (py + 1) * rowStride + px * pixelStride
                    if (rIdx < yBuffer.limit() && dIdx < yBuffer.limit()) {
                        val c = yBuffer.get(cIdx).toInt() and 0xFF
                        val r = yBuffer.get(rIdx).toInt() and 0xFF
                        val d = yBuffer.get(dIdx).toInt() and 0xFF
                        val grad = (abs(r - c) + abs(d - c)) / 255f
                        edgeGrid[gr * gridCols + gc] = if (grad > 0.16f) grad.coerceIn(0f, 1f) else 0f
                    }
                }
            }
            _focusPeaking.value = FocusPeakingOverlayData(gridCols, gridRows, edgeGrid)
        }
    }

    private fun chooseOptimalPreviewSize(chars: CameraCharacteristics?, viewW: Int, viewH: Int): Size {
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return Size(1280, 720)
        val choices = map.getOutputSizes(SurfaceTexture::class.java) ?: return Size(1280, 720)
        val isVideoFamily = isVideoFamilyMode()
        val targetRatio = if (isVideoFamily) {
            16f / 9f
        } else {
            when (currentSettings.aspectRatio) {
                com.example.model.AspectRatioMode.RATIO_4_3 -> 4f / 3f
                com.example.model.AspectRatioMode.RATIO_16_9 -> 16f / 9f
                com.example.model.AspectRatioMode.RATIO_1_1 -> 4f / 3f
                com.example.model.AspectRatioMode.FULL_SCREEN -> {
                    if (viewW > 0 && viewH > 0) max(viewW, viewH).toFloat() / min(viewW, viewH).toFloat() else 16f / 9f
                }
            }
        }
        val bounded = choices.filter { it.width <= 1920 && it.height <= 1080 }
        val pool = bounded.ifEmpty { choices.toList() }
        return pool.minByOrNull { size ->
            val r = size.width.toFloat() / size.height.toFloat().coerceAtLeast(1f)
            abs(r - targetRatio) * 1000f - (size.width * size.height) / 1_000_000f
        } ?: choices.last()
    }

    private fun configureTextureTransform(viewWidth: Int, viewHeight: Int) {
        val tv = textureView ?: return
        if (viewWidth == 0 || viewHeight == 0) return
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (displayRotationDegrees == 90 || displayRotationDegrees == 270) {
            val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((displayRotationDegrees - 180).toFloat(), centerX, centerY)
        } else {
            // Portrait (0 or 180): buffer is naturally upright (height x width for 90/270 sensor)
            val sensorSwapped = (activeLens?.sensorOrientation ?: 90) % 180 != 0
            val bufW = (if (sensorSwapped) previewSize.height else previewSize.width).toFloat()
            val bufH = (if (sensorSwapped) previewSize.width else previewSize.height).toFloat()
            val k = max(viewWidth.toFloat() / bufW, viewHeight.toFloat() / bufH)
            val scaleX = (k * bufW) / viewWidth.toFloat()
            val scaleY = (k * bufH) / viewHeight.toFloat()
            matrix.setScale(scaleX, scaleY, centerX, centerY)
            if (displayRotationDegrees == 180) {
                matrix.postRotate(180f, centerX, centerY)
            }
        }
        tv.setTransform(matrix)
    }

    private fun getJpegOrientation(lens: CameraLensInfo): Int {
        return computeJpegOrientation(
            sensorOrientation = lens.sensorOrientation,
            lensFacing = lens.lensFacing,
            displayRotationDegrees = displayRotationDegrees
        )
    }

    companion object {
        fun computeJpegOrientation(
            sensorOrientation: Int,
            lensFacing: Int,
            displayRotationDegrees: Int
        ): Int {
            val sign = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) 1 else -1
            return (sensorOrientation + displayRotationDegrees * sign + 360) % 360
        }

        fun centerCropBitmapToAspect(
            bitmap: android.graphics.Bitmap,
            targetAspect: Float
        ): android.graphics.Bitmap {
            val curW = bitmap.width
            val curH = bitmap.height
            if (curW <= 0 || curH <= 0 || targetAspect <= 0f) return bitmap
            val curAspect = curW.toFloat() / curH.toFloat()
            if (abs(curAspect - targetAspect) <= 0.02f) return bitmap
            val cropW: Int
            val cropH: Int
            if (curAspect > targetAspect) {
                cropH = curH
                cropW = (curH * targetAspect).roundToInt().coerceIn(1, curW)
            } else {
                cropW = curW
                cropH = (curW / targetAspect).roundToInt().coerceIn(1, curH)
            }
            val x = ((curW - cropW) / 2).coerceAtLeast(0)
            val y = ((curH - cropH) / 2).coerceAtLeast(0)
            return android.graphics.Bitmap.createBitmap(bitmap, x, y, cropW, cropH)
        }
    }

    private fun kelvinToRggbGains(kelvin: Int): RggbChannelVector {
        val temp = (kelvin.coerceIn(2200, 9500) / 100.0)
        val red = if (temp <= 66) 2.55f else (329.698727446 * Math.pow(temp - 60, -0.1332047592) / 100.0).toFloat()
        val green = if (temp <= 66) {
            ((99.4708025861 * kotlin.math.ln(temp) - 161.1195681661) / 150.0).toFloat()
        } else {
            (288.1221695283 * Math.pow(temp - 60, -0.0755148492) / 150.0).toFloat()
        }
        val blue = if (temp >= 66) 2.4f else if (temp <= 19) 0.4f else ((138.5177312231 * kotlin.math.ln(temp - 10) - 305.0447927307) / 120.0).toFloat()
        return RggbChannelVector(
            red.coerceIn(0.6f, 3.0f),
            green.coerceIn(0.7f, 1.5f),
            green.coerceIn(0.7f, 1.5f),
            blue.coerceIn(0.6f, 3.0f)
        )
    }

    fun clearStatusMessages() {
        _runtimeState.value = _runtimeState.value.copy(statusBanner = null, errorMessage = null)
    }
}
