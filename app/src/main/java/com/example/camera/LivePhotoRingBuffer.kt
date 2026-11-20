package com.example.camera

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class EncodedFrame(
    val data: ByteArray,
    val ptsUs: Long,
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
 * Uses monotonic SystemClock.elapsedRealtimeNanos() timestamps to synchronize still exposure
 * and encoded video/audio streams without dropping or duplicating frames.
 */
class LivePhotoRingBuffer(
    val width: Int = 1280,
    val height: Int = 960,
    val bitrateBps: Int = 14_000_000,
    val fps: Int = 30,
    private val maxBufferSeconds: Int = 6
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
    private val videoPackets = ArrayDeque<EncodedFrame>(320)
    private val audioPackets = ArrayDeque<EncodedFrame>(512)

    @Volatile
    private var audioEnabled = false

    fun getInputSurface(): Surface? = inputSurface

    @SuppressLint("MissingPermission")
    fun start(enableAudio: Boolean): Boolean {
        if (running.get()) return true
        audioEnabled = enableAudio

        return runCatching {
            val vFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val vCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            vCodec.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = vCodec.createInputSurface()
            vCodec.start()

            videoEncoder = vCodec
            inputSurface = surface
            videoFormat = null

            if (enableAudio) {
                setupAudioPipeline()
            }

            running.set(true)
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

        while (running.get()) {
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

                        val monotonicUs = SystemClock.elapsedRealtimeNanos() / 1000L
                        val ptsUs = if (info.presentationTimeUs > 0L) info.presentationTimeUs else monotonicUs

                        synchronized(videoLock) {
                            videoPackets.addLast(EncodedFrame(bytes, ptsUs, info.flags))
                            val cutoff = ptsUs - maxWindowUs
                            while (videoPackets.size > 15 && videoPackets.first().ptsUs < cutoff) {
                                // Only trim up to a safe window before the oldest needed keyframe
                                val next = videoPackets.elementAtOrNull(1)
                                if (next != null && next.ptsUs < cutoff && (next.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                    videoPackets.removeFirst()
                                } else if (videoPackets.size > maxBufferSeconds * fps + 60) {
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
                        synchronized(audioLock) {
                            audioPackets.addLast(EncodedFrame(bytes, info.presentationTimeUs, info.flags))
                            val cutoff = info.presentationTimeUs - maxWindowUs
                            while (audioPackets.isNotEmpty() && audioPackets.first().ptsUs < cutoff) {
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
        val count = synchronized(videoLock) { videoPackets.size }
        return if (!running.get()) "Standby"
        else if (count < 10) "Priming..."
        else "Ring ${(count / fps.toFloat()).let { String.format("%.1fs", it) }}"
    }

    suspend fun captureClipAroundShutter(
        durationSeconds: Int,
        shutterMonotonicUs: Long,
        outputFile: File
    ): Result<LiveClipResult> {
        if (!running.get() || videoFormat == null) {
            return Result.failure(IllegalStateException("Live Photo ring buffer is not active"))
        }

        val halfWindowUs = (durationSeconds * 1_000_000L) / 2L
        // Wait for post-shutter half window + small encoder flush margin
        val postWaitMs = (halfWindowUs / 1000L) + 160L
        delay(postWaitMs)

        val vSnapshot: List<EncodedFrame>
        synchronized(videoLock) {
            vSnapshot = videoPackets.toList()
        }
        val aSnapshot: List<EncodedFrame>
        synchronized(audioLock) {
            aSnapshot = if (audioEnabled && audioFormat != null) audioPackets.toList() else emptyList()
        }

        val vFmt = videoFormat ?: return Result.failure(IllegalStateException("Video format unavailable"))
        if (vSnapshot.isEmpty()) {
            return Result.failure(IllegalStateException("No buffered frames available"))
        }

        val latestFramePts = vSnapshot.last().ptsUs
        val targetStartUs = (shutterMonotonicUs - halfWindowUs).coerceAtMost(latestFramePts - 500_000L)
        val targetEndUs = shutterMonotonicUs + halfWindowUs

        // Find the closest keyframe at or before targetStartUs so there are no black/corrupted frames
        var startIdx = vSnapshot.indexOfLast {
            it.ptsUs <= targetStartUs && (it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        }
        if (startIdx < 0) {
            startIdx = vSnapshot.indexOfFirst { (it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 }
        }
        if (startIdx < 0) {
            return Result.failure(IllegalStateException("No sync keyframe found in buffer"))
        }

        val selectedVideo = vSnapshot.subList(startIdx, vSnapshot.size)
            .filter { it.ptsUs <= targetEndUs }
            .ifEmpty { vSnapshot.subList(startIdx, vSnapshot.size) }

        val firstPtsUs = selectedVideo.first().ptsUs
        val lastPtsUs = selectedVideo.last().ptsUs
        val shutterOffsetMs = ((shutterMonotonicUs - firstPtsUs) / 1000L).coerceAtLeast(0L)

        return runCatching {
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vTrack = muxer.addTrack(vFmt)
            val aFmt = audioFormat
            val selectedAudio = if (aFmt != null && aSnapshot.isNotEmpty()) {
                aSnapshot.filter { it.ptsUs in firstPtsUs..lastPtsUs }
            } else {
                emptyList()
            }
            val aTrack = if (aFmt != null && selectedAudio.isNotEmpty()) muxer.addTrack(aFmt) else -1

            muxer.start()
            val info = MediaCodec.BufferInfo()
            var lastWrittenV = -1L
            for (pkt in selectedVideo) {
                val relPts = (pkt.ptsUs - firstPtsUs).coerceAtLeast(lastWrittenV + 1L)
                lastWrittenV = relPts
                info.set(0, pkt.data.size, relPts, pkt.flags)
                muxer.writeSampleData(vTrack, java.nio.ByteBuffer.wrap(pkt.data), info)
            }

            if (aTrack >= 0) {
                var lastWrittenA = -1L
                for (pkt in selectedAudio) {
                    val relPts = (pkt.ptsUs - firstPtsUs).coerceAtLeast(lastWrittenA + 1L)
                    lastWrittenA = relPts
                    info.set(0, pkt.data.size, relPts, pkt.flags)
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
                totalDurationMs = ((lastPtsUs - firstPtsUs) / 1000L).coerceAtLeast(500L),
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
}
