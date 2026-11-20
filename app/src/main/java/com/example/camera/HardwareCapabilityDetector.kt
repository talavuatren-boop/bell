package com.example.camera

import android.app.ActivityManager
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.os.Build
import android.os.PowerManager
import android.util.Range
import android.util.Size
import com.example.model.CameraLensInfo
import com.example.model.DeviceCapabilityReport
import com.example.model.SlowMoOption
import com.example.model.VideoProfileOption
import com.example.model.VideoResolutionPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HardwareCapabilityDetector(private val context: Context) {

    suspend fun detectCapabilities(): DeviceCapabilityReport = withContext(Dispatchers.Default) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)

        val (thermalLevel, thermalLabel) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = powerManager.currentThermalStatus
            status to thermalStatusName(status)
        } else {
            0 to "Nominal (API < 29)"
        }

        val encoders = detectHardwareVideoEncoders()
        val supportsHevc = encoders.any { it.contains("hevc", ignoreCase = true) || it.contains("h265", ignoreCase = true) }

        val lenses = mutableListOf<CameraLensInfo>()
        val cameraIds = runCatching { cameraManager.cameraIdList }.getOrDefault(emptyArray())

        for (id in cameraIds) {
            val chars = runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val lensInfo = parseCameraCharacteristics(id, null, chars)
            lenses.add(lensInfo)

            // Also inspect physical sub-cameras when logical multi-camera is exposed (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val physicalIds = runCatching { chars.physicalCameraIds }.getOrDefault(emptySet())
                for (physId in physicalIds) {
                    if (lenses.none { it.cameraId == physId || it.physicalId == physId }) {
                        val physChars = runCatching { cameraManager.getCameraCharacteristics(physId) }.getOrNull()
                        if (physChars != null) {
                            lenses.add(parseCameraCharacteristics(id, physId, physChars))
                        }
                    }
                }
            }
        }

        // Sort cleanly: Back main/wide/tele first, then Front
        val sortedLenses = lenses.sortedWith(
            compareBy<CameraLensInfo> {
                when (it.lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> 0
                    CameraCharacteristics.LENS_FACING_FRONT -> 1
                    else -> 2
                }
            }.thenBy { it.physicalId != null }
                .thenBy { it.focalLengthMm }
        )

        DeviceCapabilityReport(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            thermalStatusLabel = thermalLabel,
            thermalLevel = thermalLevel,
            hardwareVideoEncoders = encoders,
            supportsHevcEncode = supportsHevc,
            lenses = sortedLenses
        )
    }

    private fun parseCameraCharacteristics(
        logicalId: String,
        physicalId: String?,
        chars: CameraCharacteristics
    ): CameraLensInfo {
        val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val primaryFocal = focalLengths?.firstOrNull() ?: 4.7f
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        val primaryAperture = apertures?.firstOrNull() ?: 1.8f
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
        val hwLabel = when (hwLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "LEGACY"
        }

        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val supportsManual = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val supportsRaw = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: IntArray(0)
        val supportsOis = oisModes.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON)

        val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: IntArray(0)
        val supportsEis = eisModes.any {
            it == CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    it == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
        }

        val sceneModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES) ?: IntArray(0)
        val supportsHdr = sceneModes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_HDR)

        val zoomRange: Range<Float>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            null
        }
        val maxDigitalZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 4f
        val minZoom = zoomRange?.lower ?: 1.0f
        val maxZoom = zoomRange?.upper ?: maxDigitalZoom.coerceAtLeast(1.0f)

        val isoRangeRaw = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val isoRange = if (isoRangeRaw != null) isoRangeRaw.lower..isoRangeRaw.upper else 100..3200

        val expRangeRaw = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val expRange = if (expRangeRaw != null) expRangeRaw.lower..expRangeRaw.upper else 125_000L..1_000_000_000L

        val evRangeRaw = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evRange = if (evRangeRaw != null) evRangeRaw.lower..evRangeRaw.upper else -6..6
        val evStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.333f
        val minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.sortedByDescending { it.width * it.height } ?: emptyList()
        val maxPhoto = jpegSizes.firstOrNull() ?: Size(4000, 3000)
        val stdPhoto = jpegSizes.firstOrNull { it.width <= 4096 && it.height <= 3072 } ?: maxPhoto

        val supportsHeic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && map != null) {
            map.outputFormats.contains(ImageFormat.HEIC) && !map.getOutputSizes(ImageFormat.HEIC).isNullOrEmpty()
        } else {
            false
        }

        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val videoProfiles = detectVideoProfiles(map, fpsRanges)
        val slowMoOptions = detectSlowMoOptions(map)

        val displayLabel = when {
            facing == CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            physicalId != null && primaryFocal < 2.8f -> "0.6x UW"
            physicalId != null && primaryFocal > 7.0f -> "2x Tele"
            primaryFocal < 2.8f -> "0.6x UW"
            primaryFocal > 7.0f -> "Tele"
            else -> if (physicalId == null) "1x Main" else "Lens ${physicalId}"
        }

        return CameraLensInfo(
            cameraId = logicalId,
            physicalId = physicalId,
            lensFacing = facing,
            displayLabel = displayLabel,
            focalLengthMm = primaryFocal,
            aperture = primaryAperture,
            sensorOrientation = sensorOrientation,
            hardwareLevelLabel = hwLabel,
            hasFlash = hasFlash,
            supportsManualSensor = supportsManual,
            supportsRaw = supportsRaw,
            supportsHeic = supportsHeic,
            supportsOis = supportsOis,
            supportsEis = supportsEis,
            supportsHdrScene = supportsHdr,
            minZoom = minZoom,
            maxZoom = maxZoom,
            isoRange = isoRange,
            exposureTimeRangeNs = expRange,
            evRange = evRange,
            evStep = evStep,
            minFocusDistance = minFocusDist,
            maxPhotoWidth = maxPhoto.width,
            maxPhotoHeight = maxPhoto.height,
            standardPhotoWidth = stdPhoto.width,
            standardPhotoHeight = stdPhoto.height,
            supportedVideoProfiles = videoProfiles,
            supportedSlowMoModes = slowMoOptions
        )
    }

    private fun detectVideoProfiles(
        map: StreamConfigurationMap?,
        fpsRanges: Array<Range<Int>>
    ): List<VideoProfileOption> {
        if (map == null) {
            return listOf(VideoProfileOption(VideoResolutionPreset.RES_1080P, listOf(30)))
        }
        val sizes = map.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
        val maxAeFps = fpsRanges.maxOfOrNull { it.upper } ?: 30
        val candidateFps = buildList {
            if (fpsRanges.any { it.upper >= 24 && it.lower <= 24 }) add(24)
            add(30)
            if (maxAeFps >= 60) add(60)
        }.distinct()

        val result = mutableListOf<VideoProfileOption>()
        for (preset in VideoResolutionPreset.entries) {
            val matchedSize = sizes.firstOrNull {
                (it.width == preset.width && it.height == preset.height) ||
                    (it.width >= preset.width && it.height >= preset.height &&
                        it.width * 9 == it.height * 16)
            }
            if (matchedSize != null) {
                val minFrameDurationNs = runCatching {
                    map.getOutputMinFrameDuration(MediaRecorder::class.java, matchedSize)
                }.getOrDefault(0L)
                val maxSizeFps = if (minFrameDurationNs > 0L) {
                    (1_000_000_000L / minFrameDurationNs).toInt()
                } else {
                    maxAeFps
                }
                val supportedForSize = candidateFps.filter { it <= maxSizeFps }.ifEmpty { listOf(30) }
                result.add(VideoProfileOption(preset, supportedForSize))
            }
        }
        if (result.isEmpty()) {
            result.add(VideoProfileOption(VideoResolutionPreset.RES_1080P, listOf(30)))
        }
        return result
    }

    private fun detectSlowMoOptions(map: StreamConfigurationMap?): List<SlowMoOption> {
        if (map == null) return emptyList()
        val hsSizes = runCatching { map.highSpeedVideoSizes }.getOrNull() ?: return emptyList()
        val options = mutableListOf<SlowMoOption>()
        for (size in hsSizes) {
            val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(size) }.getOrNull() ?: continue
            for (range in ranges) {
                if (range.upper >= 120) {
                    options.add(SlowMoOption(size.width, size.height, range.upper))
                }
            }
        }
        return options.distinctBy { "${it.width}x${it.height}@${it.fps}" }
            .sortedWith(compareByDescending<SlowMoOption> { it.fps }.thenByDescending { it.height })
    }

    private fun detectHardwareVideoEncoders(): List<String> {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.startsWith("video/") }
        }.filter { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                !info.name.startsWith("OMX.google.", ignoreCase = true)
            }
        }.map { "${it.name} (${it.supportedTypes.filter { t -> t.startsWith("video/") }.joinToString()})" }
            .distinct()
    }

    private fun thermalStatusName(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Nominal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Heat"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Heat"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown Risk"
        else -> "Nominal"
    }
}
