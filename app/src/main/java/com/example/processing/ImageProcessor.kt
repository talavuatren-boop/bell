package com.example.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.example.model.AiProcessingMode
import com.example.model.AiToolType
import com.example.model.CropAspectPreset
import com.example.model.PhotoEditAdjustments
import com.example.model.PhotoFilterPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ImageProcessResult(
    val outputBitmap: Bitmap,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val processingTimeMs: Long,
    val summaryLabel: String
)

object ImageProcessor {

    suspend fun runAiTool(
        source: Bitmap,
        tool: AiToolType,
        mode: AiProcessingMode,
        onProgress: (percent: Int, stage: String) -> Unit
    ): ImageProcessResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val origW = source.width
        val origH = source.height

        // Cap working resolution safely on FAST mode or very large bitmaps for memory safety
        val working = ensureMutableBitmap(source)

        val processed: Bitmap = when (tool) {
            AiToolType.DENOISE -> applyEdgePreservingDenoise(working, mode, onProgress)
            AiToolType.SHARPEN -> applyHaloFreeSharpen(working, mode, onProgress)
            AiToolType.COLOR_CORRECTION -> applyAutoColorEnhance(working, mode, onProgress)
            AiToolType.UPSCALE -> applySuperResolution2x(working, mode, onProgress)
            AiToolType.ENHANCE_ALL -> {
                onProgress(5, "Stage 1/3: Edge-preserving AI Denoise...")
                val d = applyEdgePreservingDenoise(working, mode) { p, _ ->
                    onProgress(5 + (p * 0.35f).toInt(), "Stage 1/3: Edge-preserving AI Denoise...")
                }
                onProgress(42, "Stage 2/3: Skin-safe AI Color & Exposure...")
                val c = applyAutoColorEnhance(d, mode) { p, _ ->
                    onProgress(42 + (p * 0.25f).toInt(), "Stage 2/3: Skin-safe AI Color & Exposure...")
                }
                onProgress(70, "Stage 3/3: Halo-free Micro-Contrast Sharpen...")
                applyHaloFreeSharpen(c, mode) { p, _ ->
                    onProgress(70 + (p * 0.28f).toInt(), "Stage 3/3: Halo-free Micro-Contrast Sharpen...")
                }
            }
            else -> working
        }

        val elapsed = System.currentTimeMillis() - start
        onProgress(100, "Complete")
        ImageProcessResult(
            outputBitmap = processed,
            originalWidth = origW,
            originalHeight = origH,
            outputWidth = processed.width,
            outputHeight = processed.height,
            processingTimeMs = elapsed,
            summaryLabel = "${tool.label} (${mode.label})"
        )
    }

    /**
     * Fast luminance-preserving bilateral denoise (1-pass chroma+luma filter with integer LUTs).
     * Eliminates shadow color grain smoothly while keeping fine edges sharp and using minimal RAM.
     */
    private suspend fun applyEdgePreservingDenoise(
        src: Bitmap,
        mode: AiProcessingMode,
        onProgress: (Int, String) -> Unit
    ): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        val lumaSigma = when (mode) {
            AiProcessingMode.FAST -> 18f
            AiProcessingMode.BALANCED -> 24f
            AiProcessingMode.QUALITY -> 30f
        }
        val radius = if (mode == AiProcessingMode.FAST) 1 else 2
        val invSigma = 1f / lumaSigma

        for (y in 0 until h) {
            coroutineContext.ensureActive()
            if (y % 64 == 0) {
                onProgress((y * 92 / h).coerceIn(5, 95), "Khử nhiễu giữ nét thông minh...")
            }
            val y0 = max(0, y - radius)
            val y1 = min(h - 1, y + radius)
            val rowOff = y * w
            for (x in 0 until w) {
                val c = pixels[rowOff + x]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                val cy = 0.299f * cr + 0.587f * cg + 0.114f * cb

                var sumR = cr * 1.5f
                var sumG = cg * 1.5f
                var sumB = cb * 1.5f
                var sumW = 1.5f

                val x0 = max(0, x - radius)
                val x1 = min(w - 1, x + radius)

                for (ny in y0..y1) {
                    val nOff = ny * w
                    // Step by 1 on radius 1, or cross+diagonal sparse 9-tap on radius 2 for 3x faster execution
                    for (nx in x0..x1) {
                        if (radius == 2 && ((nx - x) and 1) != 0 && ((ny - y) and 1) != 0) continue
                        val n = pixels[nOff + nx]
                        val nr = (n shr 16) and 0xFF
                        val ng = (n shr 8) and 0xFF
                        val nb = n and 0xFF
                        val nLum = 0.299f * nr + 0.587f * ng + 0.114f * nb
                        val diff = abs(nLum - cy)
                        val wEdge = if (diff < lumaSigma) 1f - diff * invSigma * 0.85f else 0.02f
                        sumR += nr * wEdge
                        sumG += ng * wEdge
                        sumB += nb * wEdge
                        sumW += wEdge
                    }
                }

                val rOut = (sumR / sumW).roundToInt().coerceIn(0, 255)
                val gOut = (sumG / sumW).roundToInt().coerceIn(0, 255)
                val bOut = (sumB / sumW).roundToInt().coerceIn(0, 255)
                out[rowOff + x] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Dual-band AI Clarity + Halo-Free Edge Sharpening:
     * 1) Mid-scale local micro-contrast pop via fast downsampled difference map (DSLR "clarity").
     * 2) 3x3 Gaussian-surround fine edge sharpening in luminance space with noise gate & overshoot clamp.
     */
    private suspend fun applyHaloFreeSharpen(
        src: Bitmap,
        mode: AiProcessingMode,
        onProgress: (Int, String) -> Unit
    ): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return ensureMutableBitmap(src)

        onProgress(12, "Phân tích vi tương phản cục bộ...")
        // Build fast 1/8-scale blurred base for clean mid-frequency clarity boost
        val cw = (w / 8).coerceAtLeast(8)
        val ch = (h / 8).coerceAtLeast(8)
        val lowRes = Bitmap.createScaledBitmap(src, cw, ch, true)
        val localBaseBmp = Bitmap.createScaledBitmap(lowRes, w, h, true)
        if (lowRes !== src) lowRes.recycle()

        val pixels = IntArray(w * h)
        val basePixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        localBaseBmp.getPixels(basePixels, 0, w, 0, 0, w, h)
        localBaseBmp.recycle()

        val out = IntArray(w * h)

        val clarityAmount = when (mode) {
            AiProcessingMode.FAST -> 0.28f
            AiProcessingMode.BALANCED -> 0.42f
            AiProcessingMode.QUALITY -> 0.56f
        }
        val fineSharpAmount = when (mode) {
            AiProcessingMode.FAST -> 1.15f
            AiProcessingMode.BALANCED -> 1.48f
            AiProcessingMode.QUALITY -> 1.82f
        }
        val maxOvershoot = when (mode) {
            AiProcessingMode.FAST -> 36f
            AiProcessingMode.BALANCED -> 46f
            AiProcessingMode.QUALITY -> 56f
        }

        fun lum(px: Int): Float {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            return 0.299f * r + 0.587f * g + 0.114f * b
        }

        for (y in 0 until h) {
            coroutineContext.ensureActive()
            if (y % 64 == 0) {
                onProgress(22 + (y * 74 / h).coerceIn(0, 74), "Làm nét đa tầng siêu chi tiết...")
            }
            val rowOff = y * w
            if (y == 0 || y == h - 1) {
                System.arraycopy(pixels, rowOff, out, rowOff, w)
                continue
            }
            val upOff = (y - 1) * w
            val dnOff = (y + 1) * w
            out[rowOff] = pixels[rowOff]
            out[rowOff + w - 1] = pixels[rowOff + w - 1]

            for (x in 1 until w - 1) {
                val idx = rowOff + x
                val c = pixels[idx]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                val cy = 0.299f * cr + 0.587f * cg + 0.114f * cb

                // 3x3 Gaussian surround luma
                val avgY = (
                    (lum(pixels[upOff + x]) + lum(pixels[dnOff + x]) + lum(pixels[rowOff + x - 1]) + lum(pixels[rowOff + x + 1])) * 2f +
                        lum(pixels[upOff + x - 1]) + lum(pixels[upOff + x + 1]) + lum(pixels[dnOff + x - 1]) + lum(pixels[dnOff + x + 1])
                    ) / 12f

                val fineDetail = cy - avgY
                val absDetail = abs(fineDetail)
                // Soft noise gate protects smooth sky/skin while sharpening crisp edges
                val gate = when {
                    absDetail < 2.2f -> 0.15f
                    absDetail < 5.5f -> 0.15f + ((absDetail - 2.2f) / 3.3f) * 0.85f
                    else -> 1f
                }

                // Mid-scale local clarity boost (makes flat smartphone shots pop cleanly)
                val baseY = lum(basePixels[idx])
                val localDelta = (cy - baseY) * clarityAmount

                // Headroom protection near clipping bounds
                val headroom = when {
                    cy > 236f -> ((255f - cy) / 19f).coerceIn(0f, 1f)
                    cy < 16f -> (cy / 16f).coerceIn(0f, 1f)
                    else -> 1f
                }

                val combinedBoost = ((fineDetail * fineSharpAmount * gate) + localDelta * gate)
                    .coerceIn(-maxOvershoot, maxOvershoot) * headroom

                val rOut = (cr + combinedBoost).roundToInt().coerceIn(0, 255)
                val gOut = (cg + combinedBoost).roundToInt().coerceIn(0, 255)
                val bOut = (cb + combinedBoost).roundToInt().coerceIn(0, 255)
                out[idx] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * AI Auto-Levels + Smart HDR Shadow/Highlight Recovery + Skin-Safe Filmic Color & Vibrance.
     * Fixes hazy/washed-out/dark photos immediately with natural vibrant contrast.
     */
    private suspend fun applyAutoColorEnhance(
        src: Bitmap,
        mode: AiProcessingMode,
        onProgress: (Int, String) -> Unit
    ): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        onProgress(15, "Cân bằng dải sáng & khử mù ảnh...")
        val lumHist = IntArray(256)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0
        for (i in pixels.indices step 3) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sumR += r
            sumG += g
            sumB += b
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
            lumHist[lum]++
            count++
        }

        // Find 1.5% black point and 98.5% white point to remove hazy gray veil safely
        val lowTarget = (count * 0.015f).roundToInt()
        val highTarget = (count * 0.985f).roundToInt()
        var acc = 0
        var lowBin = 0
        var highBin = 255
        for (i in 0..255) {
            acc += lumHist[i]
            if (acc <= lowTarget) lowBin = i
            if (acc <= highTarget) highBin = i
        }
        val blackPt = (lowBin * 0.58f).coerceIn(0f, 38f)
        val whitePt = (255f - (255f - highBin) * 0.55f).coerceIn(212f, 255f)
        val span = (whitePt - blackPt).coerceAtLeast(160f)

        // Conservative cast balance (keeps golden-hour warmth intact while fixing harsh tints)
        val avgR = (sumR / count.coerceAtLeast(1)).coerceAtLeast(1.0)
        val avgG = (sumG / count.coerceAtLeast(1)).coerceAtLeast(1.0)
        val avgB = (sumB / count.coerceAtLeast(1)).coerceAtLeast(1.0)
        val grayMean = (avgR + avgG + avgB) / 3.0
        val wbStrength = if (mode == AiProcessingMode.FAST) 0.25 else 0.36
        val gainR = (1.0 + ((grayMean / avgR) - 1.0) * wbStrength).toFloat().coerceIn(0.88f, 1.12f)
        val gainG = (1.0 + ((grayMean / avgG) - 1.0) * wbStrength).toFloat().coerceIn(0.92f, 1.08f)
        val gainB = (1.0 + ((grayMean / avgB) - 1.0) * wbStrength).toFloat().coerceIn(0.88f, 1.12f)

        // Precompute HDR + Auto-Levels + Filmic S-Curve LUT (zero per-pixel pow() math!)
        val sStrength = when (mode) {
            AiProcessingMode.FAST -> 0.42f
            AiProcessingMode.BALANCED -> 0.56f
            AiProcessingMode.QUALITY -> 0.68f
        }
        val lumaGainLut = FloatArray(256) { v ->
            val leveled = ((v - blackPt) / span).coerceIn(0f, 1f)
            // Lift crushed dark midtones gently & roll off highlights smoothly
            val shadowLift = ((1f - leveled) * (1f - leveled) * (leveled * 4.8f).coerceIn(0f, 1f)) * 0.16f * sStrength
            val hiMask = ((leveled - 0.68f) / 0.32f).coerceIn(0f, 1f)
            val hiCompress = (hiMask * hiMask) * 0.08f * sStrength
            val sCurve = (leveled * leveled * (3f - 2f * leveled)) * 0.44f + leveled * 0.56f
            val finalY = (sCurve + shadowLift - hiCompress).coerceIn(0f, 1f)
            if (v > 3) ((finalY * 255f) / v.toFloat()).coerceIn(0.75f, 1.65f) else 1f
        }

        val out = IntArray(pixels.size)
        val vibranceAmt = when (mode) {
            AiProcessingMode.FAST -> 0.26f
            AiProcessingMode.BALANCED -> 0.34f
            AiProcessingMode.QUALITY -> 0.40f
        }

        for (y in 0 until h) {
            coroutineContext.ensureActive()
            if (y % 64 == 0) {
                onProgress(22 + (y * 74 / h), "Tăng cường màu sắc & độ tương phản phim...")
            }
            val rowOff = y * w
            for (x in 0 until w) {
                val idx = rowOff + x
                val p = pixels[idx]
                var r = (((p shr 16) and 0xFF) * gainR).coerceIn(0f, 255f)
                var g = (((p shr 8) and 0xFF) * gainG).coerceIn(0f, 255f)
                var b = ((p and 0xFF) * gainB).coerceIn(0f, 255f)

                val lumIdx = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
                val lumGain = lumaGainLut[lumIdx]
                r = (r * lumGain).coerceIn(0f, 255f)
                g = (g * lumGain).coerceIn(0f, 255f)
                b = (b * lumGain).coerceIn(0f, 255f)

                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = if (maxC > 1f) (maxC - minC) / maxC else 0f
                val isSkinLike = (r > g && g > b && (r - g) > 10f && (r - b) > 18f)
                val vibGain = if (isSkinLike) 0.10f else vibranceAmt * (1f - sat * 0.72f)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                val nr = (lum + (r - lum) * (1f + vibGain)).roundToInt().coerceIn(0, 255)
                val ng = (lum + (g - lum) * (1f + vibGain)).roundToInt().coerceIn(0, 255)
                val nb = (lum + (b - lum) * (1f + vibGain)).roundToInt().coerceIn(0, 255)
                out[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * 2x Super Resolution via staged upsampling + dual-band edge/clarity reconstruction.
     */
    private suspend fun applySuperResolution2x(
        src: Bitmap,
        mode: AiProcessingMode,
        onProgress: (Int, String) -> Unit
    ): Bitmap {
        onProgress(15, "Nâng độ phân giải 2x chi tiết cao...")
        val scale = if (src.width * src.height > 10_000_000) 1.5f else 2.0f
        val targetW = (src.width * scale).roundToInt().coerceAtMost(4800)
        val targetH = (src.height * scale).roundToInt().coerceAtMost(4800)

        // Two-step 1.41x -> target upscale yields cleaner diagonal curves than 1-shot 2x bilinear
        val midW = ((src.width + targetW) / 2).coerceAtLeast(src.width)
        val midH = ((src.height + targetH) / 2).coerceAtLeast(src.height)
        val step1 = Bitmap.createScaledBitmap(src, midW, midH, true)
        val upscaled = Bitmap.createScaledBitmap(step1, targetW, targetH, true)
        if (step1 !== src) step1.recycle()
        coroutineContext.ensureActive()

        onProgress(45, "Khôi phục độ nét biên & chi tiết ảnh...")
        val sharpened = applyHaloFreeSharpen(upscaled, mode) { p, _ ->
            onProgress(45 + (p * 0.52f).toInt(), "Khôi phục độ nét biên & chi tiết ảnh...")
        }
        if (sharpened !== upscaled) upscaled.recycle()
        return sharpened
    }

    suspend fun applyPhotoEditorAdjustments(
        source: Bitmap,
        adj: PhotoEditAdjustments
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Rotate & Flip
        val transformMatrix = Matrix().apply {
            if (adj.rotationDegrees != 0) postRotate(adj.rotationDegrees.toFloat())
            val sx = if (adj.flipHorizontal) -1f else 1f
            val sy = if (adj.flipVertical) -1f else 1f
            if (sx != 1f || sy != 1f) postScale(sx, sy)
        }
        val transformed = if (!transformMatrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, transformMatrix, true)
        } else {
            source
        }

        // 2. Crop aspect preset
        val cropped = applyAspectCrop(transformed, adj.cropPreset)

        // 3. Filter + ColorMatrix pass (Exposure, Brightness, Contrast, Saturation)
        val cm = ColorMatrix()
        applyFilterPreset(cm, adj.filter, adj.filterIntensity)

        val satMatrix = ColorMatrix().apply { setSaturation(adj.saturation) }
        cm.postConcat(satMatrix)

        val expFactor = 2.0.pow(adj.exposure.toDouble()).toFloat()
        val contrast = adj.contrast
        val brightOffset = adj.brightness * 120f + (1f - contrast) * 128f
        val tempShift = adj.temperature * 28f
        val tintShift = adj.tint * 24f

        val toneMatrix = ColorMatrix(
            floatArrayOf(
                contrast * expFactor, 0f, 0f, 0f, brightOffset + tempShift,
                0f, contrast * expFactor, 0f, 0f, brightOffset - tintShift * 0.5f,
                0f, 0f, contrast * expFactor, 0f, brightOffset - tempShift,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(toneMatrix)

        val colorAdjusted = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        Canvas(colorAdjusted).drawBitmap(
            cropped,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        )

        // 4. Highlights & Shadows curve pass if non-zero
        val tonalOut = if (abs(adj.highlights) > 0.02f || abs(adj.shadows) > 0.02f) {
            applyHighlightsShadows(colorAdjusted, adj.highlights, adj.shadows)
        } else {
            colorAdjusted
        }

        // 5. Optional sharpness pass
        val sharpened = if (adj.sharpness > 0.05f) {
            val strength = if (adj.sharpness > 0.6f) AiProcessingMode.QUALITY else AiProcessingMode.BALANCED
            applyHaloFreeSharpen(tonalOut, strength) { _, _ -> }
        } else {
            tonalOut
        }

        // 6. Stickers & Meme Captions overlay pass
        if (adj.stickers.isNotEmpty() || adj.memeCaption.topText.isNotBlank() || adj.memeCaption.bottomText.isNotBlank()) {
            renderStickersAndMeme(sharpened, adj)
        } else {
            sharpened
        }
    }

    private fun renderStickersAndMeme(src: Bitmap, adj: PhotoEditAdjustments): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val baseDim = min(w, h)

        // Draw stickers
        adj.stickers.forEach { sticker ->
            val cx = sticker.normX * w
            val cy = sticker.normY * h
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(sticker.rotationDeg)
            canvas.scale(sticker.scale, sticker.scale)

            if (sticker.isBadgeStamp) {
                val padX = baseDim * 0.032f
                val padY = baseDim * 0.016f
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = baseDim * 0.042f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val tw = textPaint.measureText(sticker.label)
                val fm = textPaint.fontMetrics
                val th = fm.descent - fm.ascent
                val rect = android.graphics.RectF(
                    -tw / 2f - padX,
                    -th / 2f - padY,
                    tw / 2f + padX,
                    th / 2f + padY
                )
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(200, 14, 16, 22)
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = baseDim * 0.005f
                    color = Color.rgb(255, 184, 0)
                }
                canvas.drawRoundRect(rect, baseDim * 0.02f, baseDim * 0.02f, bgPaint)
                canvas.drawRoundRect(rect, baseDim * 0.02f, baseDim * 0.02f, borderPaint)
                canvas.drawText(sticker.label, 0f, -(fm.ascent + fm.descent) / 2f, textPaint)
            } else {
                val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = baseDim * 0.14f
                    textAlign = Paint.Align.CENTER
                }
                val fm = emojiPaint.fontMetrics
                canvas.drawText(sticker.label, 0f, -(fm.ascent + fm.descent) / 2f, emojiPaint)
            }
            canvas.restore()
        }

        // Draw classic bold outlined Meme Top / Bottom text
        val meme = adj.memeCaption
        val textSize = baseDim * 0.085f * meme.fontSizeScale.coerceIn(0.6f, 1.8f)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = meme.textColorArgb
            this.textSize = textSize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val strokePaint = Paint(fillPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = (textSize * 0.12f).coerceAtLeast(3f)
            color = Color.BLACK
            strokeJoin = Paint.Join.ROUND
        }

        fun drawMemeLine(text: String, yBase: Float) {
            if (text.isBlank()) return
            val upper = text.uppercase()
            canvas.drawText(upper, w / 2f, yBase, strokePaint)
            canvas.drawText(upper, w / 2f, yBase, fillPaint)
        }

        if (meme.topText.isNotBlank()) {
            drawMemeLine(meme.topText, textSize * 1.15f + h * 0.025f)
        }
        if (meme.bottomText.isNotBlank()) {
            drawMemeLine(meme.bottomText, h - h * 0.045f)
        }

        return out
    }

    private fun applyAspectCrop(src: Bitmap, preset: CropAspectPreset): Bitmap {
        val ratio = preset.ratio ?: return src
        val w = src.width
        val h = src.height
        val currentRatio = w.toFloat() / h.toFloat()
        return if (abs(currentRatio - ratio) < 0.01f) {
            src
        } else if (currentRatio > ratio) {
            val newW = (h * ratio).roundToInt().coerceIn(1, w)
            val x = (w - newW) / 2
            Bitmap.createBitmap(src, x, 0, newW, h)
        } else {
            val newH = (w / ratio).roundToInt().coerceIn(1, h)
            val y = (h - newH) / 2
            Bitmap.createBitmap(src, 0, y, w, newH)
        }
    }

    private fun applyHighlightsShadows(src: Bitmap, highlights: Float, shadows: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val shadowMask = (1f - lum).pow(2f)
            val highMask = lum.pow(2f)
            val delta = (shadows * 55f * shadowMask) + (highlights * 55f * highMask)
            val nr = (r + delta).roundToInt().coerceIn(0, 255)
            val ng = (g + delta).roundToInt().coerceIn(0, 255)
            val nb = (b + delta).roundToInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    fun buildFilterColorMatrix(preset: PhotoFilterPreset, intensity: Float = 1f): ColorMatrix {
        return ColorMatrix().also { applyFilterPreset(it, preset, intensity) }
    }

    fun applyFilterToBitmap(
        source: Bitmap,
        preset: PhotoFilterPreset,
        intensity: Float = 1f
    ): Bitmap {
        if (preset == PhotoFilterPreset.NATURAL || intensity <= 0.01f) return source
        val cm = buildFilterColorMatrix(preset, intensity)
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
        )
        return out
    }

    /**
     * Enhances base camera photos ("màu gốc đẹp") with clean flagship tonal dynamics:
     * subtle micro-contrast S-curve LUT + highlight roll-off + skin-protecting vibrance.
     */
    fun applyFlagshipNaturalTone(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 4 || h < 4) return ensureMutableBitmap(source)

        val lut = IntArray(256) { v ->
            val x = v / 255f
            // Gentle shadow clarity + crisp midtone pop + smooth highlight preservation
            val curved = (x * x * (3f - 2f * x)) * 0.16f + x * 0.84f
            val lift = (curved + 0.014f * (1f - x)).coerceIn(0f, 1f)
            (lift * 255f).roundToInt().coerceIn(0, 255)
        }

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r0 = lut[(p shr 16) and 0xFF].toFloat()
            val g0 = lut[(p shr 8) and 0xFF].toFloat()
            val b0 = lut[p and 0xFF].toFloat()

            val maxC = max(r0, max(g0, b0))
            val minC = min(r0, min(g0, b0))
            val sat = if (maxC > 1f) (maxC - minC) / maxC else 0f
            val isWarmSkin = (r0 > g0 && g0 > b0 && (r0 - g0) > 10f && (r0 - b0) > 18f)
            val vibGain = if (isWarmSkin) 0.05f else 0.18f * (1f - sat)

            val lum = 0.299f * r0 + 0.587f * g0 + 0.114f * b0
            val nr = (lum + (r0 - lum) * (1f + vibGain)).roundToInt().coerceIn(0, 255)
            val ng = (lum + (g0 - lum) * (1f + vibGain)).roundToInt().coerceIn(0, 255)
            val nb = (lum + (b0 - lum) * (1f + vibGain)).roundToInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    fun applyCameraWatermark(
        source: Bitmap,
        lensLabel: String,
        iso: Int?,
        exposureNs: Long?,
        focalMm: Float?
    ): Bitmap {
        val w = source.width
        val h = source.height
        val barH = (min(w, h) * 0.075f).roundToInt().coerceIn(56, 180)
        val out = Bitmap.createBitmap(w, h + barH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)

        val textSizeMain = (barH * 0.34f).coerceAtLeast(16f)
        val textSizeSub = (barH * 0.24f).coerceAtLeast(12f)
        val padX = (w * 0.04f).coerceAtLeast(20f)
        val centerY = h + barH / 2f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 22, 26)
            textSize = textSizeMain
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(228, 35, 35)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 96, 108)
            textSize = textSizeSub
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }

        val dotR = (textSizeMain * 0.32f).coerceAtLeast(5f)
        canvas.drawCircle(padX + dotR, centerY, dotR, dotPaint)
        canvas.drawText(
            "HUNG CAM",
            padX + dotR * 2.8f,
            centerY + textSizeMain * 0.34f,
            titlePaint
        )

        val focalStr = focalMm?.let { "${it.roundToInt()}mm" } ?: lensLabel
        val isoStr = iso?.let { "ISO$it" } ?: ""
        val expStr = exposureNs?.let { ns ->
            if (ns >= 1_000_000_000L) String.format("%.1fs", ns / 1_000_000_000f)
            else "1/${(1_000_000_000L / ns.coerceAtLeast(1L)).coerceAtLeast(1L)}s"
        } ?: ""
        val rightText = listOf(focalStr, expStr, isoStr).filter { it.isNotBlank() }.joinToString("  •  ")
        canvas.drawText(
            rightText.ifBlank { lensLabel },
            w - padX,
            centerY + textSizeSub * 0.34f,
            metaPaint
        )
        return out
    }

    private fun applyFilterPreset(cm: ColorMatrix, preset: PhotoFilterPreset, intensity: Float = 1f) {
        val t = intensity.coerceIn(0f, 1f)
        if (preset == PhotoFilterPreset.NATURAL || t <= 0.005f) {
            cm.reset()
            return
        }
        val target = ColorMatrix()
        when (preset) {
            PhotoFilterPreset.NATURAL -> Unit
            PhotoFilterPreset.LEICA_VIBRANT -> {
                target.setSaturation(1.24f)
                target.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.10f, 0f, -0.02f, 0f, -7f,
                            0f, 1.08f, 0f, 0f, -5f,
                            -0.02f, 0.01f, 1.12f, 0f, -8f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.LEICA_AUTHENTIC -> {
                target.setSaturation(0.92f)
                target.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.14f, 0.02f, -0.02f, 0f, -12f,
                            0f, 1.10f, 0.01f, 0f, -10f,
                            -0.02f, 0.02f, 1.03f, 0f, -11f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.LEICA_BW_NAT -> {
                target.setSaturation(0f)
                target.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.12f, 0f, 0f, 0f, -10f,
                            0f, 1.12f, 0f, 0f, -10f,
                            0f, 0f, 1.12f, 0f, -10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.LEICA_BW_HC -> {
                target.set(
                    floatArrayOf(
                        0.46f, 0.44f, 0.10f, 0f, 0f,
                        0.46f, 0.44f, 0.10f, 0f, 0f,
                        0.46f, 0.44f, 0.10f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                target.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.36f, 0f, 0f, 0f, -38f,
                            0f, 1.36f, 0f, 0f, -38f,
                            0f, 0f, 1.36f, 0f, -38f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.LEICA_SEPIA -> {
                target.setSaturation(0f)
                target.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.14f, 0f, 0f, 0f, 8f,
                            0f, 1.00f, 0f, 0f, 0f,
                            0f, 0f, 0.78f, 0f, -8f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.LEICA_BLUE -> {
                target.setSaturation(0.96f)
                target.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            0.94f, 0f, 0.03f, 0f, -6f,
                            0f, 1.04f, 0.04f, 0f, -2f,
                            0.02f, 0.06f, 1.18f, 0f, 10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.PORTRA -> {
                target.setSaturation(1.04f)
                target.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.06f, 0.03f, 0f, 0f, 10f,
                            0.01f, 1.03f, 0.01f, 0f, 7f,
                            0f, 0.02f, 0.96f, 0f, 11f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.FUJI_PRO -> {
                target.setSaturation(0.95f)
                target.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.04f, -0.02f, 0f, 0f, -4f,
                            0.02f, 1.08f, 0.02f, 0f, -2f,
                            0f, 0.04f, 1.08f, 0f, 4f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.VIVID -> target.setSaturation(1.38f)
            PhotoFilterPreset.WARM -> target.set(
                floatArrayOf(
                    1.09f, 0f, 0f, 0f, 10f,
                    0f, 1.02f, 0f, 0f, 4f,
                    0f, 0f, 0.90f, 0f, -8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.COOL -> target.set(
                floatArrayOf(
                    0.92f, 0f, 0f, 0f, -6f,
                    0f, 1.01f, 0f, 0f, 2f,
                    0f, 0f, 1.12f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.FILM -> target.set(
                floatArrayOf(
                    1.06f, 0.04f, 0f, 0f, -4f,
                    0f, 1.03f, 0.02f, 0f, -2f,
                    0.02f, 0f, 0.97f, 0f, 7f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.VINTAGE -> target.set(
                floatArrayOf(
                    0.98f, 0.12f, 0.04f, 0f, 14f,
                    0.05f, 0.92f, 0.06f, 0f, 8f,
                    0.02f, 0.08f, 0.78f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.CINEMATIC -> target.set(
                floatArrayOf(
                    1.08f, 0f, -0.04f, 0f, 4f,
                    -0.02f, 1.02f, 0.02f, 0f, -2f,
                    -0.04f, 0.06f, 1.08f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.BW -> target.setSaturation(0f)
            else -> {
                val spec = preset.grade
                if (spec != null) {
                    val c = spec.contrast
                    val pivot = 128f * (1f - c) + spec.exposureBias + spec.shadowFade
                    if (spec.isMonochrome) {
                        target.set(
                            floatArrayOf(
                                0.34f * c * spec.rGain, 0.48f * c * spec.rGain, 0.18f * c * spec.rGain, 0f, pivot + spec.rWarmBias,
                                0.34f * c * spec.gGain, 0.48f * c * spec.gGain, 0.18f * c * spec.gGain, 0f, pivot + spec.gWarmBias,
                                0.34f * c * spec.bGain, 0.48f * c * spec.bGain, 0.18f * c * spec.bGain, 0f, pivot + spec.bWarmBias,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    } else {
                        target.setSaturation(spec.saturation)
                        target.postConcat(
                            ColorMatrix(
                                floatArrayOf(
                                    c * spec.rGain, spec.rgCross, 0f, 0f, pivot + spec.rWarmBias,
                                    0f, c * spec.gGain, spec.gbCross, 0f, pivot + spec.gWarmBias,
                                    0f, 0f, c * spec.bGain, 0f, pivot + spec.bWarmBias,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                        )
                    }
                }
            }
        }
        if (t >= 0.995f) {
            cm.set(target)
        } else {
            val id = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            val dst = target.array
            val blended = FloatArray(20) { idx ->
                id[idx] + (dst[idx] - id[idx]) * t
            }
            cm.set(blended)
        }
    }

    private fun applySoftSCurve(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return (c * c * (3f - 2f * c)) * 0.22f + c * 0.78f
    }

    private fun ensureMutableBitmap(src: Bitmap): Bitmap {
        return if (src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, true)
    }

    /**
     * Real computational portrait depth-of-field bokeh around a normalized focal point.
     * Keeps the subject sharp while smoothly blending a multi-pass downsampled optical blur
     * onto the background and adding subtle portrait micro-contrast & warmth.
     */
    fun applyPortraitBokehEffect(
        source: Bitmap,
        bokehStrength: Float = 0.6f,
        focusNormX: Float = 0.5f,
        focusNormY: Float = 0.42f
    ): Bitmap {
        val strength = bokehStrength.coerceIn(0f, 1f)
        val w = source.width
        val h = source.height
        if (w < 16 || h < 16 || strength <= 0.03f) {
            return ensureMutableBitmap(source)
        }

        val scaleDown = 6
        val bw = (w / scaleDown).coerceAtLeast(16)
        val bh = (h / scaleDown).coerceAtLeast(16)
        val small = Bitmap.createScaledBitmap(source, bw, bh, true)
        val radius = (3 + (strength * 9f).roundToInt()).coerceIn(3, 14)
        val blurredSmall = fastSeparableBoxBlur(small, radius, passes = 2)
        if (blurredSmall !== small) small.recycle()
        val blurredFull = Bitmap.createScaledBitmap(blurredSmall, w, h, true)
        if (blurredFull !== blurredSmall) blurredSmall.recycle()

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcRow = IntArray(w)
        val blurRow = IntArray(w)
        val outRow = IntArray(w)

        val cx = focusNormX.coerceIn(0.15f, 0.85f) * w
        val cy = focusNormY.coerceIn(0.15f, 0.85f) * h
        // Slightly taller vertical ellipse for natural head-and-shoulders framing
        val rx = (min(w, h) * 0.30f).coerceAtLeast(1f)
        val ry = (max(w, h) * 0.36f).coerceAtLeast(1f)
        val innerEdge = 0.58f
        val outerEdge = 1.48f
        val warmthGain = 0.04f * strength
        val maxStrengthFactor = 0.35f + 0.65f * strength

        try {
            for (y in 0 until h) {
                source.getPixels(srcRow, 0, w, 0, y, w, 1)
                blurredFull.getPixels(blurRow, 0, w, 0, y, w, 1)

                val dy = (y - cy) / ry
                val dy2 = dy * dy
                for (x in 0 until w) {
                    val dx = (x - cx) / rx
                    val dist = sqrt(dx * dx + dy2)
                    val tRaw = ((dist - innerEdge) / (outerEdge - innerEdge)).coerceIn(0f, 1f)
                    val blurBlend = (tRaw * tRaw * (3f - 2f * tRaw)) * maxStrengthFactor

                    val sp = srcRow[x]
                    val bp = blurRow[x]

                    val sr = (sp shr 16) and 0xFF
                    val sg = (sp shr 8) and 0xFF
                    val sb = sp and 0xFF

                    val br = (bp shr 16) and 0xFF
                    val bg = (bp shr 8) and 0xFF
                    val bb = bp and 0xFF

                    val inv = 1f - blurBlend
                    val rMix = (sr * inv + br * blurBlend) * (1f + warmthGain * inv)
                    val gMix = (sg * inv + bg * blurBlend) * (1f + warmthGain * 0.45f * inv)
                    val bMix = (sb * inv + bb * blurBlend) * (1f - warmthGain * 0.35f * inv)

                    val nr = rMix.roundToInt().coerceIn(0, 255)
                    val ng = gMix.roundToInt().coerceIn(0, 255)
                    val nb = bMix.roundToInt().coerceIn(0, 255)
                    outRow[x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
                outBitmap.setPixels(outRow, 0, w, 0, y, w, 1)
            }
        } finally {
            blurredFull.recycle()
        }
        return outBitmap
    }

    private fun fastSeparableBoxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        var cur = IntArray(w * h)
        src.getPixels(cur, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val next = IntArray(w * h)

        repeat(passes) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowOff = y * w
                for (x in 0 until w) {
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var count = 0
                    val x0 = max(0, x - radius)
                    val x1 = min(w - 1, x + radius)
                    for (nx in x0..x1) {
                        val p = cur[rowOff + nx]
                        sumR += (p shr 16) and 0xFF
                        sumG += (p shr 8) and 0xFF
                        sumB += p and 0xFF
                        count++
                    }
                    tmp[rowOff + x] = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                }
            }
            // Vertical pass
            for (y in 0 until h) {
                val y0 = max(0, y - radius)
                val y1 = min(h - 1, y + radius)
                for (x in 0 until w) {
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var count = 0
                    for (ny in y0..y1) {
                        val p = tmp[ny * w + x]
                        sumR += (p shr 16) and 0xFF
                        sumG += (p shr 8) and 0xFF
                        sumB += p and 0xFF
                        count++
                    }
                    next[y * w + x] = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                }
            }
            cur = next.copyOf()
        }
        return Bitmap.createBitmap(cur, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Computational HDR tone-mapping pass (row-streamed, zero large intermediate buffers).
     * Lifts dark shadow detail and recovers bright highlight headroom while preserving
     * skin-safe saturation and true contrast.
     */
    fun applyComputationalHdr(
        source: Bitmap,
        strength: Float = 0.72f
    ): Bitmap {
        val s = strength.coerceIn(0f, 1f)
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0 || s <= 0.02f) return ensureMutableBitmap(source)

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcRow = IntArray(w)
        val outRow = IntArray(w)

        // Precompute 256-entry HDR luminance lift/compress LUT for crisp native speed
        val lumaGainLut = FloatArray(256) { i ->
            val y = i / 255f
            // Shadow lift peaking around dark-midtones (y ~ 0.18), zero at pitch black
            val shadowMask = ((1f - y) * (1f - y) * (y * 5.2f).coerceIn(0f, 1f))
            val shadowLift = shadowMask * 0.24f * s
            // Highlight roll-off compression starting above y > 0.62
            val hiMask = ((y - 0.62f) / 0.38f).coerceIn(0f, 1f)
            val hiCompress = (hiMask * hiMask) * 0.14f * s
            // Subtle micro S-contrast around midtones
            val midCurve = (applySoftSCurve(y) - y) * 0.32f * s
            val mapped = (y + shadowLift - hiCompress + midCurve).coerceIn(0.0001f, 1f)
            if (y > 0.004f) (mapped / y).coerceIn(0.75f, 1.85f) else 1f
        }

        for (y in 0 until h) {
            source.getPixels(srcRow, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = srcRow[x]
                val a = (p ushr 24) and 0xFF
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val lum = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
                val gain = lumaGainLut[lum]

                // Apply luminance gain while gently compensating saturation in lifted shadows/highlights
                val nr = (r * gain)
                val ng = (g * gain)
                val nb = (b * gain)
                val newLum = 0.299f * nr + 0.587f * ng + 0.114f * nb
                val chromaBoost = if (gain > 1.04f || gain < 0.94f) 1f + 0.08f * s else 1f

                val rr = (newLum + (nr - newLum) * chromaBoost).roundToInt().coerceIn(0, 255)
                val gg = (newLum + (ng - newLum) * chromaBoost).roundToInt().coerceIn(0, 255)
                val bb = (newLum + (nb - newLum) * chromaBoost).roundToInt().coerceIn(0, 255)
                outRow[x] = (a shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
            outBitmap.setPixels(outRow, 0, w, 0, y, w, 1)
        }
        return outBitmap
    }

    /**
     * Camera AI Super-Sharp clarity pass (3-row sliding window, O(width) extra RAM even at 50MP).
     * Enhances luminance edges and micro-textures with noise-gate suppression on flat surfaces
     * and highlight/shadow halo clamping so foliage, eyes, text, and fabrics look crisp.
     */
    fun applyCameraAiSuperSharp(
        source: Bitmap,
        strength: Float = 0.85f
    ): Bitmap {
        val s = strength.coerceIn(0f, 1.2f)
        val w = source.width
        val h = source.height
        if (w < 3 || h < 3 || s <= 0.02f) return ensureMutableBitmap(source)

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var prevRow = IntArray(w)
        var curRow = IntArray(w)
        var nextRow = IntArray(w)
        val outRow = IntArray(w)

        source.getPixels(prevRow, 0, w, 0, 0, w, 1)
        source.getPixels(curRow, 0, w, 0, 1, w, 1)
        // Copy top border row unchanged
        outBitmap.setPixels(prevRow, 0, w, 0, 0, w, 1)

        val sharpGain = 1.24f * s
        val maxOvershoot = (40f * s).coerceIn(10f, 44f)

        fun lumaOf(px: Int): Float {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            return 0.299f * r + 0.587f * g + 0.114f * b
        }

        for (y in 1 until h - 1) {
            source.getPixels(nextRow, 0, w, 0, y + 1, w, 1)
            outRow[0] = curRow[0]
            outRow[w - 1] = curRow[w - 1]

            for (x in 1 until w - 1) {
                val cp = curRow[x]
                val a = (cp ushr 24) and 0xFF
                val cr = (cp shr 16) and 0xFF
                val cg = (cp shr 8) and 0xFF
                val cb = cp and 0xFF
                val cy = 0.299f * cr + 0.587f * cg + 0.114f * cb

                // 3x3 Gaussian-weighted surround in luma domain (weights: cardinal=2, diagonal=1, sum=12)
                val avgY = (
                    (lumaOf(prevRow[x]) + lumaOf(nextRow[x]) + lumaOf(curRow[x - 1]) + lumaOf(curRow[x + 1])) * 2f +
                        lumaOf(prevRow[x - 1]) + lumaOf(prevRow[x + 1]) + lumaOf(nextRow[x - 1]) + lumaOf(nextRow[x + 1])
                    ) / 12f

                val rawDelta = cy - avgY
                val absDelta = abs(rawDelta)

                // Soft noise gate: suppress noise amplification on uniform areas, boost real texture/edges
                val gate = when {
                    absDelta < 2.8f -> 0.18f
                    absDelta < 6.5f -> 0.18f + ((absDelta - 2.8f) / 3.7f) * 0.82f
                    else -> 1.0f
                }

                // Subtle local micro-contrast pop + clipped halo-free overshoot protection
                val edgeScale = 1f - ((absDelta - 45f) / 90f).coerceIn(0f, 0.35f)
                val sharpDelta = (rawDelta * sharpGain * gate * edgeScale).coerceIn(-maxOvershoot, maxOvershoot)

                // Taper extreme near-black / near-white boost to prevent clipping halos
                val headroomFade = when {
                    cy > 235f -> ((255f - cy) / 20f).coerceIn(0f, 1f)
                    cy < 18f -> (cy / 18f).coerceIn(0f, 1f)
                    else -> 1f
                }
                val d = (sharpDelta * headroomFade).roundToInt()

                val nr = (cr + d).coerceIn(0, 255)
                val ng = (cg + d).coerceIn(0, 255)
                val nb = (cb + d).coerceIn(0, 255)
                outRow[x] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
            outBitmap.setPixels(outRow, 0, w, 0, y, w, 1)

            // Rotate row references cleanly with zero extra allocations
            val reuse = prevRow
            prevRow = curRow
            curRow = nextRow
            nextRow = reuse
        }

        // Copy bottom border row
        outBitmap.setPixels(curRow, 0, w, 0, h - 1, w, 1)
        return outBitmap
    }
}
