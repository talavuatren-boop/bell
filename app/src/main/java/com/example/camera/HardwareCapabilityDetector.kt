package com.example.camera

import android.app.ActivityManager
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.media.MediaCodecInfo
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
import kotlin.math.roundToInt

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
        val listedIds = runCatching { cameraManager.cameraIdList }.getOrDefault(emptyArray()).toList()
        val listedSet = listedIds.toSet()
        // Some OEM HALs (e.g. Xiaomi HyperOS/MIUI) keep ultrawide/tele IDs outside cameraIdList
        val candidateIds = (listedIds + listOf("2", "3", "4", "5", "20", "21", "22", "23")).distinct()

        for (id in candidateIds) {
            val chars = runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val isListed = id in listedSet
            val lensInfo = parseCameraCharacteristics(id, null, chars, isListedLogicalCamera = isListed)
            val isHiddenId = !isListed
            if (isHiddenId) {
                val mp = lensInfo.maxPhotoWidth.toLong() * lensInfo.maxPhotoHeight.toLong()
                val alreadyExists = lenses.any {
                    it.lensFacing == lensInfo.lensFacing &&
                        kotlin.math.abs(it.focalLengthMm - lensInfo.focalLengthMm) < 0.15f &&
                        kotlin.math.abs(it.aperture - lensInfo.aperture) < 0.15f
                }
                // Ignore depth/IR/2MP macro hidden IDs and exact duplicates
                if (alreadyExists || mp < 4_000_000L) continue
            }
            lenses.add(lensInfo)

            // Also inspect physical sub-cameras when logical multi-camera is exposed (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val physicalIds = runCatching { chars.physicalCameraIds }.getOrDefault(emptySet())
                for (physId in physicalIds) {
                    if (lenses.none { it.cameraId == physId || it.physicalId == physId }) {
                        val physChars = runCatching { cameraManager.getCameraCharacteristics(physId) }.getOrNull()
                        if (physChars != null) {
                            val physInfo = parseCameraCharacteristics(id, physId, physChars, isListedLogicalCamera = false)
                            val physMp = physInfo.maxPhotoWidth.toLong() * physInfo.maxPhotoHeight.toLong()
                            if (physMp >= 4_000_000L) {
                                lenses.add(physInfo)
                            }
                        }
                    }
                }
            }
        }

        // Identify true primary 1x main rear camera BEFORE sorting (never let 0.6x ultrawide sort first!)
        val backPrimaryPool = lenses.filter {
            it.lensFacing == CameraCharacteristics.LENS_FACING_BACK && it.physicalId == null
        }
        val primaryMainBack = backPrimaryPool.firstOrNull { it.cameraId == "0" }
            ?: backPrimaryPool
                .filter { it.equivalentFocalLengthMm in 21..32 }
                .maxByOrNull { it.maxPhotoWidth.toLong() * it.maxPhotoHeight.toLong() }
            ?: backPrimaryPool.maxByOrNull { it.maxPhotoWidth.toLong() * it.maxPhotoHeight.toLong() }
            ?: lenses.firstOrNull { it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }

        val mainBackEqFocal = primaryMainBack?.equivalentFocalLengthMm ?: 24

        val normalizedLenses = lenses.map { info ->
            if (info.lensFacing != CameraCharacteristics.LENS_FACING_BACK) {
                info.copy(opticalZoomRatio = 1.0f)
            } else {
                val isPrimary = primaryMainBack != null &&
                    info.cameraId == primaryMainBack.cameraId &&
                    info.physicalId == primaryMainBack.physicalId
                val rawRatio = info.equivalentFocalLengthMm.toFloat() / mainBackEqFocal.coerceAtLeast(1).toFloat()
                val normalized = when {
                    isPrimary -> 1.0f
                    rawRatio in 0.85f..1.18f -> 1.0f
                    rawRatio < 0.54f -> 0.5f
                    rawRatio < 0.72f -> 0.6f
                    rawRatio < 0.85f -> (Math.round(rawRatio * 10f) / 10f).coerceIn(0.4f, 0.8f)
                    else -> (Math.round(rawRatio * 10f) / 10f).coerceIn(1.2f, 20f)
                }
                val upgradedLabel = when {
                    isPrimary || (info.physicalId == null && normalized == 1.0f) -> "1x"
                    normalized < 0.85f -> "${normalized}x"
                    normalized >= 1.8f && normalized == normalized.toInt().toFloat() -> "${normalized.toInt()}x"
                    normalized >= 1.5f -> "${normalized}x"
                    else -> info.displayLabel
                }
                info.copy(
                    opticalZoomRatio = normalized,
                    displayLabel = upgradedLabel
                )
            }
        }

        // Sort cleanly: Primary 1x Rear first, then other rear lenses by zoom ratio, then Front
        val sortedLenses = normalizedLenses.sortedWith(
            compareBy<CameraLensInfo> {
                when (it.lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> 0
                    CameraCharacteristics.LENS_FACING_FRONT -> 1
                    else -> 2
                }
            }.thenByDescending {
                primaryMainBack != null &&
                    it.cameraId == primaryMainBack.cameraId &&
                    it.physicalId == primaryMainBack.physicalId
            }.thenBy { it.physicalId != null }
                .thenBy { it.opticalZoomRatio }
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
        chars: CameraCharacteristics,
        isListedLogicalCamera: Boolean
    ): CameraLensInfo {
        val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val primaryFocal = focalLengths?.firstOrNull() ?: 4.7f
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        val primaryAperture = apertures?.firstOrNull() ?: 1.8f
        val sensorSizeMm = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val sensorW = sensorSizeMm?.width ?: 5.76f
        val sensorH = sensorSizeMm?.height ?: 4.29f
        val eqFocalMm = compute35mmEquivalentFocalMm(primaryFocal, sensorW, sensorH)
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
        val isLogicalMultiCam = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
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
        val videoProfiles = detectVideoProfiles(logicalId, isListedLogicalCamera, hwLevel, map, fpsRanges)
        val slowMoOptions = detectSlowMoOptions(map)

        val displayLabel = when {
            facing == CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            eqFocalMm < 19 -> "0.6x"
            eqFocalMm > 42 -> "${((eqFocalMm / 24f) * 10f).roundToInt() / 10f}x"
            else -> if (physicalId == null) "1x" else "Lens $physicalId"
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
            supportedSlowMoModes = slowMoOptions,
            sensorPhysicalWidthMm = sensorW,
            sensorPhysicalHeightMm = sensorH,
            equivalentFocalLengthMm = eqFocalMm,
            opticalZoomRatio = 1.0f,
            isLogicalMultiCamera = isLogicalMultiCam
        )
    }

    companion object {
        fun compute35mmEquivalentFocalMm(focalMm: Float, sensorWidthMm: Float, sensorHeightMm: Float): Int {
            if (focalMm <= 0f || sensorWidthMm <= 0f || sensorHeightMm <= 0f) return 24
            val diag = kotlin.math.hypot(sensorWidthMm.toDouble(), sensorHeightMm.toDouble())
            return ((focalMm * 43.2666) / diag).toInt().coerceIn(8, 600)
        }

        fun buildZoomStopPresets(
            allLenses: List<CameraLensInfo>,
            activeLens: CameraLensInfo?
        ): List<com.example.model.ZoomStopPreset> {
            val active = activeLens ?: return emptyList()
            if (active.lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                val list = mutableListOf(
                    com.example.model.ZoomStopPreset(
                        label = "1x",
                        targetZoomRatio = 1.0f,
                        equivalentFocalMm = active.equivalentFocalLengthMm,
                        isOpticalLens = true,
                        backingLens = active
                    )
                )
                if (active.maxZoom >= 1.95f) {
                    list.add(
                        com.example.model.ZoomStopPreset(
                            label = "2x",
                            targetZoomRatio = 2.0f,
                            equivalentFocalMm = active.equivalentFocalLengthMm * 2,
                            isOpticalLens = false,
                            backingLens = active
                        )
                    )
                }
                return list
            }

            val backLenses = allLenses.filter { it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }
            val mainLens = backLenses.firstOrNull { it.physicalId == null && it.opticalZoomRatio in 0.9f..1.15f }
                ?: backLenses.firstOrNull { it.opticalZoomRatio in 0.9f..1.15f }
                ?: active
            val mainEq = mainLens.equivalentFocalLengthMm
            val stops = mutableListOf<com.example.model.ZoomStopPreset>()

            // 1. Ultrawide (< 0.85x): prefer CONTROL_ZOOM_RATIO on logical camera if supported, else dedicated physical UW lens
            val uwPhysical = backLenses
                .filter { it.opticalZoomRatio < 0.85f && it.maxPhotoWidth >= 1920 }
                .minByOrNull { it.opticalZoomRatio }
            if (mainLens.minZoom <= 0.78f) {
                val rawMin = mainLens.minZoom.coerceIn(0.5f, 0.75f)
                val rounded = (Math.round(rawMin * 10f) / 10f).coerceIn(0.5f, 0.7f)
                stops.add(
                    com.example.model.ZoomStopPreset(
                        label = "${rounded}x",
                        targetZoomRatio = rounded,
                        equivalentFocalMm = (mainEq * rounded).toInt().coerceAtLeast(10),
                        isOpticalLens = true,
                        backingLens = mainLens
                    )
                )
            } else if (uwPhysical != null) {
                val uwRatio = uwPhysical.opticalZoomRatio.coerceIn(0.5f, 0.8f)
                stops.add(
                    com.example.model.ZoomStopPreset(
                        label = "${uwRatio}x",
                        targetZoomRatio = 1.0f,
                        equivalentFocalMm = uwPhysical.equivalentFocalLengthMm,
                        isOpticalLens = true,
                        backingLens = uwPhysical
                    )
                )
            }

            // 2. Primary 1x Main Wide lens
            stops.add(
                com.example.model.ZoomStopPreset(
                    label = "1x",
                    targetZoomRatio = 1.0f,
                    equivalentFocalMm = mainEq,
                    isOpticalLens = true,
                    backingLens = mainLens
                )
            )

            // 3. Dedicated 2x (physical 2x optical tele OR smooth 2x in-sensor/digital zoom on main lens)
            val tele2x = backLenses.firstOrNull { it.opticalZoomRatio in 1.75f..2.3f }
            if (tele2x != null && !(mainLens.isLogicalMultiCamera && mainLens.maxZoom >= 2.0f)) {
                stops.add(
                    com.example.model.ZoomStopPreset(
                        label = "2x",
                        targetZoomRatio = 1.0f,
                        equivalentFocalMm = tele2x.equivalentFocalLengthMm,
                        isOpticalLens = true,
                        backingLens = tele2x
                    )
                )
            } else if (mainLens.maxZoom >= 2.0f) {
                stops.add(
                    com.example.model.ZoomStopPreset(
                        label = "2x",
                        targetZoomRatio = 2.0f,
                        equivalentFocalMm = mainEq * 2,
                        isOpticalLens = tele2x != null,
                        backingLens = mainLens
                    )
                )
            }

            // 4. Optional higher optical tele (> 2.5x) or 5x telephoto shortcut when maxZoom >= 5x
            val highTele = backLenses.firstOrNull { it.opticalZoomRatio >= 2.6f }
            if (highTele != null) {
                val lbl = if (highTele.opticalZoomRatio == highTele.opticalZoomRatio.toInt().toFloat()) {
                    "${highTele.opticalZoomRatio.toInt()}x"
                } else {
                    "${highTele.opticalZoomRatio}x"
                }
                val useLogical = mainLens.isLogicalMultiCamera && mainLens.maxZoom >= highTele.opticalZoomRatio
                stops.add(
                    com.example.model.ZoomStopPreset(
                        label = lbl,
                        targetZoomRatio = if (useLogical) highTele.opticalZoomRatio else 1.0f,
                        equivalentFocalMm = highTele.equivalentFocalLengthMm,
                        isOpticalLens = true,
                        backingLens = if (useLogical) mainLens else highTele
                    )
                )
            } else if (mainLens.maxZoom >= 5.0f) {
                stops.add(
                    com.example.model.ZoomStopPreset(
                        label = "5x",
                        targetZoomRatio = 5.0f,
                        equivalentFocalMm = mainEq * 5,
                        isOpticalLens = false,
                        backingLens = mainLens
                    )
                )
            }

            return stops.distinctBy { it.label }
        }
    }

    private fun detectVideoProfiles(
        cameraId: String,
        isListedLogicalCamera: Boolean,
        hardwareLevel: Int,
        map: StreamConfigurationMap?,
        fpsRanges: Array<Range<Int>>
    ): List<VideoProfileOption> {
        if (map == null) {
            return listOf(
                VideoProfileOption(VideoResolutionPreset.RES_720P, listOf(30, 60)),
                VideoProfileOption(VideoResolutionPreset.RES_1080P, listOf(30, 60))
            )
        }
        val sizes = map.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
        val hsSizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        val camIdInt = cameraId.toIntOrNull()
        val maxAeFps = fpsRanges.maxOfOrNull { it.upper } ?: 30
        val supports24 = fpsRanges.any { it.upper >= 24 && it.lower <= 24 }

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
                val durationSupports60 = minFrameDurationNs in 1L..17_500_000L

                val hsSupports60 = hsSizes.any { hs ->
                    hs.width >= preset.width && hs.height >= preset.height &&
                        (runCatching {
                            map.getHighSpeedVideoFpsRangesFor(hs)?.any { it.upper >= 60 } == true
                        }.getOrDefault(false))
                }

                val encoderSupports60 = (preset == VideoResolutionPreset.RES_720P || preset == VideoResolutionPreset.RES_1080P) &&
                    hardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY &&
                    hwEncoderSupportsSizeAndFps(preset.width, preset.height, 60.0)

                val fastSupports60 = when (preset) {
                    VideoResolutionPreset.RES_720P, VideoResolutionPreset.RES_1080P -> {
                        maxAeFps >= 60 || durationSupports60 || hsSupports60 || encoderSupports60
                    }
                    VideoResolutionPreset.RES_4K -> {
                        (maxAeFps >= 60 && durationSupports60) || hsSupports60
                    }
                }

                // Only query MediaProfiles for standard listed logical cameras and only if earlier signals weren't already true,
                // avoiding spurious E/MediaProfiles native logs on unmapped qualities/auxiliary cameras.
                val supports60 = fastSupports60 || (
                    isListedLogicalCamera &&
                        camIdInt != null &&
                        camcorderSupports60Fps(camIdInt, cameraId, preset)
                    )

                val supportedForSize = buildList {
                    if (supports24) add(24)
                    add(30)
                    if (supports60) add(60)
                }.distinct()
                result.add(VideoProfileOption(preset, supportedForSize))
            }
        }
        if (result.isEmpty()) {
            result.add(VideoProfileOption(VideoResolutionPreset.RES_720P, listOf(30, 60)))
            result.add(VideoProfileOption(VideoResolutionPreset.RES_1080P, listOf(30, 60)))
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun camcorderSupports60Fps(camIdInt: Int, camIdStr: String, preset: VideoResolutionPreset): Boolean {
        return runCatching {
            val hsQuality = when (preset) {
                VideoResolutionPreset.RES_720P -> CamcorderProfile.QUALITY_HIGH_SPEED_720P
                VideoResolutionPreset.RES_1080P -> CamcorderProfile.QUALITY_HIGH_SPEED_1080P
                VideoResolutionPreset.RES_4K -> CamcorderProfile.QUALITY_HIGH_SPEED_2160P
            }
            val stdQuality = when (preset) {
                VideoResolutionPreset.RES_720P -> CamcorderProfile.QUALITY_720P
                VideoResolutionPreset.RES_1080P -> CamcorderProfile.QUALITY_1080P
                VideoResolutionPreset.RES_4K -> CamcorderProfile.QUALITY_2160P
            }

            if (CamcorderProfile.hasProfile(camIdInt, hsQuality)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val hsAll = CamcorderProfile.getAll(camIdStr, hsQuality)?.videoProfiles.orEmpty()
                    if (hsAll.any { it.frameRate >= 60 }) return@runCatching true
                }
                val p = CamcorderProfile.get(camIdInt, hsQuality)
                if (p.videoFrameRate >= 60) return@runCatching true
            }

            if (CamcorderProfile.hasProfile(camIdInt, stdQuality)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val stdAll = CamcorderProfile.getAll(camIdStr, stdQuality)?.videoProfiles.orEmpty()
                    if (stdAll.any { it.frameRate >= 60 }) return@runCatching true
                }
                val p = CamcorderProfile.get(camIdInt, stdQuality)
                if (p.videoFrameRate >= 60) return@runCatching true
            }
            false
        }.getOrDefault(false)
    }

    private fun hwEncoderSupportsSizeAndFps(width: Int, height: Int, fps: Double): Boolean {
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { info ->
                if (!info.isEncoder) return@any false
                val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    !info.name.startsWith("OMX.google.", ignoreCase = true)
                }
                if (!isHw) return@any false
                info.supportedTypes.any { type ->
                    if (type != "video/avc" && type != "video/hevc") return@any false
                    val caps = runCatching { info.getCapabilitiesForType(type)?.videoCapabilities }.getOrNull()
                    caps?.areSizeAndRateSupported(width, height, fps) == true
                }
            }
        }.getOrDefault(false)
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
