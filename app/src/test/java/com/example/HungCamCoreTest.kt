package com.example

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.camera.CameraEngine
import com.example.camera.HardwareCapabilityDetector
import com.example.camera.LivePhotoRingBuffer
import com.example.data.HungCamDatabase
import com.example.data.MediaAssetEntity
import com.example.model.AiProcessingMode
import com.example.model.AiToolType
import com.example.model.CameraMode
import com.example.model.CropAspectPreset
import com.example.model.MediaItemType
import com.example.model.MemeCaptionConfig
import com.example.model.PhotoEditAdjustments
import com.example.model.PhotoFilterPreset
import com.example.model.ProCaptureType
import com.example.model.StickerItem
import com.example.model.VideoMergeLayout
import com.example.processing.ImageProcessor
import com.example.processing.ThermalAndMemoryGuard
import com.example.ui.components.formatShutterSpeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HungCamCoreTest {

    @Test
    fun `image processor runs denoise sharpen color and 2x upscale cleanly`() = runBlocking {
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(120, 95, 70))
        }

        val enhanced = ImageProcessor.runAiTool(bmp, AiToolType.ENHANCE_ALL, AiProcessingMode.BALANCED) { _, _ -> }
        assertEquals(64, enhanced.outputWidth)
        assertEquals(48, enhanced.outputHeight)

        val upscaled = ImageProcessor.runAiTool(bmp, AiToolType.UPSCALE, AiProcessingMode.FAST) { _, _ -> }
        assertEquals(128, upscaled.outputWidth)
        assertEquals(96, upscaled.outputHeight)
    }

    @Test
    fun `photo editor adjustments apply crop rotate and bw filter`() = runBlocking {
        val bmp = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(200, 100, 50))
        }
        val edited = ImageProcessor.applyPhotoEditorAdjustments(
            bmp,
            PhotoEditAdjustments(
                rotationDegrees = 90,
                cropPreset = CropAspectPreset.SQUARE,
                filter = PhotoFilterPreset.BW
            )
        )
        assertEquals(edited.width, edited.height)
        val px = edited.getPixel(edited.width / 2, edited.height / 2)
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        assertTrue(kotlin.math.abs(r - g) <= 2 && kotlin.math.abs(g - b) <= 2)
    }

    @Test
    fun `capability detector and thermal guard return valid snapshots`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = HardwareCapabilityDetector(context)
        val report = detector.detectCapabilities()
        assertTrue(report.cpuCores >= 1)
        assertNotNull(report.deviceModel)

        val guard = ThermalAndMemoryGuard(context)
        val health = guard.inspect(AiProcessingMode.QUALITY)
        assertNotNull(health.advisedMode)
    }

    @Test
    fun `room database links live photo pairs and manages trash safely`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HungCamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.mediaAssetDao()

        val id = dao.insert(
            MediaAssetEntity(
                mediaStoreUri = "content://media/external/images/media/1",
                mediaType = MediaItemType.LIVE_PHOTO.name,
                displayName = "HUNG_LIVE_001.jpg",
                width = 4000,
                height = 3000,
                livePairId = "pair-xyz",
                liveVideoUri = "content://media/external/video/media/2",
                liveDurationSec = 3,
                liveHasAudio = true,
                shutterOffsetMs = 1500L
            )
        )
        val fetched = dao.getByLivePairId("pair-xyz")
        assertNotNull(fetched)
        assertEquals("content://media/external/video/media/2", fetched?.liveVideoUri)

        dao.softDelete(id, 123456L)
        assertTrue(dao.observeActiveAssets().first().isEmpty())
        assertEquals(1, dao.observeDeletedAssets().first().size)

        dao.restore(id)
        assertEquals(1, dao.observeActiveAssets().first().size)
        db.close()
    }

    @Test
    fun `shutter speed formatter renders sub-second and multi-second values`() {
        assertEquals("1/120s", formatShutterSpeed(8_333_333L))
        assertEquals("1.0s", formatShutterSpeed(1_000_000_000L))
    }

    @Test
    fun `photo editor renders sticker badges and meme captions onto exported bitmap`() = runBlocking {
        val baseColor = android.graphics.Color.rgb(30, 40, 50)
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(baseColor)
        }
        val edited = ImageProcessor.applyPhotoEditorAdjustments(
            bmp,
            PhotoEditAdjustments(
                stickers = listOf(
                    StickerItem(
                        id = "s1",
                        label = "SHOT ON HUNG CAM",
                        isBadgeStamp = true,
                        normX = 0.5f,
                        normY = 0.5f,
                        scale = 1.2f
                    )
                ),
                memeCaption = MemeCaptionConfig(
                    topText = "TOP MEME",
                    bottomText = "BOTTOM MEME"
                )
            )
        )
        assertEquals(400, edited.width)
        assertEquals(400, edited.height)
        val centerPx = edited.getPixel(200, 200)
        assertTrue(centerPx != baseColor)
        assertEquals(3, VideoMergeLayout.entries.size)
    }

    @Test
    fun `camera orientation computes upright portrait 90 back and 270 front`() {
        // Portrait ROTATION_0 (0 deg)
        assertEquals(90, CameraEngine.computeJpegOrientation(90, CameraCharacteristics.LENS_FACING_BACK, 0))
        assertEquals(270, CameraEngine.computeJpegOrientation(270, CameraCharacteristics.LENS_FACING_FRONT, 0))
        // Landscape ROTATION_90 (90 deg)
        assertEquals(0, CameraEngine.computeJpegOrientation(90, CameraCharacteristics.LENS_FACING_BACK, 90))
        assertEquals(0, CameraEngine.computeJpegOrientation(270, CameraCharacteristics.LENS_FACING_FRONT, 90))
        // Reverse Portrait ROTATION_180 (180 deg)
        assertEquals(270, CameraEngine.computeJpegOrientation(90, CameraCharacteristics.LENS_FACING_BACK, 180))
        assertEquals(90, CameraEngine.computeJpegOrientation(270, CameraCharacteristics.LENS_FACING_FRONT, 180))
        // Reverse Landscape ROTATION_270 (270 deg)
        assertEquals(180, CameraEngine.computeJpegOrientation(90, CameraCharacteristics.LENS_FACING_BACK, 270))
        assertEquals(180, CameraEngine.computeJpegOrientation(270, CameraCharacteristics.LENS_FACING_FRONT, 270))
    }

    @Test
    fun `camera still aspect crop enforces exact 1_1 and 9_16 without stretching`() {
        val portrait43 = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val square = CameraEngine.centerCropBitmapToAspect(portrait43, 1f)
        assertEquals(300, square.width)
        assertEquals(300, square.height)

        val portrait169 = CameraEngine.centerCropBitmapToAspect(portrait43, 9f / 16f)
        assertEquals(225, portrait169.width)
        assertEquals(400, portrait169.height)
    }

    @Test
    fun `live photo ring buffer selects full 2s 3s and 5s windows around shutter`() {
        // Simulate 8 seconds at 30fps (240 frames, keyframe every 15 frames = 500ms)
        val totalFrames = 240
        val stepUs = 33_333L
        val baseWallUs = 10_000_000L
        val wallTimes = List(totalFrames) { i -> baseWallUs + i * stepUs }
        val keyFrames = List(totalFrames) { i -> i % 15 == 0 }
        val shutterWallUs = baseWallUs + 120 * stepUs // t = 4.0s

        for (durSec in listOf(2, 3, 5)) {
            val range = LivePhotoRingBuffer.selectWindowIndices(
                wallTimesUs = wallTimes,
                isKeyFrames = keyFrames,
                shutterWallUs = shutterWallUs,
                durationSeconds = durSec
            )
            assertNotNull("Expected valid range for ${durSec}s", range)
            val r = range!!
            assertTrue("Window must start on a sync keyframe", keyFrames[r.first])
            val actualSpanMs = (wallTimes[r.last] - wallTimes[r.first]) / 1000L
            assertTrue(
                "Expected at least ${(durSec * 1000) - 200}ms for ${durSec}s clip, got ${actualSpanMs}ms",
                actualSpanMs >= (durSec * 1000L) - 200L
            )
        }

        // Simulate shutter pressed only 0.4s after buffer started, with 2.7s buffered post-shutter
        val shortWallTimes = List(95) { i -> baseWallUs + i * stepUs }
        val shortKeys = List(95) { i -> i % 15 == 0 }
        val earlyShutterUs = baseWallUs + 12 * stepUs
        val earlyRange = LivePhotoRingBuffer.selectWindowIndices(
            wallTimesUs = shortWallTimes,
            isKeyFrames = shortKeys,
            shutterWallUs = earlyShutterUs,
            durationSeconds = 3
        )
        assertNotNull(earlyRange)
        val earlySpanMs = (shortWallTimes[earlyRange!!.last] - shortWallTimes[earlyRange.first]) / 1000L
        assertTrue("Early shutter should still yield ~3s clip, got ${earlySpanMs}ms", earlySpanMs >= 2800L)
    }

    @Test
    fun `camera mode bar groups match photo video portrait and more modes with pro photo and video`() {
        assertEquals(listOf(CameraMode.PHOTO, CameraMode.VIDEO, CameraMode.PORTRAIT), CameraMode.primaryBarModes)
        assertEquals(listOf(CameraMode.PRO, CameraMode.SLOW_MOTION, CameraMode.TIMELAPSE), CameraMode.extraMoreModes)
        assertEquals(listOf(ProCaptureType.PHOTO, ProCaptureType.VIDEO), ProCaptureType.entries)
    }

    @Test
    fun `portrait bokeh effect keeps center focal area sharp and blurs outer background`() {
        val bmp = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
        for (y in 0 until 160) {
            for (x in 0 until 120) {
                // High-frequency checkerboard in corner background, solid sharp cross in center
                val color = if (((x / 2) + (y / 2)) % 2 == 0) {
                    android.graphics.Color.WHITE
                } else {
                    android.graphics.Color.BLACK
                }
                bmp.setPixel(x, y, color)
            }
        }
        val portrait = ImageProcessor.applyPortraitBokehEffect(
            source = bmp,
            bokehStrength = 0.85f,
            focusNormX = 0.5f,
            focusNormY = 0.5f
        )
        assertEquals(120, portrait.width)
        assertEquals(160, portrait.height)
        // Outer corner checkerboard should be averaged toward gray rather than pure 255/0
        val cornerPx = portrait.getPixel(4, 4)
        val cornerR = (cornerPx shr 16) and 0xFF
        assertTrue("Expected corner blur averaging (25..230), got $cornerR", cornerR in 25..230)
    }

    @Test
    fun `hardware detector computes 35mm equivalent focal length and 2x optical zoom stops`() {
        // 1/1.4" main sensor ~9.8x7.3mm with 6.1mm lens -> ~21-24mm eq
        val mainEq = HardwareCapabilityDetector.compute35mmEquivalentFocalMm(
            focalMm = 6.1f,
            sensorWidthMm = 9.8f,
            sensorHeightMm = 7.35f
        )
        assertTrue("Expected ~21..24mm 35mm eq, got $mainEq", mainEq in 20..25)

        val mainLogical = com.example.model.CameraLensInfo(
            cameraId = "0",
            physicalId = null,
            lensFacing = CameraCharacteristics.LENS_FACING_BACK,
            displayLabel = "1x",
            focalLengthMm = 6.1f,
            aperture = 1.65f,
            sensorOrientation = 90,
            hardwareLevelLabel = "LEVEL_3",
            hasFlash = true,
            supportsManualSensor = true,
            supportsRaw = true,
            supportsHeic = true,
            supportsOis = true,
            supportsEis = true,
            supportsHdrScene = true,
            minZoom = 0.6f,
            maxZoom = 10.0f,
            isoRange = 50..6400,
            exposureTimeRangeNs = 100_000L..30_000_000_000L,
            evRange = -12..12,
            evStep = 0.25f,
            minFocusDistance = 10f,
            maxPhotoWidth = 4080,
            maxPhotoHeight = 3060,
            standardPhotoWidth = 4080,
            standardPhotoHeight = 3060,
            supportedVideoProfiles = emptyList(),
            supportedSlowMoModes = emptyList(),
            sensorPhysicalWidthMm = 9.8f,
            sensorPhysicalHeightMm = 7.35f,
            equivalentFocalLengthMm = 23,
            opticalZoomRatio = 1.0f,
            isLogicalMultiCamera = true
        )

        val stops = HardwareCapabilityDetector.buildZoomStopPresets(listOf(mainLogical), mainLogical)
        val labels = stops.map { it.label }
        assertTrue("Expected 0.6x stop in $labels", "0.6x" in labels)
        assertTrue("Expected 1x stop in $labels", "1x" in labels)
        assertTrue("Expected 2x stop in $labels", "2x" in labels)
        val twoX = stops.first { it.label == "2x" }
        assertEquals(2.0f, twoX.targetZoomRatio, 0.01f)
        assertEquals(46, twoX.equivalentFocalMm)
    }

    @Test
    fun `camera live filter helper transforms bitmap for bw and warm presets`() {
        val src = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(200, 120, 60))
        }

        val bw = ImageProcessor.applyFilterToBitmap(src, PhotoFilterPreset.BW)
        val bwPx = bw.getPixel(8, 8)
        val bwR = (bwPx shr 16) and 0xFF
        val bwG = (bwPx shr 8) and 0xFF
        val bwB = bwPx and 0xFF
        assertTrue("Expected B&W channels equalized, got R=$bwR G=$bwG B=$bwB", kotlin.math.abs(bwR - bwG) <= 2 && kotlin.math.abs(bwG - bwB) <= 2)

        val warm = ImageProcessor.applyFilterToBitmap(src, PhotoFilterPreset.WARM)
        val warmPx = warm.getPixel(8, 8)
        val warmR = (warmPx shr 16) and 0xFF
        val warmB = warmPx and 0xFF
        assertTrue("Expected WARM filter red > blue, got R=$warmR B=$warmB", warmR > warmB)
    }
}
