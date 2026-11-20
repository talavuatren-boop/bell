package com.example

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.camera.CameraEngine
import com.example.camera.HardwareCapabilityDetector
import com.example.data.HungCamDatabase
import com.example.data.MediaAssetEntity
import com.example.model.AiProcessingMode
import com.example.model.AiToolType
import com.example.model.CropAspectPreset
import com.example.model.MediaItemType
import com.example.model.MemeCaptionConfig
import com.example.model.PhotoEditAdjustments
import com.example.model.PhotoFilterPreset
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
}
