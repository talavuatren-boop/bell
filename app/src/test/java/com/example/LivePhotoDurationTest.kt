package com.example

import com.example.camera.EncodedFrame
import com.example.camera.LivePhotoRingBuffer
import com.example.model.LivePhotoDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePhotoDurationTest {

    @Test
    fun `live photo durations include off 2s 3s 5s and 10s`() {
        assertEquals(
            listOf(0, 2, 3, 5, 10),
            LivePhotoDuration.entries.map { it.seconds }
        )
        assertEquals("10s", LivePhotoDuration.TEN_SEC.label)
    }

    @Test
    fun `ring buffer selects complete 10s window around shutter`() {
        // 14s at 30fps = 420 frames, keyframe every 500ms (15 frames)
        val totalFrames = 420
        val stepUs = 33_333L
        val baseWallUs = 1_000_000L
        val wallTimes = List(totalFrames) { i -> baseWallUs + i * stepUs }
        val keyFrames = List(totalFrames) { i -> i % 15 == 0 }
        val shutterWallUs = baseWallUs + 210 * stepUs // t = 7.0s

        for (durSec in listOf(2, 3, 5, 10)) {
            val range = LivePhotoRingBuffer.selectWindowIndices(
                wallTimesUs = wallTimes,
                isKeyFrames = keyFrames,
                shutterWallUs = shutterWallUs,
                durationSeconds = durSec
            )
            assertNotNull("Expected valid range for ${durSec}s", range)
            val r = range!!
            assertTrue("Must start on sync keyframe", keyFrames[r.first])
            val spanMs = (wallTimes[r.last] - wallTimes[r.first]) / 1000L
            assertTrue(
                "Expected >= ${(durSec * 1000) - 200}ms for ${durSec}s, got ${spanMs}ms",
                spanMs >= (durSec * 1000L) - 200L
            )
        }
    }

    @Test
    fun `early shutter on 10s live photo extends post-shutter window to full 10s`() {
        // User toggled 10s Live Photo and snapped after 0.5s; post-shutter recorded ~9.8s
        val totalFrames = 315
        val stepUs = 33_333L
        val baseWallUs = 2_000_000L
        val wallTimes = List(totalFrames) { i -> baseWallUs + i * stepUs }
        val keyFrames = List(totalFrames) { i -> i % 15 == 0 }
        val earlyShutterUs = baseWallUs + 15 * stepUs

        val range = LivePhotoRingBuffer.selectWindowIndices(
            wallTimesUs = wallTimes,
            isKeyFrames = keyFrames,
            shutterWallUs = earlyShutterUs,
            durationSeconds = 10
        )
        assertNotNull(range)
        val spanMs = (wallTimes[range!!.last] - wallTimes[range.first]) / 1000L
        assertTrue("Early shutter 10s clip should cover ~10s, got ${spanMs}ms", spanMs >= 9800L)
    }

    @Test
    fun `monotonic video pts spans full 10s across 300 frames`() {
        val frames = List(301) { i ->
            EncodedFrame(
                data = byteArrayOf(0x01),
                codecPtsUs = i * 33_333L,
                wallTimeUs = 5_000_000L + i * 33_333L,
                flags = if (i % 15 == 0) 1 else 0
            )
        }
        val pts = LivePhotoRingBuffer.buildMonotonicVideoPtsUs(frames)
        assertEquals(301, pts.size)
        assertEquals(0L, pts.first())
        assertTrue("Expected ~10s span, got ${pts.last()}", pts.last() >= 9_950_000L)
        for (i in 1 until pts.size) {
            assertTrue("PTS must increase monotonically", pts[i] > pts[i - 1])
        }
    }
}
