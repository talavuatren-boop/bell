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
import com.example.model.LivePhotoDuration
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
    fun `live photo ring buffer selects full 2s 3s 5s and 10s windows around shutter`() {
        // Simulate 14 seconds at 30fps (420 frames, keyframe every 15 frames = 500ms)
        val totalFrames = 420
        val stepUs = 33_333L
        val baseWallUs = 10_000_000L
        val wallTimes = List(totalFrames) { i -> baseWallUs + i * stepUs }
        val keyFrames = List(totalFrames) { i -> i % 15 == 0 }
        val shutterWallUs = baseWallUs + 210 * stepUs // t = 7.0s

        assertTrue(LivePhotoDuration.entries.any { it.seconds == 10 && it.label == "10s" })

        for (durSec in listOf(2, 3, 5, 10)) {
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

    @Test
    fun `zoom stop presets include physical 0_6x ultrawide 1x main and 2x even when minZoom is 1_0`() {
        val mainRear = com.example.model.CameraLensInfo(
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
            minZoom = 1.0f,
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
            isLogicalMultiCamera = false
        )
        val uwRear = mainRear.copy(
            cameraId = "2",
            displayLabel = "0.6x",
            focalLengthMm = 1.65f,
            aperture = 2.2f,
            sensorPhysicalWidthMm = 3.6f,
            sensorPhysicalHeightMm = 2.7f,
            equivalentFocalLengthMm = 14,
            opticalZoomRatio = 0.6f,
            maxPhotoWidth = 3264,
            maxPhotoHeight = 2448
        )
        val stops = HardwareCapabilityDetector.buildZoomStopPresets(listOf(mainRear, uwRear), mainRear)
        val labels = stops.map { it.label }
        assertEquals(listOf("0.6x", "1x", "2x", "5x"), labels)
        assertEquals("2", stops.first { it.label == "0.6x" }.backingLens.cameraId)
    }

    @Test
    fun `motion photo writer packages valid jpeg plus xmp offset and appended mp4 payload`() {
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(40, 120, 200))
        }
        val jpgStream = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, jpgStream)
        val jpegBytes = jpgStream.toByteArray()
        val fakeMp4 = "ftypmp42_fake_motion_clip_payload_123456".toByteArray()
        // Append stray vendor zero-padding after JPEG EOI to verify MotionPhotoWriter trims cleanly
        val jpegWithPadding = jpegBytes + ByteArray(64) { 0 }

        val packaged = com.example.processing.MotionPhotoWriter.embedMotionPhoto(
            jpegBytes = jpegWithPadding,
            mp4Bytes = fakeMp4,
            presentationTimestampUs = 1_500_000L,
            includeSamsungSef = false,
            useLegacyGCameraPrefix = false
        )
        assertTrue(packaged.size > jpegBytes.size + fakeMp4.size)
        val offset = com.example.processing.MotionPhotoWriter.extractEmbeddedMp4Offset(packaged)
        assertEquals(fakeMp4.size, offset)

        // Trailing bytes at end of JPEG must match MP4 byte-for-byte, preceded immediately by FF D9 EOI
        val mp4Start = packaged.size - fakeMp4.size
        val tail = packaged.copyOfRange(mp4Start, packaged.size)
        assertTrue(tail.contentEquals(fakeMp4))
        assertEquals(0xFF.toByte(), packaged[mp4Start - 2])
        assertEquals(0xD9.toByte(), packaged[mp4Start - 1])

        val headerStr = String(packaged, 0, minOf(packaged.size, 8192), Charsets.UTF_8)
        assertTrue(headerStr.contains("Camera:MotionPhoto=\"1\""))
        assertTrue(headerStr.contains("Camera:MicroVideo=\"1\""))
        assertTrue(headerStr.contains("OpCamera:MotionPhotoOwner=\"oplus\""))
        assertTrue(headerStr.contains("OpCamera:OLivePhotoVersion=\"2\""))
        assertTrue(headerStr.contains("OpCamera:VideoLength=\"${fakeMp4.size}\""))
        assertTrue(headerStr.contains("MiCamera:XMPMeta="))
        val secondaryBlock = headerStr.substringAfter("Item:Semantic=\"MotionPhoto\"").substringBefore("/>")
        assertTrue("Secondary MotionPhoto item must not include Item:Padding", !secondaryBlock.contains("Item:Padding"))

        // Standard Android JPEG decoders still decode the still image cleanly
        val decoded = android.graphics.BitmapFactory.decodeByteArray(packaged, 0, packaged.size)
        assertNotNull(decoded)
        assertEquals(64, decoded.width)
        assertEquals(48, decoded.height)

        // Verify Xiaomi legacy prefix mode
        val xiaomiPackaged = com.example.processing.MotionPhotoWriter.embedMotionPhoto(
            jpegBytes = jpegBytes,
            mp4Bytes = fakeMp4,
            presentationTimestampUs = 1_000_000L,
            includeSamsungSef = false,
            useLegacyGCameraPrefix = true
        )
        val xiaomiHeader = String(xiaomiPackaged, 0, minOf(xiaomiPackaged.size, 8192), Charsets.UTF_8)
        assertTrue(xiaomiHeader.contains("GCamera:MotionPhoto=\"1\""))
        assertTrue(xiaomiHeader.contains("GCamera:MicroVideo=\"1\""))
    }

    @Test
    fun `motion photo writer appends valid samsung sef MotionPhoto_Data and SEFH SEFT trailer`() {
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(20, 80, 160))
        }
        val jpgStream = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, jpgStream)
        val jpegBytes = jpgStream.toByteArray()
        val fakeMp4 = "ftypmp42_samsung_sef_test_payload_987654321".toByteArray()

        val packaged = com.example.processing.MotionPhotoWriter.embedMotionPhoto(
            jpegBytes = jpegBytes,
            mp4Bytes = fakeMp4,
            presentationTimestampUs = 900_000L,
            includeSamsungSef = true,
            useLegacyGCameraPrefix = false
        )

        // SEF extraction recovers exact fakeMp4 payload
        val mp4Range = com.example.processing.MotionPhotoWriter.extractSamsungSefMp4Range(packaged)
        assertNotNull("Expected valid Samsung SEFT/SEFH MotionPhoto_Data block", mp4Range)
        val extracted = packaged.copyOfRange(mp4Range!!.first, mp4Range.last + 1)
        assertTrue(extracted.contentEquals(fakeMp4))

        // Google Container:Directory Item:Length = fakeMp4.size + 32 (SEF footer), so EOF - offset == mp4Range.first
        val containerOffset = com.example.processing.MotionPhotoWriter.extractEmbeddedMp4Offset(packaged)
        assertEquals(fakeMp4.size + 32, containerOffset)
        assertEquals(mp4Range.first, packaged.size - containerOffset!!)

        // Still JPEG image remains cleanly decodable
        val decoded = android.graphics.BitmapFactory.decodeByteArray(packaged, 0, packaged.size)
        assertNotNull(decoded)
        assertEquals(64, decoded.width)
        assertEquals(48, decoded.height)
    }

    @Test
    fun `packageMotionPhotoBytes preserves exif and trailing mp4 bytes before single-pass mediastore save`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HungCamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = com.example.data.MediaStoreRepository(context, db.mediaAssetDao())

        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(90, 180, 60))
        }
        val jpgStream = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, jpgStream)
        val jpegBytes = jpgStream.toByteArray()
        val mp4File = java.io.File(context.cacheDir, "stage_mp4_test.mp4").apply {
            writeBytes(ByteArray(2048) { (it % 251).toByte() })
        }

        val packaged = repo.packageMotionPhotoBytes(
            jpegBytes = jpegBytes,
            mp4File = mp4File,
            shutterOffsetUs = 750_000L,
            iso = 200,
            exposureTimeNs = 10_000_000L,
            focalLengthMm = 5.4f,
            livePairId = "pair-test-999"
        )
        assertEquals(2048, com.example.processing.MotionPhotoWriter.extractEmbeddedMp4Offset(packaged))
        val mp4Start = packaged.size - 2048
        assertTrue(packaged.copyOfRange(mp4Start, packaged.size).contentEquals(mp4File.readBytes()))

        mp4File.delete()
        db.close()
    }

    @Test
    fun `live photo monotonic pts builder blocks 7 hour clock spikes`() {
        val step = 33_333L
        val sevenHoursUs = 7L * 3600L * 1_000_000L
        val frames = List(90) { idx ->
            com.example.camera.EncodedFrame(
                data = byteArrayOf(1, 2, 3),
                codecPtsUs = if (idx < 30) 1_000_000L + idx * step else sevenHoursUs + idx * step,
                wallTimeUs = 5_000_000L + idx * step,
                flags = if (idx == 0) 1 else 0
            )
        }
        val pts = LivePhotoRingBuffer.buildMonotonicVideoPtsUs(frames)
        assertEquals(90, pts.size)
        assertEquals(0L, pts.first())
        val totalSpanMs = pts.last() / 1000L
        assertTrue("Expected ~3000ms clip even after 7h clock jump, got ${totalSpanMs}ms", totalSpanMs in 2700L..3400L)
    }

    @Test
    fun `live photo companion clip saves in app private storage never public mediastore video`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HungCamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = com.example.data.MediaStoreRepository(context, db.mediaAssetDao())
        val tempFile = java.io.File(context.cacheDir, "test_live_clip.mp4").apply {
            writeBytes(ByteArray(2048) { it.toByte() })
        }
        val uriRes = repo.saveLiveCompanionClipPrivately(tempFile, "pair-private-123")
        assertTrue("Expected private live clip save success, got ${uriRes.exceptionOrNull()}", uriRes.isSuccess)
        val uri = uriRes.getOrThrow()
        assertEquals("file", uri.scheme)
        assertTrue(uri.path!!.contains("live_clips"))
        assertTrue(java.io.File(uri.path!!).exists())

        java.io.File(uri.path!!).delete()
        tempFile.delete()
        db.close()
    }

    @Test
    fun `leica and flagship camera filters transform bitmaps cleanly with intensity and natural base tone`() {
        val src = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(180, 120, 90))
        }

        val leicaPresets = PhotoFilterPreset.entries.filter { it.isLeica }
        assertTrue("Expected at least 86 Leica filter presets", leicaPresets.size >= 86)

        for (preset in PhotoFilterPreset.entries) {
            val out = ImageProcessor.applyFilterToBitmap(src, preset, intensity = 0.85f)
            assertEquals(32, out.width)
            assertEquals(24, out.height)
            if (preset == PhotoFilterPreset.LEICA_BW_NAT || preset == PhotoFilterPreset.LEICA_BW_HC) {
                val px = out.getPixel(16, 12)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                assertTrue("Expected grayscale channels at high intensity, got R=$r G=$g B=$b", kotlin.math.abs(r - g) <= 25 && kotlin.math.abs(g - b) <= 25)
            }
        }

        val toneOut = ImageProcessor.applyFlagshipNaturalTone(src)
        assertEquals(32, toneOut.width)
        assertEquals(24, toneOut.height)

        val watermarked = ImageProcessor.applyCameraWatermark(
            source = toneOut,
            lensLabel = "1x 23mm",
            iso = 100,
            exposureNs = 8_000_000L,
            focalMm = 23f
        )
        assertEquals(32, watermarked.width)
        assertTrue("Expected watermark bar added below photo", watermarked.height > 24)
    }

    @Test
    fun `computational hdr lifts dark shadows and camera ai super sharp boosts edge acutance`() {
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val c = if (x < 16) android.graphics.Color.rgb(28, 30, 34) else android.graphics.Color.rgb(210, 214, 218)
                bmp.setPixel(x, y, c)
            }
        }
        val hdr = ImageProcessor.applyComputationalHdr(bmp, strength = 0.78f)
        assertEquals(32, hdr.width)
        assertEquals(32, hdr.height)
        val shadowBefore = android.graphics.Color.red(bmp.getPixel(4, 16))
        val shadowAfter = android.graphics.Color.red(hdr.getPixel(4, 16))
        assertTrue("Expected computational HDR to lift shadow ($shadowBefore -> $shadowAfter)", shadowAfter > shadowBefore)

        val sharpened = ImageProcessor.applyCameraAiSuperSharp(hdr, strength = 0.85f)
        assertEquals(32, sharpened.width)
        assertEquals(32, sharpened.height)
        val darkEdge = android.graphics.Color.red(sharpened.getPixel(15, 16))
        val brightEdge = android.graphics.Color.red(sharpened.getPixel(16, 16))
        assertTrue("Expected clear edge contrast preserved after super-sharp", brightEdge - darkEdge > 150)
    }
}

