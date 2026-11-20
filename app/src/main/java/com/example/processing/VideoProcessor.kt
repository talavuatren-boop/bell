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

        // Preserve orientation aspect
        val portrait = displayH > displayW
        val rawW = config.targetResolution.width
        val rawH = config.targetResolution.height
        val targetW = ((if (portrait) min(rawW, rawH) else max(rawW, rawH)) / 16) * 16
        val targetH = ((if (portrait) max(rawW, rawH) else min(rawW, rawH)) / 16) * 16

        val baseFps = config.targetFps.coerceIn(24, 60)
        val outFps = if (interpolateTo60Fps && baseFps < 60) 60 else baseFps

        // Cap sample step gracefully for thermal/RAM efficiency while maintaining smooth output
        val sampleFps = when (mode) {
            AiProcessingMode.FAST -> min(baseFps, 24)
            AiProcessingMode.BALANCED -> min(baseFps, 30)
            AiProcessingMode.QUALITY -> baseFps
        }

        val totalSourceFrames = ((effectiveDurMs / 1000.0) * sampleFps).roundToInt().coerceIn(4, 1800)
        val frameStepUs = (effectiveDurMs * 1000L) / totalSourceFrames.coerceAtLeast(1)

        val bitrate = when (mode) {
            AiProcessingMode.FAST -> 10_000_000
            AiProcessingMode.BALANCED -> 18_000_000
            AiProcessingMode.QUALITY -> 28_000_000
        }

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
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIdx = -1
        var audioTrackIdx = -1
        var muxerStarted = false

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
        val colorMatrix = buildColorMatrix(config)
        if (colorMatrix != null) {
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        // Stabilization state (Gaussian trajectory smoothing)
        var smoothDx = 0f
        var smoothDy = 0f
        var prevLumaGrid: IntArray? = null
        var prevFrameBitmap: Bitmap? = null

        var outFrameIndex = 0L
        val outFrameDurationUs = 1_000_000L / outFps

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
                    drawFrameToSurface(inputSurface, interp, targetW, targetH, paint, 0f, 0f, 1f)
                    drainEncoder(encoder, muxer, bufferInfo, audioFormat, { vt, at, started ->
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
                    frame = processedFrame,
                    targetW = targetW,
                    targetH = targetH,
                    paint = paint,
                    shiftX = shiftX,
                    shiftY = shiftY,
                    cropScale = cropScale
                )

                drainEncoder(encoder, muxer, bufferInfo, audioFormat, { vt, at, started ->
                    videoTrackIdx = vt; audioTrackIdx = at; muxerStarted = started
                }, { videoTrackIdx to muxerStarted })

                outFrameIndex++
                prevFrameBitmap?.recycle()
                prevFrameBitmap = processedFrame
            }

            encoder.signalEndOfInputStream()
            drainEncoderEnd(encoder, muxer, bufferInfo, { videoTrackIdx to muxerStarted })

            // Mux preserved audio track within trim window
            if (muxerStarted && audioTrackIdx >= 0 && audioExtractor != null && audioSrcTrack >= 0) {
                onProgress(90, "Synchronizing original audio track...")
                audioExtractor.seekTo(trimStart * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val aBuf = ByteBuffer.allocate(256 * 1024)
                val aInfo = MediaCodec.BufferInfo()
                while (true) {
                    coroutineContext.ensureActive()
                    val sampleSize = audioExtractor.readSampleData(aBuf, 0)
                    if (sampleSize < 0) break
                    val sampleTime = audioExtractor.sampleTime
                    if (sampleTime > trimEnd * 1000L) break
                    if (sampleTime >= trimStart * 1000L) {
                        aInfo.offset = 0
                        aInfo.size = sampleSize
                        aInfo.presentationTimeUs = (sampleTime - trimStart * 1000L).coerceAtLeast(0L)
                        aInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(audioTrackIdx, aBuf, aInfo)
                    }
                    audioExtractor.advance()
                }
            }

            onProgress(100, "Export complete")
        } finally {
            prevFrameBitmap?.recycle()
            runCatching { retriever.release() }
            runCatching { audioExtractor?.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
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

    private fun drawFrameToSurface(
        surface: android.view.Surface,
        frame: Bitmap,
        targetW: Int,
        targetH: Int,
        paint: Paint,
        shiftX: Float,
        shiftY: Float,
        cropScale: Float
    ) {
        val canvas: Canvas = surface.lockHardwareCanvas()
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
                    val at = if (audioFormat != null) muxer.addTrack(audioFormat) else -1
                    muxer.start()
                    onMuxerConfigured(vt, at, true)
                }
            } else if (outIdx >= 0) {
                val buf = encoder.getOutputBuffer(outIdx)
                val (vt, started) = trackState()
                if (buf != null && started && vt >= 0 && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(vt, buf, info)
                }
                encoder.releaseOutputBuffer(outIdx, false)
            }
        }
    }

    private fun drainEncoderEnd(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        trackState: () -> Pair<Int, Boolean>
    ) {
        var attempts = 0
        while (attempts < 40) {
            val outIdx = encoder.dequeueOutputBuffer(info, 10_000L)
            if (outIdx >= 0) {
                val buf = encoder.getOutputBuffer(outIdx)
                val (vt, started) = trackState()
                if (buf != null && started && vt >= 0 && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(vt, buf, info)
                }
                encoder.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            } else {
                attempts++
            }
        }
    }

    private fun buildColorMatrix(config: VideoEditConfig): ColorMatrix? {
        val hasEdits = abs(config.brightness) > 0.02f ||
            abs(config.contrast - 1f) > 0.02f ||
            abs(config.saturation - 1f) > 0.02f ||
            abs(config.temperature) > 0.02f ||
            config.applyAiColor
        if (!hasEdits) return null

        val cm = ColorMatrix()
        val sat = if (config.applyAiColor) (config.saturation * 1.12f).coerceIn(0f, 2f) else config.saturation
        cm.setSaturation(sat)

        val c = if (config.applyAiColor) (config.contrast * 1.06f) else config.contrast
        val b = config.brightness * 90f + (1f - c) * 128f
        val t = config.temperature * 24f
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
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIdx = -1
        var audioTrackIdx = -1
        var muxerStarted = false

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
                            val canvas = inputSurface.lockHardwareCanvas()
                            try {
                                canvas.drawColor(android.graphics.Color.BLACK)
                                drawAspectFit(canvas, raw, 0f, 0f, outW.toFloat(), outH.toFloat(), paint)
                            } finally {
                                inputSurface.unlockCanvasAndPost(canvas)
                            }
                            raw.recycle()

                            drainEncoder(encoder, muxer, bufferInfo, audioFormat, { vt, at, started ->
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
                        val canvas = inputSurface.lockHardwareCanvas()
                        try {
                            canvas.drawColor(android.graphics.Color.BLACK)
                            if (layout == VideoMergeLayout.SIDE_BY_SIDE) {
                                val halfW = outW / 2f
                                f1?.let { drawAspectFit(canvas, it, 0f, 0f, halfW, outH.toFloat(), paint) }
                                f2?.let { drawAspectFit(canvas, it, halfW, 0f, halfW, outH.toFloat(), paint) }
                            } else {
                                val halfH = outH / 2f
                                f1?.let { drawAspectFit(canvas, it, 0f, 0f, outW.toFloat(), halfH, paint) }
                                f2?.let { drawAspectFit(canvas, it, 0f, halfH, outW.toFloat(), halfH, paint) }
                            }
                        } finally {
                            inputSurface.unlockCanvasAndPost(canvas)
                        }
                        f1?.recycle()
                        f2?.recycle()

                        drainEncoder(encoder, muxer, bufferInfo, audioFormat, { vt, at, started ->
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
            drainEncoderEnd(encoder, muxer, bufferInfo, { videoTrackIdx to muxerStarted })

            if (muxerStarted && audioTrackIdx >= 0 && primaryAudioExtractor != null && audioSrcTrack >= 0) {
                onProgress(90, "Muxing audio track...")
                primaryAudioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val aBuf = ByteBuffer.allocate(256 * 1024)
                val aInfo = MediaCodec.BufferInfo()
                while (true) {
                    coroutineContext.ensureActive()
                    val sampleSize = primaryAudioExtractor.readSampleData(aBuf, 0)
                    if (sampleSize < 0) break
                    aInfo.offset = 0
                    aInfo.size = sampleSize
                    aInfo.presentationTimeUs = primaryAudioExtractor.sampleTime.coerceAtLeast(0L)
                    aInfo.flags = primaryAudioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIdx, aBuf, aInfo)
                    primaryAudioExtractor.advance()
                }
            }

            onProgress(100, "Merge complete")
        } finally {
            runCatching { primaryAudioExtractor?.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
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
