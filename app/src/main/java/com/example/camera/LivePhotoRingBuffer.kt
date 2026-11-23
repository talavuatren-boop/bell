package com.example.camera

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class EncodedFrame(
    val data: ByteArray,
    val codecPtsUs: Long,
    val wallTimeUs: Long,
    val flags: Int
)

data class LiveClipResult(
    val file: File,
    val width: Int,
    val height: Int,
    val shutterOffsetMs: Long,
    val totalDurationMs: Long,
    val hasAudio: Boolean
)

/**
 * Circular rolling MediaCodec buffer for true pre-shutter + post-shutter Live Photo capture.
 * Uses unified SystemClock.elapsedRealtimeNanos() wallTimeUs across video, audio, and shutter events
 * so deep-sleep / CLOCK_MONOTONIC vs CLOCK_BOOTTIME offsets never truncate 2s / 3s / 5s / 10s clips,
 * while preserving exact codecPtsUs frame deltas and frequent sync keyframes.
 */
class LivePhotoRingBuffer(
    val width: Int = 1280,
    val height: Int = 960,
    val bitrateBps: Int = 12_000_000,
    val fps: Int = 30,
    private val maxBufferSeconds: Int = 14
) {
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var inputSurface: Surface? = null

    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null

    private val running = AtomicBoolean(false)
    private var videoDrainThread: Thread? = null
    private var audioThread: Thread? = null

    private val videoLock = Any()
    private val audioLock = Any()
    private val videoPackets = ArrayDeque<EncodedFrame>(480)
    private val audioPackets = ArrayDeque<EncodedFrame>(768)

    @Volatile
    private var audioEnabled = false

    fun getInputSurface(): Surface? = inputSurface

    @SuppressLint("MissingPermission")
    fun start(enableAudio: Boolean): Boolean {
        if (running.get()) return true
        audioEnabled = enableAudio

        return runCatching {
            val (vCodec, surface) = configureVideoEncoder(bitrateBps)
            videoEncoder = vCodec
            inputSurface = surface
            videoFormat = null

            if (enableAudio) {
                setupAudioPipeline()
            }

            running.set(true)
            // Request an immediate keyframe so early shutter presses still have a sync frame
            runCatching {
                vCodec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
            }
            videoDrainThread = Thread(::drainVideoLoop, "HungCamLiveVideo").apply { start() }
            if (enableAudio && audioEncoder != null && audioRecord != null) {
                audioThread = Thread(::pumpAndDrainAudioLoop, "HungCamLiveAudio").apply { start() }
            }
            true
        }.getOrElse {
            stop()
            false
        }
    }

    private fun configureVideoEncoder(preferredBitrate: Int): Pair<MediaCodec, Surface> {
        val attempts = listOf(
            Triple(preferredBitrate, true, 1),
            Triple(minOf(preferredBitrate, 8_000_000), false, 1),
            Triple(6_000_000, false, 1)
        )
        var lastError: Throwable? = null
        for ((bps, setNoBFrames, iFrameSec) in attempts) {
            val vCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            try {
                val vFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, bps)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameSec)
                    if (setNoBFrames && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                    }
                }
                vCodec.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val surface = vCodec.createInputSurface()
                vCodec.start()
                return vCodec to surface
            } catch (t: Throwable) {
                lastError = t
                runCatching { vCodec.release() }
            }
        }
        throw (lastError ?: IllegalStateException("Unable to configure Live Photo video encoder"))
    }

    @SuppressLint("MissingPermission")
    private fun setupAudioPipeline() {
        runCatching {
            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return
            }

            val aFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val aCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            aCodec.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aCodec.start()
            recorder.startRecording()

            audioRecord = recorder
            audioEncoder = aCodec
            audioFormat = null
        }
    }

    private fun drainVideoLoop() {
        val codec = videoEncoder ?: return
        val info = MediaCodec.BufferInfo()
        val maxWindowUs = maxBufferSeconds * 1_000_000L
        var lastSyncRequestMs = 0L

        while (running.get()) {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastSyncRequestMs >= 500L) {
                lastSyncRequestMs = nowMs
                runCatching {
                    val syncBundle = Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }
                    codec.setParameters(syncBundle)
                }
            }

            val outIdx = runCatching { codec.dequeueOutputBuffer(info, 10_000L) }.getOrDefault(-1)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    videoFormat = codec.outputFormat
                }
                outIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val bytes = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.get(bytes)

                        val wallUs = SystemClock.elapsedRealtimeNanos() / 1000L
                        val codecPts = if (info.presentationTimeUs > 0L) info.presentationTimeUs else wallUs

                        synchronized(videoLock) {
                            videoPackets.addLast(
                                EncodedFrame(
                                    data = bytes,
                                    codecPtsUs = codecPts,
                                    wallTimeUs = wallUs,
                                    flags = info.flags
                                )
                            )
                            val cutoff = wallUs - maxWindowUs
                            while (videoPackets.size > 15 && videoPackets.first().wallTimeUs < cutoff) {
                                var nextKeyIdx = -1
                                for (idx in 1 until videoPackets.size) {
                                    val candidate = videoPackets[idx]
                                    if (candidate.wallTimeUs >= cutoff) break
                                    if ((candidate.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                        nextKeyIdx = idx
                                        break
                                    }
                                }
                                if (nextKeyIdx > 0) {
                                    repeat(nextKeyIdx) { videoPackets.removeFirst() }
                                } else if (videoPackets.size > maxBufferSeconds * fps + 120) {
                                    videoPackets.removeFirst()
                                } else {
                                    break
                                }
                            }
                        }
                    }
                    runCatching { codec.releaseOutputBuffer(outIdx, false) }
                }
            }
        }
    }

    private fun pumpAndDrainAudioLoop() {
        val recorder = audioRecord ?: return
        val codec = audioEncoder ?: return
        val pcmBuffer = ByteArray(4096)
        val info = MediaCodec.BufferInfo()
        val maxWindowUs = maxBufferSeconds * 1_000_000L

        while (running.get()) {
            val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
            if (read > 0) {
                val inIdx = runCatching { codec.dequeueInputBuffer(5_000L) }.getOrDefault(-1)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)
                    inBuf?.clear()
                    inBuf?.put(pcmBuffer, 0, read)
                    val ptsUs = SystemClock.elapsedRealtimeNanos() / 1000L
                    runCatching { codec.queueInputBuffer(inIdx, 0, read, ptsUs, 0) }
                }
            }

            var outIdx = runCatching { codec.dequeueOutputBuffer(info, 0L) }.getOrDefault(-1)
            while (outIdx >= 0 || outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioFormat = codec.outputFormat
                } else if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val bytes = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.get(bytes)
                        val wallUs = SystemClock.elapsedRealtimeNanos() / 1000L
                        val pts = if (info.presentationTimeUs > 0L) info.presentationTimeUs else wallUs
                        val effectiveWallUs = if (kotlin.math.abs(pts - wallUs) < 2_000_000L) pts else wallUs
                        synchronized(audioLock) {
                            audioPackets.addLast(
                                EncodedFrame(
                                    data = bytes,
                                    codecPtsUs = pts,
                                    wallTimeUs = effectiveWallUs,
                                    flags = info.flags
                                )
                            )
                            val cutoff = effectiveWallUs - maxWindowUs
                            while (audioPackets.isNotEmpty() && audioPackets.first().wallTimeUs < cutoff) {
                                audioPackets.removeFirst()
                            }
                        }
                    }
                    runCatching { codec.releaseOutputBuffer(outIdx, false) }
                }
                outIdx = runCatching { codec.dequeueOutputBuffer(info, 0L) }.getOrDefault(-1)
            }
        }
    }

    fun currentBufferStatusLabel(): String {
        val (count, spanSec) = synchronized(videoLock) {
            val c = videoPackets.size
            val span = if (c >= 2) {
                ((videoPackets.last().wallTimeUs - videoPackets.first().wallTimeUs) / 1_000_000f).coerceAtLeast(0f)
            } else 0f
            c to span
        }
        return if (!running.get()) "Standby"
        else if (count < 10) "Priming..."
        else "Ring ${String.format("%.1fs", spanSec)}"
    }

    suspend fun captureClipAroundShutter(
        durationSeconds: Int,
        shutterMonotonicUs: Long,
        outputFile: File,
        orientationHint: Int = 90,
        onPostShutterProgress: ((Float) -> Unit)? = null
    ): Result<LiveClipResult> {
        val hasInitialPackets = synchronized(videoLock) { videoPackets.isNotEmpty() && videoFormat != null }
        if (!running.get() && !hasInitialPackets) {
            return Result.failure(IllegalStateException("Live Photo ring buffer is not active"))
        }

        val targetMs = durationSeconds * 1000L
        val halfWindowMs = targetMs / 2L
        val preAvailableMs = synchronized(videoLock) {
            val firstWall = videoPackets.firstOrNull()?.wallTimeUs
            if (firstWall != null && firstWall < shutterMonotonicUs) {
                ((shutterMonotonicUs - firstWall) / 1000L).coerceIn(0L, halfWindowMs)
            } else 0L
        }
        // If buffer started very recently, extend post-shutter window so total clip still meets 2s/3s/5s/10s
        val postNeededMs = (targetMs - preAvailableMs).coerceIn(halfWindowMs, targetMs)
        val alreadyElapsedMs = ((SystemClock.elapsedRealtimeNanos() / 1000L - shutterMonotonicUs) / 1000L).coerceAtLeast(0L)
        val remainingWaitMs = (postNeededMs + 280L - alreadyElapsedMs).coerceAtLeast(160L)

        // Grab pre-shutter snapshot immediately and update incrementally during post-shutter window
        // so early stop() (e.g., user opens Viewer immediately) retains valid motion packets.
        var vSnapshot: List<EncodedFrame> = emptyList()
        var aSnapshot: List<EncodedFrame> = emptyList()
        var vFmt: MediaFormat? = videoFormat
        synchronized(videoLock) {
            if (videoPackets.isNotEmpty()) {
                vSnapshot = videoPackets.toList()
                vFmt = videoFormat ?: vFmt
            }
        }
        synchronized(audioLock) {
            if (audioEnabled && audioFormat != null && audioPackets.isNotEmpty()) {
                aSnapshot = audioPackets.toList()
            }
        }
        val waitStartMs = SystemClock.elapsedRealtime()
        val waitEndMs = waitStartMs + remainingWaitMs
        onPostShutterProgress?.invoke(0.04f)
        while (true) {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs >= waitEndMs || !running.get()) break
            val progress = ((nowMs - waitStartMs).toFloat() / remainingWaitMs.toFloat()).coerceIn(0.05f, 0.99f)
            onPostShutterProgress?.invoke(progress)
            delay(45L)
            synchronized(videoLock) {
                if (videoPackets.isNotEmpty()) {
                    vSnapshot = videoPackets.toList()
                    vFmt = videoFormat ?: vFmt
                }
            }
            synchronized(audioLock) {
                if (audioEnabled && audioFormat != null && audioPackets.isNotEmpty()) {
                    aSnapshot = audioPackets.toList()
                }
            }
        }
        onPostShutterProgress?.invoke(1f)

        synchronized(videoLock) {
            if (videoPackets.isNotEmpty()) {
                vSnapshot = videoPackets.toList()
                vFmt = videoFormat ?: vFmt
            }
        }
        synchronized(audioLock) {
            if (audioEnabled && audioFormat != null && audioPackets.isNotEmpty()) {
                aSnapshot = audioPackets.toList()
            }
        }

        val resolvedVFmt = vFmt ?: return Result.failure(IllegalStateException("Video format unavailable"))
        if (vSnapshot.isEmpty()) {
            return Result.failure(IllegalStateException("No buffered frames available"))
        }

        val windowRange = selectWindowIndices(
            wallTimesUs = vSnapshot.map { it.wallTimeUs },
            isKeyFrames = vSnapshot.map { (it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 },
            shutterWallUs = shutterMonotonicUs,
            durationSeconds = durationSeconds
        ) ?: return Result.failure(IllegalStateException("No sync keyframe found in buffer"))

        val selectedVideo = vSnapshot.subList(windowRange.first, windowRange.last + 1)
        val firstWallUs = selectedVideo.first().wallTimeUs
        val shutterOffsetMs = ((shutterMonotonicUs - firstWallUs) / 1000L).coerceAtLeast(0L)
        val videoRelPtsList = buildMonotonicVideoPtsUs(selectedVideo)
        val totalSpanUs = videoRelPtsList.lastOrNull()?.coerceAtLeast(400_000L) ?: 400_000L

        return runCatching {
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vTrack = muxer.addTrack(resolvedVFmt)
            val aFmt = audioFormat
            val selectedAudio = if (aFmt != null && aSnapshot.isNotEmpty()) {
                aSnapshot.filter { it.wallTimeUs in firstWallUs..(selectedVideo.last().wallTimeUs + 60_000L) }
            } else {
                emptyList()
            }
            val aTrack = if (aFmt != null && selectedAudio.isNotEmpty()) muxer.addTrack(aFmt) else -1

            muxer.setOrientationHint(((orientationHint % 360) + 360) % 360)
            muxer.start()
            val info = MediaCodec.BufferInfo()
            for (i in selectedVideo.indices) {
                val pkt = selectedVideo[i]
                val relPts = videoRelPtsList[i]
                info.set(0, pkt.data.size, relPts, pkt.flags)
                muxer.writeSampleData(vTrack, java.nio.ByteBuffer.wrap(pkt.data), info)
            }

            if (aTrack >= 0) {
                var relAudioUs = 0L
                val maxAudioUs = totalSpanUs + 120_000L
                for (i in selectedAudio.indices) {
                    val pkt = selectedAudio[i]
                    if (i == 0) {
                        relAudioUs = (pkt.wallTimeUs - firstWallUs).coerceIn(0L, 120_000L)
                    } else {
                        val delta = (pkt.wallTimeUs - selectedAudio[i - 1].wallTimeUs).coerceIn(1_000L, 120_000L)
                        relAudioUs = (relAudioUs + delta).coerceAtMost(maxAudioUs)
                    }
                    if (relAudioUs >= maxAudioUs && i > 0) break
                    info.set(0, pkt.data.size, relAudioUs, pkt.flags)
                    muxer.writeSampleData(aTrack, java.nio.ByteBuffer.wrap(pkt.data), info)
                }
            }

            muxer.stop()
            muxer.release()

            LiveClipResult(
                file = outputFile,
                width = width,
                height = height,
                shutterOffsetMs = shutterOffsetMs,
                totalDurationMs = (totalSpanUs / 1000L).coerceAtLeast(400L),
                hasAudio = aTrack >= 0
            )
        }
    }

    fun stop() {
        running.set(false)
        runCatching { videoDrainThread?.join(400) }
        runCatching { audioThread?.join(400) }
        videoDrainThread = null
        audioThread = null

        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null

        runCatching { audioEncoder?.stop() }
        runCatching { audioEncoder?.release() }
        audioEncoder = null

        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        videoEncoder = null

        runCatching { inputSurface?.release() }
        inputSurface = null

        synchronized(videoLock) { videoPackets.clear() }
        synchronized(audioLock) { audioPackets.clear() }
    }

    companion object {
        fun selectWindowIndices(
            wallTimesUs: List<Long>,
            isKeyFrames: List<Boolean>,
            shutterWallUs: Long,
            durationSeconds: Int
        ): IntRange? {
            if (wallTimesUs.isEmpty() || wallTimesUs.size != isKeyFrames.size) return null
            val targetDurationUs = durationSeconds * 1_000_000L
            val halfWindowUs = targetDurationUs / 2L
            val latestWall = wallTimesUs.last()
            val targetStartWall = (shutterWallUs - halfWindowUs).coerceAtMost(latestWall - 400_000L)

            var startIdx = -1
            for (i in wallTimesUs.indices.reversed()) {
                if (isKeyFrames[i] && wallTimesUs[i] <= targetStartWall) {
                    startIdx = i
                    break
                }
            }
            if (startIdx < 0) {
                startIdx = isKeyFrames.indexOfFirst { it }
            }
            if (startIdx < 0) return null

            val actualStartWall = wallTimesUs[startIdx]
            // Ensure full target duration is preserved even if pre-shutter started slightly later
            val desiredEndWall = maxOf(shutterWallUs + halfWindowUs, actualStartWall + targetDurationUs)
            var endIdx = startIdx
            for (i in startIdx until wallTimesUs.size) {
                if (wallTimesUs[i] <= desiredEndWall) {
                    endIdx = i
                } else {
                    break
                }
            }
            return startIdx..endIdx
        }

        fun buildMonotonicVideoPtsUs(frames: List<EncodedFrame>): List<Long> {
            if (frames.isEmpty()) return emptyList()
            val out = ArrayList<Long>(frames.size)
            var relPts = 0L
            out.add(0L)
            for (i in 1 until frames.size) {
                val cur = frames[i]
                val prev = frames[i - 1]
                val wallDelta = (cur.wallTimeUs - prev.wallTimeUs).coerceIn(1_000L, 250_000L)
                val codecDelta = cur.codecPtsUs - prev.codecPtsUs
                val step = if (codecDelta in 1_000L..250_000L) codecDelta else wallDelta
                relPts += step
                out.add(relPts)
            }
            return out
        }
    }
}
