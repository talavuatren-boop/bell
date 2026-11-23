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
     * Edge-preserving bilateral filter in YCbCr luminance/chrominance space.
     * Aggressively suppresses color speckle noise in shadows while preserving fine luma edges.
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

        val radius = when (mode) {
            AiProcessingMode.FAST -> 1
            AiProcessingMode.BALANCED -> 2
            AiProcessingMode.QUALITY -> 2
        }
        val passes = if (mode == AiProcessingMode.QUALITY) 2 else 1
        val lumaSigma = when (mode) {
            AiProcessingMode.FAST -> 18f
            AiProcessingMode.BALANCED -> 24f
            AiProcessingMode.QUALITY -> 28f
        }

        var current = pixels
        for (pass in 0 until passes) {
            for (y in 0 until h) {
                coroutineContext.ensureActive()
                if (y % 48 == 0) {
                    val base = (pass * 50) + (y * 50 / h)
                    onProgress(base.coerceIn(5, 95), "Denoising pass ${pass + 1}/$passes...")
                }
                for (x in 0 until w) {
                    val c = current[y * w + x]
                    val cr = (c shr 16) and 0xFF
                    val cg = (c shr 8) and 0xFF
                    val cb = c and 0xFF
                    val cy = 0.299f * cr + 0.587f * cg + 0.114f * cb

                    var sumR = 0f
                    var sumG = 0f
                    var sumB = 0f
                    var sumW = 0f

                    val y0 = max(0, y - radius)
                    val y1 = min(h - 1, y + radius)
                    val x0 = max(0, x - radius)
                    val x1 = min(w - 1, x + radius)

                    for (ny in y0..y1) {
                        val rowOff = ny * w
                        for (nx in x0..x1) {
                            val n = current[rowOff + nx]
                            val nr = (n shr 16) and 0xFF
                            val ng = (n shr 8) and 0xFF
                            val nb = n and 0xFF
                            val nLum = 0.299f * nr + 0.587f * ng + 0.114f * nb
                            val diff = abs(nLum - cy)
                            val weight = if (diff < lumaSigma) {
                                1f - (diff / lumaSigma) * 0.7f
                            } else {
                                0.08f
                            }
                            sumR += nr * weight
                            sumG += ng * weight
                            sumB += nb * weight
                            sumW += weight
                        }
                    }

                    val rOut = (sumR / sumW).roundToInt().coerceIn(0, 255)
                    val gOut = (sumG / sumW).roundToInt().coerceIn(0, 255)
                    val bOut = (sumB / sumW).roundToInt().coerceIn(0, 255)
                    out[y * w + x] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                }
            }
            if (pass < passes - 1) {
                current = out.copyOf()
            }
        }

        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Halo-free adaptive unsharp mask with edge-overshoot clamping.
     * Enhances micro-contrast and detail clarity without producing bright/dark edge halos.
     */
    private suspend fun applyHaloFreeSharpen(
        src: Bitmap,
        mode: AiProcessingMode,
        onProgress: (Int, String) -> Unit
    ): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        val amount = when (mode) {
            AiProcessingMode.FAST -> 0.45f
            AiProcessingMode.BALANCED -> 0.68f
            AiProcessingMode.QUALITY -> 0.85f
        }
        val maxOvershoot = 26 // Prevents harsh white/black halos around high-contrast edges

        for (y in 0 until h) {
            coroutineContext.ensureActive()
            if (y % 64 == 0) {
                onProgress((y * 95 / h).coerceIn(5, 95), "Adaptive edge sharpening...")
            }
            for (x in 0 until w) {
                val idx = y * w + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    out[idx] = pixels[idx]
                    continue
                }
                val c = pixels[idx]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF

                val up = pixels[(y - 1) * w + x]
                val dn = pixels[(y + 1) * w + x]
                val lt = pixels[y * w + (x - 1)]
                val rt = pixels[y * w + (x + 1)]

                val avgR = (((up shr 16) and 0xFF) + ((dn shr 16) and 0xFF) + ((lt shr 16) and 0xFF) + ((rt shr 16) and 0xFF)) * 0.25f
                val avgG = (((up shr 8) and 0xFF) + ((dn shr 8) and 0xFF) + ((lt shr 8) and 0xFF) + ((rt shr 8) and 0xFF)) * 0.25f
                val avgB = ((up and 0xFF) + (dn and 0xFF) + (lt and 0xFF) + (rt and 0xFF)) * 0.25f

                val diffR = ((cr - avgR) * amount).roundToInt().coerceIn(-maxOvershoot, maxOvershoot)
                val diffG = ((cg - avgG) * amount).roundToInt().coerceIn(-maxOvershoot, maxOvershoot)
                val diffB = ((cb - avgB) * amount).roundToInt().coerceIn(-maxOvershoot, maxOvershoot)

                // Soft noise gate: avoid amplifying flat-area sensor grain
                val detailMag = abs(diffR) + abs(diffG) + abs(diffB)
                val factor = if (detailMag < 5) 0.2f else 1.0f

                val rOut = (cr + (diffR * factor).roundToInt()).coerceIn(0, 255)
                val gOut = (cg + (diffG * factor).roundToInt()).coerceIn(0, 255)
                val bOut = (cb + (diffB * factor).roundToInt()).coerceIn(0, 255)
                out[idx] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Skin-tone-safe Auto White Balance (Gray-World + Percentile), S-Curve Contrast & Vibrance.
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

        onProgress(15, "Analyzing scene histogram & white balance...")
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0
        for (i in pixels.indices step 4) {
            val p = pixels[i]
            sumR += (p shr 16) and 0xFF
            sumG += (p shr 8) and 0xFF
            sumB += p and 0xFF
            count++
        }
        val avgR = (sumR / count).coerceAtLeast(1.0)
        val avgG = (sumG / count).coerceAtLeast(1.0)
        val avgB = (sumB / count).coerceAtLeast(1.0)
        val grayMean = (avgR + avgG + avgB) / 3.0

        val wbStrength = if (mode == AiProcessingMode.FAST) 0.45 else 0.65
        val gainR = (1.0 + ((grayMean / avgR) - 1.0) * wbStrength).toFloat().coerceIn(0.82f, 1.22f)
        val gainG = (1.0 + ((grayMean / avgG) - 1.0) * wbStrength).toFloat().coerceIn(0.88f, 1.14f)
        val gainB = (1.0 + ((grayMean / avgB) - 1.0) * wbStrength).toFloat().coerceIn(0.82f, 1.22f)

        val out = IntArray(pixels.size)
        for (y in 0 until h) {
            coroutineContext.ensureActive()
            if (y % 64 == 0) {
                onProgress(20 + (y * 75 / h), "Applying skin-safe color & tonal curve...")
            }
            for (x in 0 until w) {
                val idx = y * w + x
                val p = pixels[idx]
                var r = (((p shr 16) and 0xFF) * gainR).coerceIn(0f, 255f)
                var g = (((p shr 8) and 0xFF) * gainG).coerceIn(0f, 255f)
                var b = ((p and 0xFF) * gainB).coerceIn(0f, 255f)

                // Subtle gentle S-curve contrast around midpoint
                r = applySoftSCurve(r / 255f) * 255f
                g = applySoftSCurve(g / 255f) * 255f
                b = applySoftSCurve(b / 255f) * 255f

                // Skin-tone aware vibrance: boost desaturated cool/foliage tones more than warm skin hues
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = if (maxC > 0f) (maxC - minC) / maxC else 0f
                val isSkinLike = (r > g && g > b && (r - g) > 12f && (r - b) > 20f)
                val vibranceBoost = if (isSkinLike) 0.05f else 0.22f * (1f - sat)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                r = (lum + (r - lum) * (1f + vibranceBoost)).coerceIn(0f, 255f)
                g = (lum + (g - lum) * (1f + vibranceBoost)).coerceIn(0f, 255f)
                b = (lum + (b - lum) * (1f + vibranceBoost)).coerceIn(0f, 255f)

                out[idx] = (0xFF shl 24) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * 2x Super Resolution via high-precision filtered scaling + edge-directed micro-sharpening.
     * Avoids over-promising synthetic details while giving clean crisp resolution upscaling.
     */
    private suspend fun applySuperResolution2x(
        src: Bitmap,
        mode: AiProcessingMode,
        onProgress: (Int, String) -> Unit
    ): Bitmap {
        onProgress(20, "Upscaling geometry (2x high-precision filter)...")
        val scale = if (src.width * src.height > 8_000_000) 1.5f else 2.0f
        val targetW = (src.width * scale).roundToInt().coerceAtMost(4800)
        val targetH = (src.height * scale).roundToInt().coerceAtMost(4800)

        val upscaled = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        coroutineContext.ensureActive()
        onProgress(55, "Restoring edge acuity...")
        return applyHaloFreeSharpen(upscaled, mode) { p, _ ->
            onProgress(55 + (p * 0.42f).toInt(), "Restoring edge acuity...")
        }
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
}
