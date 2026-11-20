package com.example.model

enum class MainNavDestination(val route: String, val label: String) {
    CAMERA("camera", "Camera"),
    GALLERY("gallery", "Gallery"),
    AI_STUDIO("ai_studio", "AI Studio"),
    SETTINGS("settings", "Settings")
}

enum class CameraMode(val label: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    PORTRAIT("CHÂN DUNG"),
    PRO("PRO"),
    SLOW_MOTION("SLOW-MO"),
    TIMELAPSE("TIMELAPSE");

    companion object {
        val primaryBarModes: List<CameraMode> = listOf(PHOTO, VIDEO, PORTRAIT)
        val extraMoreModes: List<CameraMode> = listOf(PRO, SLOW_MOTION, TIMELAPSE)
    }
}

enum class ProCaptureType(val label: String) {
    PHOTO("ẢNH"),
    VIDEO("VIDEO")
}

enum class AspectRatioMode(val label: String, val ratioWidth: Float, val ratioHeight: Float) {
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_1_1("1:1", 1f, 1f),
    FULL_SCREEN("FULL", 9f, 19.5f)
}

enum class FlashMode(val label: String) {
    OFF("OFF"),
    AUTO("AUTO"),
    ON("ON"),
    TORCH("TORCH")
}

enum class LivePhotoDuration(val seconds: Int, val label: String) {
    OFF(0, "OFF"),
    TWO_SEC(2, "2s"),
    THREE_SEC(3, "3s"),
    FIVE_SEC(5, "5s")
}

enum class SelfTimerDuration(val seconds: Int, val label: String) {
    OFF(0, "OFF"),
    THREE(3, "3s"),
    FIVE(5, "5s"),
    TEN(10, "10s")
}

enum class GridType(val label: String) {
    NONE("Off"),
    RULE_OF_THIRDS("3x3"),
    GOLDEN_RATIO("Golden"),
    CENTER_CROSS("Cross")
}

enum class PhotoFormat(val label: String) {
    JPEG("JPEG"),
    HEIC("HEIC"),
    RAW_DNG("RAW/DNG")
}

enum class PhotoResolutionTier(val label: String) {
    MAX("Maximum"),
    STANDARD("Standard")
}

enum class VideoResolutionPreset(val label: String, val width: Int, val height: Int) {
    RES_720P("720p", 1280, 720),
    RES_1080P("1080p", 1920, 1080),
    RES_4K("4K", 3840, 2160)
}

enum class VideoCodecOption(val label: String, val mimeType: String) {
    H264("H.264", "video/avc"),
    HEVC("HEVC", "video/hevc")
}

enum class VideoBitrateTier(val label: String, val bitsPerSecond: Int) {
    STANDARD("Standard", 16_000_000),
    HIGH("High", 35_000_000),
    PRO_MASTER("Pro 60M", 60_000_000)
}

enum class StabilizationMode(val label: String) {
    OFF("Off"),
    OIS("OIS"),
    EIS("EIS"),
    HYBRID("OIS+EIS")
}

enum class WhiteBalancePreset(val label: String, val camera2Mode: Int, val kelvinApprox: Int) {
    AUTO("Auto", 1, 5500),
    DAYLIGHT("Day", 5, 5600),
    CLOUDY("Cloud", 6, 6500),
    TUNGSTEN("Tung", 3, 3200),
    FLUORESCENT("Fluor", 2, 4200),
    MANUAL("Kelvin", 0, 5200)
}

enum class TimelapseSpeed(val multiplier: Int, val label: String) {
    X2(2, "2x"),
    X5(5, "5x"),
    X10(10, "10x"),
    X20(20, "20x")
}

enum class AiProcessingMode(val label: String, val subtitle: String) {
    FAST("FAST", "Basic enhancement, low power & latency"),
    BALANCED("BALANCED", "Improved denoise, sharpness & stabilization"),
    QUALITY("QUALITY", "Multi-pass temporal denoise & high-bitrate export")
}

enum class AiToolType(val label: String, val isVideoOnly: Boolean, val isPhotoCompatible: Boolean) {
    ENHANCE_ALL("Enhance All", false, true),
    DENOISE("AI Denoise", true, true),
    SHARPEN("AI Sharpen", true, true),
    UPSCALE("AI Super-Res 2x", true, true),
    COLOR_CORRECTION("AI Color", true, true),
    VIDEO_STABILIZE("AI Stabilization", true, false),
    FRAME_ENHANCE("AI Frame Enhance", true, false),
    FRAME_INTERPOLATE("Frame Interp 60fps", true, false)
}

enum class PhotoFilterPreset(val label: String) {
    NATURAL("Natural"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    FILM("Film"),
    VINTAGE("Vintage"),
    CINEMATIC("Cinematic"),
    BW("B&W")
}

enum class CropAspectPreset(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    SIXTEEN_NINE("16:9", 16f / 9f)
}

enum class AccentColorPreset(val label: String) {
    LEICA_AMBER("Amber Gold"),
    ELECTRIC_CYAN("Cyan"),
    CRIMSON_PRO("Crimson"),
    EMERALD("Emerald")
}

enum class GalleryCategory(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    LIVE_PHOTOS("Live"),
    FAVORITES("Favorites"),
    RECENTLY_DELETED("Trash")
}

enum class MediaItemType {
    PHOTO,
    VIDEO,
    LIVE_PHOTO,
    RAW_DNG
}

data class CameraLensInfo(
    val cameraId: String,
    val physicalId: String? = null,
    val lensFacing: Int,
    val displayLabel: String,
    val focalLengthMm: Float,
    val aperture: Float,
    val sensorOrientation: Int,
    val hardwareLevelLabel: String,
    val hasFlash: Boolean,
    val supportsManualSensor: Boolean,
    val supportsRaw: Boolean,
    val supportsHeic: Boolean,
    val supportsOis: Boolean,
    val supportsEis: Boolean,
    val supportsHdrScene: Boolean,
    val minZoom: Float,
    val maxZoom: Float,
    val isoRange: IntRange,
    val exposureTimeRangeNs: LongRange,
    val evRange: IntRange,
    val evStep: Float,
    val minFocusDistance: Float,
    val maxPhotoWidth: Int,
    val maxPhotoHeight: Int,
    val standardPhotoWidth: Int,
    val standardPhotoHeight: Int,
    val supportedVideoProfiles: List<VideoProfileOption>,
    val supportedSlowMoModes: List<SlowMoOption>,
    val sensorPhysicalWidthMm: Float = 5.76f,
    val sensorPhysicalHeightMm: Float = 4.29f,
    val equivalentFocalLengthMm: Int = 24,
    val opticalZoomRatio: Float = 1.0f,
    val isLogicalMultiCamera: Boolean = false
)

data class ZoomStopPreset(
    val label: String,
    val targetZoomRatio: Float,
    val equivalentFocalMm: Int,
    val isOpticalLens: Boolean,
    val backingLens: CameraLensInfo
)

data class VideoProfileOption(
    val resolution: VideoResolutionPreset,
    val supportedFps: List<Int>
)

data class SlowMoOption(
    val width: Int,
    val height: Int,
    val fps: Int
) {
    val label: String get() = "${height}p ${fps}fps"
}

data class DeviceCapabilityReport(
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val cpuCores: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val thermalStatusLabel: String,
    val thermalLevel: Int,
    val hardwareVideoEncoders: List<String>,
    val supportsHevcEncode: Boolean,
    val lenses: List<CameraLensInfo>
)

data class GyroSample(
    val timestampNs: Long,
    val wx: Float,
    val wy: Float,
    val wz: Float
)

data class ProCameraParameters(
    val autoExposure: Boolean = true,
    val iso: Int = 200,
    val exposureTimeNs: Long = 8_333_333L, // 1/120s
    val evSteps: Int = 0,
    val whiteBalance: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val manualKelvin: Int = 5200,
    val autoFocus: Boolean = true,
    val focusDistance: Float = 0f,
    val focusPeakingEnabled: Boolean = false,
    val exposureWarningEnabled: Boolean = false,
    val captureRawDng: Boolean = false
)

data class LiveTelemetryState(
    val luminanceBins: List<Float> = List(32) { 0f },
    val highlightClipPercent: Float = 0f,
    val shadowClipPercent: Float = 0f,
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val isLevelAligned: Boolean = false,
    val focusPeakScore: Float = 0f,
    val activeIso: Int? = null,
    val activeExposureNs: Long? = null,
    val livePhotoBufferHealth: String = "Standby",
    val freeStorageMb: Long = 0L
)

data class StickerItem(
    val id: String,
    val label: String,
    val isBadgeStamp: Boolean = false,
    val normX: Float = 0.5f,
    val normY: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotationDeg: Float = 0f
)

data class MemeCaptionConfig(
    val topText: String = "",
    val bottomText: String = "",
    val textColorArgb: Int = android.graphics.Color.WHITE,
    val fontSizeScale: Float = 1.0f
)

enum class VideoMergeLayout(val label: String, val subtitle: String) {
    SEQUENTIAL("Sequential Merge", "Combine 2+ clips back-to-back into one continuous video"),
    SIDE_BY_SIDE("Split Side-by-Side", "Combine 2 videos side-by-side in a single frame"),
    TOP_BOTTOM("Split Top & Bottom", "Stack 2 videos vertically in a single frame")
}

data class PhotoEditAdjustments(
    val brightness: Float = 0f,       // -1f..1f
    val contrast: Float = 1f,         // 0.5f..1.8f
    val saturation: Float = 1f,       // 0f..2f
    val exposure: Float = 0f,         // -2f..2f EV stops
    val highlights: Float = 0f,       // -1f..1f
    val shadows: Float = 0f,          // -1f..1f
    val temperature: Float = 0f,      // -1f..1f
    val tint: Float = 0f,             // -1f..1f
    val sharpness: Float = 0f,        // 0f..1f
    val rotationDegrees: Int = 0,     // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropPreset: CropAspectPreset = CropAspectPreset.FREE,
    val filter: PhotoFilterPreset = PhotoFilterPreset.NATURAL,
    val stickers: List<StickerItem> = emptyList(),
    val memeCaption: MemeCaptionConfig = MemeCaptionConfig()
)

data class VideoEditConfig(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val targetResolution: VideoResolutionPreset = VideoResolutionPreset.RES_1080P,
    val targetFps: Int = 30,
    val audioVolume: Float = 1f,
    val muteAudio: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val filter: PhotoFilterPreset = PhotoFilterPreset.NATURAL,
    val applyStabilization: Boolean = false,
    val applyDenoise: Boolean = false,
    val applySharpen: Boolean = false,
    val applyAiColor: Boolean = false
)

data class AiProcessingProgress(
    val isRunning: Boolean = false,
    val tool: AiToolType = AiToolType.ENHANCE_ALL,
    val mode: AiProcessingMode = AiProcessingMode.BALANCED,
    val percent: Int = 0,
    val stage: String = "",
    val estimatedStatus: String = "",
    val thermalAdvisedMode: AiProcessingMode? = null,
    val errorMessage: String? = null
)
