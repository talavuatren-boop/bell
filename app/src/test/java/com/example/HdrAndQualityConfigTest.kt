package com.example

import com.example.data.HungCamSettings
import com.example.model.HdrMode
import com.example.model.PhotoResolutionTier
import com.example.model.StabilizationMode
import com.example.model.VideoBitrateTier
import com.example.model.VideoEditConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrAndQualityConfigTest {

    @Test
    fun `hdr modes include off auto and always with flagship labels`() {
        assertEquals(listOf(HdrMode.OFF, HdrMode.AUTO, HdrMode.ALWAYS), HdrMode.entries)
        assertEquals("HDR", HdrMode.OFF.badgeLabel)
        assertEquals("HDRᴀ", HdrMode.AUTO.badgeLabel)
        assertEquals("HDR+", HdrMode.ALWAYS.badgeLabel)
    }

    @Test
    fun `default flagship settings enable hdr always camera ai super sharp max resolution and ois`() {
        val defaults = HungCamSettings()
        assertEquals(HdrMode.ALWAYS, defaults.hdrMode)
        assertTrue(defaults.hdrEnabled)
        assertTrue(defaults.cameraAiSuperSharpEnabled)
        assertEquals(PhotoResolutionTier.MAX, defaults.photoResolutionTier)
        assertEquals(StabilizationMode.OIS, defaults.stabilizationMode)
        assertFalse(HungCamSettings(hdrMode = HdrMode.OFF).hdrEnabled)
    }

    @Test
    fun `video bitrate tiers and edit config provide high flagship bitrates`() {
        assertEquals(22_000_000, VideoBitrateTier.STANDARD.bitsPerSecond)
        assertEquals(42_000_000, VideoBitrateTier.HIGH.bitsPerSecond)
        assertEquals(65_000_000, VideoBitrateTier.PRO_MASTER.bitsPerSecond)

        val editConfig = VideoEditConfig(targetBitrateBps = VideoBitrateTier.HIGH.bitsPerSecond)
        assertEquals(42_000_000, editConfig.targetBitrateBps)
    }

    @Test
    fun `low end device detection classifies weak hardware and preserves default toggle`() {
        val defaults = HungCamSettings()
        assertFalse(defaults.lowEndOptimizedMode)

        assertTrue(com.example.camera.DevicePerformanceProfile.isLowEndHardwareSpec(true, 8L * 1024 * 1024 * 1024, 8, 34))
        assertTrue(com.example.camera.DevicePerformanceProfile.isLowEndHardwareSpec(false, 4L * 1024 * 1024 * 1024, 8, 34))
        assertTrue(com.example.camera.DevicePerformanceProfile.isLowEndHardwareSpec(false, 8L * 1024 * 1024 * 1024, 4, 34))
        assertTrue(com.example.camera.DevicePerformanceProfile.isLowEndHardwareSpec(false, 8L * 1024 * 1024 * 1024, 8, 28))
        assertFalse(com.example.camera.DevicePerformanceProfile.isLowEndHardwareSpec(false, 8L * 1024 * 1024 * 1024, 8, 34))
    }

    @Test
    fun `main navigation destinations keep clean separated labels`() {
        val labels = com.example.model.MainNavDestination.entries.map { it.label }
        assertEquals(listOf("Camera", "Thư viện", "AI Lab"), labels)
    }

    @Test
    fun `camera mode colors default to crimson red and support optional vibrant multicolor and monochrome`() {
        val defaults = HungCamSettings()
        assertEquals(com.example.model.AccentColorPreset.CRIMSON_PRO, defaults.accentColorPreset)
        assertEquals(com.example.model.ModeColorStyle.DEFAULT_RED, defaults.modeColorStyle)

        val crimson = com.example.ui.theme.CrimsonLeica
        assertEquals(
            crimson,
            com.example.ui.theme.resolveCameraModeColor(
                com.example.model.CameraMode.VIDEO,
                com.example.model.ModeColorStyle.DEFAULT_RED,
                crimson
            )
        )
        assertEquals(
            com.example.ui.theme.PureWhite,
            com.example.ui.theme.resolveCameraModeColor(
                com.example.model.CameraMode.PORTRAIT,
                com.example.model.ModeColorStyle.MONOCHROME_WHITE,
                crimson
            )
        )

        val vibrantColors = com.example.model.CameraMode.entries.map { mode ->
            com.example.ui.theme.resolveCameraModeColor(
                mode,
                com.example.model.ModeColorStyle.PER_MODE_VIBRANT,
                crimson
            )
        }
        assertEquals(com.example.model.CameraMode.entries.size, vibrantColors.distinct().size)
    }
}
