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
}
