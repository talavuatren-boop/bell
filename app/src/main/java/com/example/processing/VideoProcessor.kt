package com.example.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.model.AiProcessingMode
import com.example.model.GyroSample
import com.example.model.PhotoFilterPreset
import com.example.model.VideoEditConfig
import com.example.model.VideoMergeLayout
import com.example.model.VideoResolutionPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class VideoProcessResult(
    val outputFile: File,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int,
    val processingTimeMs: Long
)

/**
 * Memory-safe segmented video processor.
 * Processes frames sequentially without loading the whole video into RAM,
 * preserves audio track via MediaExtractor/MediaMuxer, supports gyroscope-assisted
 * motion estimation + block-matching stabilization, temporal frame enhancement,
 * and motion-compensated frame interpolation.
 */
class VideoProcessor(private val context: Context) {

    suspend fun processVideo(
        sourceUri: Uri,
        config: VideoEditConfig,
        mode: AiProcessingMode,
        gyroTrackPath: String? = null,
        interpolateTo60Fps: Boolean = false,
        applyFrameEnhance: Boolean = false,
        onProgress: (percent: Int, stage: String) -> Unit
    ): VideoProcessResult = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()
        val outFile = File(context.cacheDir, "hung_vid_proc_${System.currentTimeMillis()}.mp4")

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, sourceUri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 3000L
        val srcW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
        val srcH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
        val srcRot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val displayW = if (srcRot == 90 || srcRot == 270) srcH else srcW
        val displayH = if (srcRot == 90 || srcRot == 270) srcW else srcH

        val trimStart = config.trimStartMs.coerceIn(0L, durationMs)
        val trimEnd = if (config.trimEndMs in (trimStart + 200L)..durationMs) config.trimEndMs else durationMs
        val effectiveDurMs = (trimEnd - trimStart).coerceAtLeast(300L)

        // Preserve orientation aspect (or exact coded aspect when preserveSourceAspect = true)
        val portrait = displayH > displayW
        val targetW: Int
        val targetH: Int
        if (config.preserveSourceAspect && displayW > 0 && displayH > 0) {
            val maxDim = max(config.targetResolution.width, config.targetResolution.height)
            val scale = min(1f, maxDim.toFloat() / max(displayW, displayH).toFloat())
            targetW = (((displayW * scale).roundToInt()) / 16).coerceAtLeast(16) * 16
            targetH = (((displayH * scale).roundToInt()) / 16).coerceAtLeast(16) * 16
        } else {
            val rawW = config.targetResolution.width
            val rawH = config.targetResolution.height
            targetW = ((if (portrait) min(rawW, rawH) else max(rawW, rawH)) / 16) * 16
            targetH = ((if (portrait) max(rawW, rawH) else min(rawW, rawH)) / 16) * 16
        }

        val baseFps = config.targetFps.coerceIn(24, 60)
        val outFps = if (interpolateTo60Fps && baseFps < 60) 60 else baseFps
        val bitrate = when (mode) {
            AiProcessingMode.FAST -> 10_000_000
            AiProcessingMode.BALANCED -> 18_000_000
            AiProcessingMode.QUALITY -> 28_000_000
        }
        val colorMatrix = buildColorMatrix(config)

        // Fast hardware GPU transcode path when no spatial/temporal bitmap kernels are required
        val needsBitmapKernels = config.applyStabilization ||
            config.applyDenoise ||
            config.applySharpen ||
            interpolateTo60Fps ||
            applyFrameEnhance
        if (!needsBitmapKernels) {
            val fastResult = runCatching {
                tryFastGpuTranscode(
                    sourceUri = sourceUri,
                    outFile = outFile,
                    srcRot = srcRot,
                    srcW = srcW,
                    srcH = srcH,
                    trimStartMs = trimStart,
                    trimEndMs = trimEnd,
                    effectiveDurMs = effectiveDurMs,
                    muteAudio = config.muteAudio,
                    preserveSourceAspect = config.preserveSourceAspect,
                    targetPreset = config.targetResolution,
                    outFps = baseFps,
                    bitrate = bitrate,
                    colorMatrix = colorMatrix,
                    startMs = startMs,
                    onProgress = onProgress
                )
            }.getOrNull()
            if (fastResult != null) {
                runCatching { retriever.release() }
                return@withContext fastResult
            } else {
                runCatching { outFile.delete() }
            }
        }

        // Cap sample step gracefully for thermal/RAM efficiency while maintaining smooth output
        val sampleFps = when (mode) {
            AiProcessingMode.FAST -> min(baseFps, 24)
            AiProcessingMode.BALANCED -> min(baseFps, 30)
            AiProcessingMode.QUALITY -> baseFps
        }

        val totalSourceFrames = ((effectiveDurMs / 1000.0) * sampleFps).roundToInt().coerceIn(4, 1800)
        val frameStepUs = (effectiveDurMs * 1000L) / totalSourceFrames.coerceAtLeast(1)

        val gyroSamples = loadGyroSamples(gyroTrackPath)

        val vFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, outFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        val eglSurface = EglVideoColorSurface.create(inputSurface, targetW, targetH)
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIdx = -1
        var audioTrackIdx = -1
        var muxerStarted = false
        val ptsState = PtsTrackState()

        // Inspect source audio track if not muted
        val audioExtractor = if (!config.muteAudio) prepareAudioExtractor(sourceUri) else null
        var audioSrcTrack = -1
        var audioFormat: MediaFormat? = null
        if (audioExtractor != null) {
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioSrcTrack = i
                    audioFormat = fmt
                    audioExtractor.selectTrack(i)
                    break
                }
            }
        }

        val bufferInfo = MediaCodec.BufferInfo()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (colorMatrix != null) {
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        // Stabilization state (Gaussian trajectory smoothing)
        var smoothDx = 0f
        var smoothDy = 0f
        var prevLumaGrid: IntArray? = null
        var prevFrameBitmap: Bitmap? = null

        var outFrameIndex = 0L
        val outFrameDurationNs = 1_000_000_000L / outFps.coerceAtLeast(1)

        try {
            onProgress(5, "Initializing segmented MediaCodec pipeline...")

            for (i in 0 until totalSourceFrames) {
                coroutineContext.ensureActive()
                val timeUs = (trimStart * 1000L) + (i * frameStepUs)

                val pct = 5 + ((i * 80) / totalSourceFrames)
                val stageDesc = when {
                    config.applyStabilization -> "Stabilizing & compensating motion (${i + 1}/$totalSourceFrames)..."
                    interpolateTo60Fps -> "Motion-aware frame interpolation (${i + 1}/$totalSourceFrames)..."
                    config.applyDenoise || applyFrameEnhance -> "Temporal denoising & frame enhancement (${i + 1}/$totalSourceFrames)..."
                    else -> "Encoding segment (${i + 1}/$totalSourceFrames)..."
                }
                if (i % 4 == 0) onProgress(pct.coerceIn(5, 88), stageDesc)

                val rawBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: continue
                val fitted = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                Canvas(fitted).apply {
                    drawColor(android.graphics.Color.BLACK)
                    drawAspectFit(this, rawBitmap, 0f, 0f, targetW.toFloat(), targetH.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
                }
                rawBitmap.recycle()

                var processedFrame = fitted

                // Optional AI Color / Denoise / Sharpen per frame
                if (config.applyAiColor || config.applyDenoise || config.applySharpen) {
                    if (config.applyDenoise && mode != AiProcessingMode.FAST) {
                        processedFrame = fastSpatialDenoise(processedFrame)
                    }
                    if (config.applySharpen) {
                        processedFrame = fastEdgeSharpen(processedFrame)
                    }
                }

                // Temporal frame enhancement (flicker & inter-frame noise suppression)
                if (applyFrameEnhance && prevFrameBitmap != null) {
                    processedFrame = blendTemporalConsistency(prevFrameBitmap!!, processedFrame)
                }

                // Motion-compensated frame interpolation (generates warped intermediate frame before current frame)
                if (interpolateTo60Fps && prevFrameBitmap != null) {
                    val interp = buildMotionInterpolatedFrame(prevFrameBitmap!!, processedFrame)
                    drawFrameToSurface(
                        surface = inputSurface,
                        eglSurface = eglSurface,
                        frame = interp,
                        targetW = targetW,
                        targetH = targetH,
                        paint = paint,
                        colorMatrix = colorMatrix,
                        shiftX = 0f,
                        shiftY = 0f,
                        cropScale = 1f,
                        ptsNs = outFrameIndex * outFrameDurationNs
                    )
                    drainEncoder(encoder, muxer, bufferInfo, ptsState, audioFormat, { vt, at, started ->
                        videoTrackIdx = vt; audioTrackIdx = at; muxerStarted = started
                    }, { videoTrackIdx to muxerStarted })
                    outFrameIndex++
                    interp.recycle()
                }

                // Stabilization motion estimation (gyro + block matching)
                var shiftX = 0f
                var shiftY = 0f
                var cropScale = 1.0f
                if (config.applyStabilization) {
                    val curGrid = extractLumaGrid(processedFrame)
                    val motion = estimateMotionDelta(prevLumaGrid, curGrid, gyroSamples, i, totalSourceFrames)
                    prevLumaGrid = curGrid
                    // Exponential low-pass filter on camera path -> subtract high-freq shake
                    val alpha = 0.22f
                    smoothDx += alpha * motion.first
                    smoothDy += alpha * motion.second
                    shiftX = (smoothDx - motion.first).coerceIn(-targetW * 0.055f, targetW * 0.055f)
                    shiftY = (smoothDy - motion.second).coerceIn(-targetH * 0.055f, targetH * 0.055f)
                    cropScale = 1.12f // Minimal adaptive crop to cover stabilized border
                }

                drawFrameToSurface(
                    surface = inputSurface,
                    eglSurface = eglSurface,
                    frame = processedFrame,
                    targetW = targetW,
                    targetH = targetH,
                    paint = paint,
                    colorMatrix = colorMatrix,
                    shiftX = shiftX,
                    shiftY = shiftY,
                    cropScale = cropScale,
                    ptsNs = outFrameIndex * outFrameDurationNs
                )

                drainEncoder(encoder, muxer, bufferInfo, ptsState, audioFormat, { vt, at, started ->
                    videoTrackIdx = vt; audioTrackIdx = at; muxerStarted = started
                }, { videoTrackIdx to muxerStarted })

                outFrameIndex++
                prevFrameBitmap?.recycle()
                prevFrameBitmap = processedFrame
            }

            encoder.signalEndOfInputStream()
            drainEncoderEnd(encoder, muxer, bufferInfo, ptsState, { videoTrackIdx to muxerStarted })

            // Mux preserved audio track within trim window with monotonic zero-based timestamps
            if (muxerStarted && audioTrackIdx >= 0 && audioExtractor != null && audioSrcTrack >= 0) {
                onProgress(90, "Synchronizing original audio track...")
                muxAudioRange(audioExtractor, muxer, audioTrackIdx, trimStart * 1000L, trimEnd * 1000L, ptsState.lastWrittenPtsUs.coerceAtLeast(300_000L))
            }

            onProgress(100, "Export complete")
        } finally {
            prevFrameBitmap?.recycle()
            runCatching { retriever.release() }
            runCatching { audioExtractor?.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { eglSurface?.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }

        VideoProcessResult(
            outputFile = outFile,
            width = targetW,
            height = targetH,
            fps = outFps,
            bitrateBps = bitrate,
            processingTimeMs = System.currentTimeMillis() - startMs
        )
    }

    private class PtsTrackState {
        var firstRawPtsUs: Long = -1L
        var lastWrittenPtsUs: Long = -1L
    }

    private suspend fun tryFastGpuTranscode(
        sourceUri: Uri,
        outFile: File,
        srcRot: Int,
        srcW: Int,
        srcH: Int,
        trimStartMs: Long,
        trimEndMs: Long,
        effectiveDurMs: Long,
        muteAudio: Boolean,
        preserveSourceAspect: Boolean,
        targetPreset: VideoResolutionPreset,
        outFps: Int,
        bitrate: Int,
        colorMatrix: ColorMatrix?,
        startMs: Long,
        onProgress: (percent: Int, stage: String) -> Unit
    ): VideoProcessResult? {
        val extractor = MediaExtractor().apply { setDataSource(context, sourceUri, null) }
        var videoTrack = -1
        var audioTrack = -1
        var inputVideoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/") && videoTrack < 0) {
                videoTrack = i
                inputVideoFormat = fmt
            } else if (mime.startsWith("audio/") && audioTrack < 0 && !muteAudio) {
                audioTrack = i
                audioFormat = fmt
            }
        }
        val inFmt = inputVideoFormat ?: run {
            extractor.release()
            return null
        }
        val videoMime = inFmt.getString(MediaFormat.KEY_MIME) ?: run {
            extractor.release()
            return null
        }

        val rawCodedW = inFmt.getInteger(MediaFormat.KEY_WIDTH).takeIf { it > 0 } ?: srcW
        val rawCodedH = inFmt.getInteger(MediaFormat.KEY_HEIGHT).takeIf { it > 0 } ?: srcH
        val maxTargetDim = max(targetPreset.width, targetPreset.height)
        val scale = if (preserveSourceAspect || max(rawCodedW, rawCodedH) > maxTargetDim) {
            min(1f, maxTargetDim.toFloat() / max(rawCodedW, rawCodedH).toFloat())
        } else 1f
        val encW = (((rawCodedW * scale).roundToInt()) / 16).coerceAtLeast(16) * 16
        val encH = (((rawCodedH * scale).roundToInt()) / 16).coerceAtLeast(16) * 16

        val encFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, outFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val encInputSurface = encoder.createInputSurface()
        val egl = EglVideoColorSurface.create(encInputSurface, encW, encH)
        if (egl == null) {
            runCatching { encoder.release() }
            runCatching { encInputSurface.release() }
            runCatching { extractor.release() }
            return null
        }
        encoder.start()

        val oesTexId = egl.createOesInputTexture()
        val surfaceTexture = android.graphics.SurfaceTexture(oesTexId)
        val frameSync = Object()
        var frameReady = false
        surfaceTexture.setOnFrameAvailableListener {
            synchronized(frameSync) {
                frameReady = true
                frameSync.notifyAll()
            }
        }
        val decOutputSurface = android.view.Surface(surfaceTexture)
        val decoder = MediaCodec.createDecoderByType(videoMime)
        // Strip KEY_ROTATION before passing to decoder when we preserve coded stream + muxer.setOrientationHint
        val decFmt = MediaFormat.createVideoFormat(videoMime, rawCodedW, rawCodedH).apply {
            for (k in listOf("csd-0", "csd-1", "csd-2")) {
                if (inFmt.containsKey(k)) inFmt.getByteBuffer(k)?.let { setByteBuffer(k, it) }
            }
        }
        decoder.configure(decFmt, decOutputSurface, null, 0)
        decoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.setOrientationHint(((srcRot % 360) + 360) % 360)
        var muxVideoTrack = -1
        var muxAudioTrack = -1
        var muxerStarted = false
        val ptsState = PtsTrackState()

        val stMatrix = FloatArray(16)
        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()
        val trimStartUs = trimStartMs * 1000L
        val trimEndUs = trimEndMs * 1000L
        val effectiveDurUs = (effectiveDurMs * 1000L).coerceAtLeast(300_000L)

        try {
            extractor.selectTrack(videoTrack)
            if (trimStartUs > 0L) {
                extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            onProgress(8, "GPU ColorMatrix transcode active...")
            var inputDone = false
            var outputDone = false
            var firstDecodedPtsUs = -1L
            var lastRenderedPtsNs = -1L

            while (!outputDone) {
                coroutineContext.ensureActive()

                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val size = extractor.readSampleData(inBuf, 0)
                            val sampleTime = extractor.sampleTime
                            if (size < 0 || (trimEndUs > trimStartUs && sampleTime > trimEndUs + 200_000L)) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, size, sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(decInfo, 5_000L)
                when {
                    outIdx >= 0 -> {
                        val eos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val inWindow = decInfo.size > 0 && decInfo.presentationTimeUs >= trimStartUs - 35_000L &&
                            (trimEndUs <= trimStartUs || decInfo.presentationTimeUs <= trimEndUs + 50_000L)
                        if (inWindow) {
                            synchronized(frameSync) { frameReady = false }
                            decoder.releaseOutputBuffer(outIdx, true)
                            var waited = false
                            synchronized(frameSync) {
                                if (!frameReady) {
                                    frameSync.wait(250L)
                                }
                                waited = frameReady
                            }
                            if (waited) {
                                surfaceTexture.updateTexImage()
                                surfaceTexture.getTransformMatrix(stMatrix)
                                if (firstDecodedPtsUs < 0L) firstDecodedPtsUs = decInfo.presentationTimeUs
                                val relPtsUs = (decInfo.presentationTimeUs - firstDecodedPtsUs).coerceAtLeast(0L)
                                val ptsNs = max(relPtsUs * 1000L, lastRenderedPtsNs + 1_000_000L)
                                lastRenderedPtsNs = ptsNs
                                egl.drawOesFrame(oesTexId, stMatrix, colorMatrix, ptsNs)
                                drainEncoder(encoder, muxer, encInfo, ptsState, audioFormat, { vt, at, started ->
                                    muxVideoTrack = vt; muxAudioTrack = at; muxerStarted = started
                                }, { muxVideoTrack to muxerStarted })
                                val pct = 10 + ((relPtsUs * 75) / effectiveDurUs).toInt().coerceIn(0, 78)
                                onProgress(pct, "Applying filter ($pct%)...")
                            }
                        } else {
                            decoder.releaseOutputBuffer(outIdx, false)
                        }
                        if (eos) {
                            outputDone = true
                        }
                    }
                }
            }

            encoder.signalEndOfInputStream()
            drainEncoderEnd(encoder, muxer, encInfo, ptsState, { muxVideoTrack to muxerStarted })

            if (muxerStarted && muxAudioTrack >= 0 && audioTrack >= 0) {
                onProgress(90, "Synchronizing audio track...")
                extractor.unselectTrack(videoTrack)
                extractor.selectTrack(audioTrack)
                muxAudioRange(extractor, muxer, muxAudioTrack, trimStartUs, trimEndUs, ptsState.lastWrittenPtsUs.coerceAtLeast(300_000L))
            }
            onProgress(100, "Export complete")
        } finally {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { decOutputSurface.release() }
            runCatching { surfaceTexture.release() }
            runCatching { extractor.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { egl.release() }
            runCatching { encInputSurface.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }

        val displayOutW = if (srcRot == 90 || srcRot == 270) encH else encW
        val displayOutH = if (srcRot == 90 || srcRot == 270) encW else encH
        return VideoProcessResult(
            outputFile = outFile,
            width = displayOutW,
            height = displayOutH,
            fps = outFps,
            bitrateBps = bitrate,
            processingTimeMs = System.currentTimeMillis() - startMs
        )
    }

    private suspend fun muxAudioRange(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        audioTrackIdx: Int,
        trimStartUs: Long,
        trimEndUs: Long,
        maxVideoPtsUs: Long
    ) {
        extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val aBuf = ByteBuffer.allocate(256 * 1024)
        val aInfo = MediaCodec.BufferInfo()
        var firstAudioPts = -1L
        var lastWrittenAudio = -1L
        val maxAudioUs = maxVideoPtsUs + 150_000L
        while (true) {
            coroutineContext.ensureActive()
            val sampleSize = extractor.readSampleData(aBuf, 0)
            if (sampleSize < 0) break
            val sampleTime = extractor.sampleTime
            if (trimEndUs > trimStartUs && sampleTime > trimEndUs) break
            if (sampleTime >= trimStartUs) {
                if (firstAudioPts < 0L) firstAudioPts = sampleTime
                val relUs = (sampleTime - firstAudioPts).coerceAtLeast(lastWrittenAudio + 1L)
                if (relUs > maxAudioUs && lastWrittenAudio >= 0L) break
                lastWrittenAudio = relUs
                aInfo.offset = 0
                aInfo.size = sampleSize
                aInfo.presentationTimeUs = relUs
                aInfo.flags = extractor.sampleFlags
                runCatching { muxer.writeSampleData(audioTrackIdx, aBuf, aInfo) }
            }
            extractor.advance()
        }
    }

    private fun drawFrameToSurface(
        surface: android.view.Surface,
        eglSurface: EglVideoColorSurface?,
        frame: Bitmap,
        targetW: Int,
        targetH: Int,
        paint: Paint,
        colorMatrix: ColorMatrix?,
        shiftX: Float,
        shiftY: Float,
        cropScale: Float,
        ptsNs: Long
    ) {
        if (eglSurface != null) {
            eglSurface.drawBitmapFrame(
                bitmap = frame,
                colorMatrix = colorMatrix,
                shiftX = shiftX,
                shiftY = shiftY,
                cropScale = cropScale,
                ptsNs = ptsNs
            )
            return
        }
        // Fallback when EGL14 is unavailable (e.g. headless Robolectric JVM)
        val canvas: Canvas = surface.lockCanvas(null)
        try {
            canvas.drawColor(android.graphics.Color.BLACK)
            val m = Matrix().apply {
                postScale(cropScale, cropScale, targetW / 2f, targetH / 2f)
                postTranslate(shiftX, shiftY)
            }
            canvas.drawBitmap(frame, m, paint)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        ptsState: PtsTrackState,
        audioFormat: MediaFormat?,
        onMuxerConfigured: (videoTrack: Int, audioTrack: Int, started: Boolean) -> Unit,
        trackState: () -> Pair<Int, Boolean>
    ) {
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(info, 2_000L)
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val (_, alreadyStarted) = trackState()
                if (!alreadyStarted) {
                    val vt = muxer.addTrack(encoder.outputFormat)
                    val at = if (audioFormat != null) runCatching { muxer.addTrack(audioFormat) }.getOrDefault(-1) else -1
                    muxer.start()
                    onMuxerConfigured(vt, at, true)
                }
            } else if (outIdx >= 0) {
                val buf = encoder.getOutputBuffer(outIdx)
                val (vt, started) = trackState()
                if (buf != null && started && vt >= 0 && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (ptsState.firstRawPtsUs < 0L) ptsState.firstRawPtsUs = info.presentationTimeUs
                    val normPts = (info.presentationTimeUs - ptsState.firstRawPtsUs).coerceAtLeast(ptsState.lastWrittenPtsUs + 1L)
                    ptsState.lastWrittenPtsUs = normPts
                    val normInfo = MediaCodec.BufferInfo().apply {
                        set(info.offset, info.size, normPts, info.flags)
                    }
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(vt, buf, normInfo)
                }
                encoder.releaseOutputBuffer(outIdx, false)
            }
        }
    }

    private fun drainEncoderEnd(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        ptsState: PtsTrackState,
        trackState: () -> Pair<Int, Boolean>
    ) {
        var attempts = 0
        while (attempts < 40) {
            val outIdx = encoder.dequeueOutputBuffer(info, 10_000L)
            if (outIdx >= 0) {
                val buf = encoder.getOutputBuffer(outIdx)
                val (vt, started) = trackState()
                if (buf != null && started && vt >= 0 && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (ptsState.firstRawPtsUs < 0L) ptsState.firstRawPtsUs = info.presentationTimeUs
                    val normPts = (info.presentationTimeUs - ptsState.firstRawPtsUs).coerceAtLeast(ptsState.lastWrittenPtsUs + 1L)
                    ptsState.lastWrittenPtsUs = normPts
                    val normInfo = MediaCodec.BufferInfo().apply {
                        set(info.offset, info.size, normPts, info.flags)
                    }
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(vt, buf, normInfo)
                }
                encoder.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            } else {
                attempts++
            }
        }
    }

    private fun buildColorMatrix(config: VideoEditConfig): ColorMatrix? {
        val hasEdits = config.filter != PhotoFilterPreset.NATURAL ||
            abs(config.brightness) > 0.02f ||
            abs(config.contrast - 1f) > 0.02f ||
            abs(config.saturation - 1f) > 0.02f ||
            abs(config.temperature) > 0.02f ||
            config.applyAiColor
        if (!hasEdits) return null

        val cm = ImageProcessor.buildFilterColorMatrix(config.filter)
        val sat = if (config.applyAiColor) (config.saturation * 1.12f).coerceIn(0f, 2f) else config.saturation
        if (abs(sat - 1f) > 0.02f) {
            cm.postConcat(ColorMatrix().apply { setSaturation(sat) })
        }

        val c = if (config.applyAiColor) (config.contrast * 1.06f) else config.contrast
        val b = config.brightness * 90f + (1f - c) * 128f
        val t = config.temperature * 24f
        if (abs(c - 1f) > 0.01f || abs(b) > 0.5f || abs(t) > 0.5f) {
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, b + t,
                        0f, c, 0f, 0f, b,
                        0f, 0f, c, 0f, b - t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return cm
    }

    private fun prepareAudioExtractor(uri: Uri): MediaExtractor? {
        return runCatching {
            MediaExtractor().apply { setDataSource(context, uri, null) }
        }.getOrNull()
    }

    private fun loadGyroSamples(path: String?): List<GyroSample> {
        if (path.isNullOrBlank()) return emptyList()
        val f = File(path)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size == 4) {
                    GyroSample(
                        timestampNs = parts[0].toLong(),
                        wx = parts[1].toFloat(),
                        wy = parts[2].toFloat(),
                        wz = parts[3].toFloat()
                    )
                } else null
            }
        }.getOrDefault(emptyList())
    }

    private fun extractLumaGrid(bmp: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(bmp, 32, 18, false)
        val px = IntArray(32 * 18)
        small.getPixels(px, 0, 32, 0, 0, 32, 18)
        small.recycle()
        return IntArray(px.size) { idx ->
            val p = px[idx]
            (((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF)) / 10
        }
    }

    private fun estimateMotionDelta(
        prevGrid: IntArray?,
        curGrid: IntArray,
        gyroSamples: List<GyroSample>,
        frameIdx: Int,
        totalFrames: Int
    ): Pair<Float, Float> {
        if (prevGrid == null) return 0f to 0f

        var bestDx = 0
        var bestDy = 0
        var bestSad = Int.MAX_VALUE
        val w = 32
        val h = 18

        for (dy in -3..3) {
            for (dx in -3..3) {
                var sad = 0
                for (y in 3 until h - 3) {
                    for (x in 3 until w - 3) {
                        sad += abs(curGrid[y * w + x] - prevGrid[(y + dy) * w + (x + dx)])
                    }
                }
                if (sad < bestSad) {
                    bestSad = sad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        var vx = bestDx * 14f
        var vy = bestDy * 14f

        // Fuse gyroscope angular rate when recorded for rolling-shutter/shake accuracy
        if (gyroSamples.isNotEmpty()) {
            val gIdx = ((frameIdx.toFloat() / totalFrames.coerceAtLeast(1)) * gyroSamples.size)
                .roundToInt().coerceIn(0, gyroSamples.lastIndex)
            val g = gyroSamples[gIdx]
            vx = vx * 0.65f + (g.wy * 18f) * 0.35f
            vy = vy * 0.65f + (g.wx * 18f) * 0.35f
        }
        return vx to vy
    }

    private fun fastSpatialDenoise(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = px[y * w + x]
                val l = px[y * w + x - 1]
                val r = px[y * w + x + 1]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                val lr = (l shr 16) and 0xFF
                val rr = (r shr 16) and 0xFF
                if (abs(cr - lr) < 18 && abs(cr - rr) < 18) {
                    val nr = (cr * 2 + lr + rr) / 4
                    val ng = (cg * 2 + ((l shr 8) and 0xFF) + ((r shr 8) and 0xFF)) / 4
                    val nb = (cb * 2 + (l and 0xFF) + (r and 0xFF)) / 4
                    out[y * w + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun fastEdgeSharpen(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = px[y * w + x]
                val n = px[(y - 1) * w + x]
                val s = px[(y + 1) * w + x]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                val avgR = (((n shr 16) and 0xFF) + ((s shr 16) and 0xFF)) / 2
                val avgG = (((n shr 8) and 0xFF) + ((s shr 8) and 0xFF)) / 2
                val avgB = ((n and 0xFF) + (s and 0xFF)) / 2
                val nr = (cr + ((cr - avgR) * 0.45f).roundToInt().coerceIn(-18, 18)).coerceIn(0, 255)
                val ng = (cg + ((cg - avgG) * 0.45f).roundToInt().coerceIn(-18, 18)).coerceIn(0, 255)
                val nb = (cb + ((cb - avgB) * 0.45f).roundToInt().coerceIn(-18, 18)).coerceIn(0, 255)
                out[y * w + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Temporal consistency blending for flicker and inter-frame noise suppression.
     * Avoids ghosting by clamping blend weight when pixel motion difference is high.
     */
    private fun blendTemporalConsistency(prev: Bitmap, cur: Bitmap): Bitmap {
        val w = cur.width
        val h = cur.height
        val pPx = IntArray(w * h)
        val cPx = IntArray(w * h)
        prev.getPixels(pPx, 0, w, 0, 0, w, h)
        cur.getPixels(cPx, 0, w, 0, 0, w, h)
        for (i in cPx.indices) {
            val c = cPx[i]
            val p = pPx[i]
            val cr = (c shr 16) and 0xFF
            val cg = (c shr 8) and 0xFF
            val cb = c and 0xFF
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            val diff = abs(cr - pr) + abs(cg - pg) + abs(cb - pb)
            if (diff < 36) {
                val nr = (cr * 3 + pr) / 4
                val ng = (cg * 3 + pg) / 4
                val nb = (cb * 3 + pb) / 4
                cPx[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return Bitmap.createBitmap(cPx, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Motion-aware intermediate frame synthesis (avoids naive frame duplication).
     * Estimates global translational flow vector and warps both frames halfway before blending.
     */
    private fun buildMotionInterpolatedFrame(prev: Bitmap, cur: Bitmap): Bitmap {
        val g1 = extractLumaGrid(prev)
        val g2 = extractLumaGrid(cur)
        val delta = estimateMotionDelta(g1, g2, emptyList(), 0, 1)
        val halfDx = (delta.first * 0.5f).roundToInt().coerceIn(-24, 24)
        val halfDy = (delta.second * 0.5f).roundToInt().coerceIn(-24, 24)

        val out = Bitmap.createBitmap(cur.width, cur.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(prev, halfDx.toFloat(), halfDy.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawBitmap(
            cur,
            -halfDx.toFloat(),
            -halfDy.toFloat(),
            Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 128 }
        )
        return out
    }

    /**
     * Merges 2 or more videos into a single MP4 output using segmented frame decoding/encoding.
     * Supports SEQUENTIAL concatenation as well as SIDE_BY_SIDE and TOP_BOTTOM dual-video combine.
     */
    suspend fun mergeVideos(
        sourceUris: List<Uri>,
        layout: VideoMergeLayout,
        targetResolution: VideoResolutionPreset,
        targetFps: Int,
        includeAudio: Boolean = true,
        onProgress: (percent: Int, stage: String) -> Unit
    ): VideoProcessResult = withContext(Dispatchers.Default) {
        require(sourceUris.size >= 2) { "Select at least 2 videos to merge" }
        val startMs = System.currentTimeMillis()
        val outFile = File(context.cacheDir, "hung_merged_${System.currentTimeMillis()}.mp4")

        val firstRetriever = MediaMetadataRetriever()
        val isFirstPortrait = runCatching {
            firstRetriever.setDataSource(context, sourceUris.first())
            val w = firstRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val h = firstRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val r = firstRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (r == 90 || r == 270) w > h else h > w
        }.getOrDefault(false).also { runCatching { firstRetriever.release() } }

        val rawOutW = targetResolution.width
        val rawOutH = targetResolution.height
        val outW = if (layout == VideoMergeLayout.SEQUENTIAL && isFirstPortrait) (min(rawOutW, rawOutH) / 16) * 16 else (max(rawOutW, rawOutH) / 16) * 16
        val outH = if (layout == VideoMergeLayout.SEQUENTIAL && isFirstPortrait) (max(rawOutW, rawOutH) / 16) * 16 else (min(rawOutW, rawOutH) / 16) * 16
        val fps = targetFps.coerceIn(24, 60)
        val bitrate = if (targetResolution == VideoResolutionPreset.RES_4K) 36_000_000 else 16_000_000

        val vFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        val eglSurface = EglVideoColorSurface.create(inputSurface, outW, outH)
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIdx = -1
        var audioTrackIdx = -1
        var muxerStarted = false
        val ptsState = PtsTrackState()

        val primaryAudioExtractor = if (includeAudio) prepareAudioExtractor(sourceUris.first()) else null
        var audioSrcTrack = -1
        var audioFormat: MediaFormat? = null
        if (primaryAudioExtractor != null) {
            for (i in 0 until primaryAudioExtractor.trackCount) {
                val fmt = primaryAudioExtractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioSrcTrack = i
                    audioFormat = fmt
                    primaryAudioExtractor.selectTrack(i)
                    break
                }
            }
        }

        val bufferInfo = MediaCodec.BufferInfo()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var outFrameIndex = 0L
        val outFrameDurationNs = 1_000_000_000L / fps.coerceAtLeast(1)

        try {
            onProgress(4, "Inspecting ${sourceUris.size} video streams...")

            if (layout == VideoMergeLayout.SEQUENTIAL) {
                val clipDurations = sourceUris.map { uri ->
                    val r = MediaMetadataRetriever()
                    runCatching {
                        r.setDataSource(context, uri)
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2000L
                    }.getOrDefault(2000L).also { runCatching { r.release() } }
                }
                val totalDurMs = clipDurations.sum().coerceAtLeast(1000L)
                var accumDurMs = 0L

                sourceUris.forEachIndexed { clipIdx, uri ->
                    val durMs = clipDurations[clipIdx].coerceAtLeast(300L)
                    val frameCount = ((durMs / 1000.0) * fps).roundToInt().coerceIn(4, 1500)
                    val stepUs = (durMs * 1000L) / frameCount
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    try {
                        for (i in 0 until frameCount) {
                            coroutineContext.ensureActive()
                            val raw = retriever.getFrameAtTime(i * stepUs, MediaMetadataRetriever.OPTION_CLOSEST)
                                ?: continue
                            val fitted = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                            Canvas(fitted).apply {
                                drawColor(android.graphics.Color.BLACK)
                                drawAspectFit(this, raw, 0f, 0f, outW.toFloat(), outH.toFloat(), paint)
                            }
                            raw.recycle()
                            drawFrameToSurface(
                                surface = inputSurface,
                                eglSurface = eglSurface,
                                frame = fitted,
                                targetW = outW,
                                targetH = outH,
                                paint = paint,
                                colorMatrix = null,
                                shiftX = 0f,
                                shiftY = 0f,
                                cropScale = 1f,
                                ptsNs = outFrameIndex * outFrameDurationNs
                            )
                            fitted.recycle()
                            outFrameIndex++

                            drainEncoder(encoder, muxer, bufferInfo, ptsState, audioFormat, { vt, at, started ->
                                videoTrackIdx = vt; audioTrackIdx = at; muxerStarted = started
                            }, { videoTrackIdx to muxerStarted })

                            if (i % 5 == 0) {
                                val elapsedMs = accumDurMs + (i * durMs / frameCount)
                                val pct = 6 + ((elapsedMs * 82) / totalDurMs).toInt()
                                onProgress(pct.coerceIn(6, 88), "Merging clip ${clipIdx + 1}/${sourceUris.size}...")
                            }
                        }
                    } finally {
                        runCatching { retriever.release() }
                    }
                    accumDurMs += durMs
                }
            } else {
                // SIDE_BY_SIDE or TOP_BOTTOM dual video combine
                val r1 = MediaMetadataRetriever().apply { setDataSource(context, sourceUris[0]) }
                val r2 = MediaMetadataRetriever().apply { setDataSource(context, sourceUris[1]) }
                val d1 = r1.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2000L
                val d2 = r2.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2000L
                val maxDurMs = max(d1, d2).coerceAtLeast(400L)
                val frameCount = ((maxDurMs / 1000.0) * fps).roundToInt().coerceIn(4, 1500)
                val stepUs = (maxDurMs * 1000L) / frameCount

                try {
                    for (i in 0 until frameCount) {
                        coroutineContext.ensureActive()
                        val tUs = i * stepUs
                        val f1 = r1.getFrameAtTime(min(tUs, d1 * 1000L), MediaMetadataRetriever.OPTION_CLOSEST)
                        val f2 = r2.getFrameAtTime(min(tUs, d2 * 1000L), MediaMetadataRetriever.OPTION_CLOSEST)
                        val combined = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        Canvas(combined).apply {
                            drawColor(android.graphics.Color.BLACK)
                            if (layout == VideoMergeLayout.SIDE_BY_SIDE) {
                                val halfW = outW / 2f
                                f1?.let { drawAspectFit(this, it, 0f, 0f, halfW, outH.toFloat(), paint) }
                                f2?.let { drawAspectFit(this, it, halfW, 0f, halfW, outH.toFloat(), paint) }
                            } else {
                                val halfH = outH / 2f
                                f1?.let { drawAspectFit(this, it, 0f, 0f, outW.toFloat(), halfH, paint) }
                                f2?.let { drawAspectFit(this, it, 0f, halfH, outW.toFloat(), halfH, paint) }
                            }
                        }
                        f1?.recycle()
                        f2?.recycle()
                        drawFrameToSurface(
                            surface = inputSurface,
                            eglSurface = eglSurface,
                            frame = combined,
                            targetW = outW,
                            targetH = outH,
                            paint = paint,
                            colorMatrix = null,
                            shiftX = 0f,
                            shiftY = 0f,
                            cropScale = 1f,
                            ptsNs = outFrameIndex * outFrameDurationNs
                        )
                        combined.recycle()
                        outFrameIndex++

                        drainEncoder(encoder, muxer, bufferInfo, ptsState, audioFormat, { vt, at, started ->
                            videoTrackIdx = vt; audioTrackIdx = at; muxerStarted = started
                        }, { videoTrackIdx to muxerStarted })

                        if (i % 5 == 0) {
                            val pct = 6 + ((i * 82) / frameCount)
                            onProgress(pct.coerceIn(6, 88), "Combining split frames (${i + 1}/$frameCount)...")
                        }
                    }
                } finally {
                    runCatching { r1.release() }
                    runCatching { r2.release() }
                }
            }

            encoder.signalEndOfInputStream()
            drainEncoderEnd(encoder, muxer, bufferInfo, ptsState, { videoTrackIdx to muxerStarted })

            if (muxerStarted && audioTrackIdx >= 0 && primaryAudioExtractor != null && audioSrcTrack >= 0) {
                onProgress(90, "Muxing audio track...")
                muxAudioRange(primaryAudioExtractor, muxer, audioTrackIdx, 0L, 0L, ptsState.lastWrittenPtsUs.coerceAtLeast(300_000L))
            }

            onProgress(100, "Merge complete")
        } finally {
            runCatching { primaryAudioExtractor?.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { eglSurface?.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }

        VideoProcessResult(
            outputFile = outFile,
            width = outW,
            height = outH,
            fps = fps,
            bitrateBps = bitrate,
            processingTimeMs = System.currentTimeMillis() - startMs
        )
    }

    private fun drawAspectFit(
        canvas: Canvas,
        bmp: Bitmap,
        left: Float,
        top: Float,
        boxW: Float,
        boxH: Float,
        paint: Paint
    ) {
        val scale = min(boxW / bmp.width.toFloat(), boxH / bmp.height.toFloat())
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val dx = left + (boxW - drawW) / 2f
        val dy = top + (boxH - drawH) / 2f
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(bmp, m, paint)
    }
}
