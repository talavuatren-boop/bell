package com.example.camera

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import coil.ImageLoader
import coil.decode.DataSource
import coil.disk.DiskCache
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.memory.MemoryCache
import coil.request.Options
import coil.size.pxOrElse
import java.io.File

object DevicePerformanceProfile {
    @Volatile
    private var cachedIsWeakDevice: Boolean? = null

    fun isLowEndHardwareSpec(
        isLowRamDevice: Boolean,
        totalRamBytes: Long,
        cpuCores: Int,
        sdkInt: Int
    ): Boolean {
        val totalRamGb = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
        return isLowRamDevice || (totalRamBytes > 0L && totalRamGb <= 4.5) || cpuCores <= 4 || sdkInt < 29
    }

    fun isLowEndHardware(context: Context): Boolean {
        cachedIsWeakDevice?.let { return it }
        val result = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val lowRamFlag = am?.isLowRamDevice == true
            val cores = Runtime.getRuntime().availableProcessors()
            isLowEndHardwareSpec(
                isLowRamDevice = lowRamFlag,
                totalRamBytes = memInfo.totalMem,
                cpuCores = cores,
                sdkInt = Build.VERSION.SDK_INT
            )
        }.getOrDefault(false)
        cachedIsWeakDevice = result
        return result
    }

    fun buildFastImageLoader(context: Context): ImageLoader {
        val appCtx = context.applicationContext
        val isWeak = isLowEndHardware(appCtx)
        return ImageLoader.Builder(appCtx)
            .components {
                add(VideoThumbnailFetcher.Factory(appCtx))
            }
            .allowRgb565(true)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(appCtx)
                    .maxSizePercent(if (isWeak) 0.14 else 0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appCtx.cacheDir, "hungcam_img_cache"))
                    .maxSizeBytes(if (isWeak) 32L * 1024L * 1024L else 64L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}

private class VideoThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val targetW = options.size.width.pxOrElse { 360 }.coerceIn(120, 720)
            val targetH = options.size.height.pxOrElse { 360 }.coerceIn(120, 720)

            val rawBmp: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetW,
                    targetH
                )
            } else {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            val bmp = rawBmp ?: return null
            val configBmp = if (bmp.config != Bitmap.Config.RGB_565) {
                bmp.copy(Bitmap.Config.RGB_565, false)?.also { bmp.recycle() } ?: bmp
            } else {
                bmp
            }
            DrawableResult(
                drawable = BitmapDrawable(context.resources, configBmp),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!isVideoUri(context, data)) return null
            return VideoThumbnailFetcher(context, data, options)
        }

        private fun isVideoUri(ctx: Context, uri: Uri): Boolean {
            val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull().orEmpty()
            if (mime.startsWith("video/")) return true
            val str = uri.toString().lowercase()
            return str.contains("/video/") || str.endsWith(".mp4") || str.endsWith(".mov") || str.endsWith(".mkv")
        }
    }
}
