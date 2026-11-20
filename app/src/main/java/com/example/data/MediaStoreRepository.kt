package com.example.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.MediaItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MediaStoreRepository(
    private val context: Context,
    private val dao: MediaAssetDao
) {
    private val resolver: ContentResolver = context.contentResolver

    val activeAssets: Flow<List<MediaAssetEntity>> = dao.observeActiveAssets()
    val deletedAssets: Flow<List<MediaAssetEntity>> = dao.observeDeletedAssets()

    fun getAvailableStorageMb(): Long {
        return runCatching {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = StatFs(path.absolutePath)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L)
        }.getOrDefault(0L)
    }

    fun hasSufficientStorage(minMb: Long = 100L): Boolean = getAvailableStorageMb() >= minMb

    suspend fun saveCapturedPhotoBytes(
        bytes: ByteArray,
        mimeType: String,
        extension: String,
        width: Int,
        height: Int,
        lensLabel: String,
        iso: Int?,
        exposureTimeNs: Long?,
        focalLengthMm: Float?,
        isRawDng: Boolean = false,
        livePairId: String? = null,
        liveVideoUri: String? = null,
        liveDurationSec: Int = 0,
        liveHasAudio: Boolean = false,
        shutterOffsetMs: Long = 0L,
        aiTag: String? = null,
        originalAssetId: Long? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<MediaAssetEntity> = withContext(Dispatchers.IO) {
        if (!hasSufficientStorage(25L)) {
            return@withContext Result.failure(IllegalStateException("Insufficient storage space (< 25 MB free)"))
        }
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val prefix = if (aiTag != null) "HUNG_AI_" else if (livePairId != null) "HUNG_LIVE_" else "HUNG_IMG_"
            val displayName = "$prefix$timestamp.$extension"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        if (aiTag != null) "${Environment.DIRECTORY_PICTURES}/HUNG CAM/Enhanced"
                        else "${Environment.DIRECTORY_PICTURES}/HUNG CAM"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create MediaStore image entry")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw IllegalStateException("Failed to open output stream for $uri")

                // Preserve / write EXIF metadata for JPEG/HEIC
                if (!isRawDng && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    runCatching {
                        resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                            val exif = ExifInterface(pfd.fileDescriptor)
                            if (iso != null) exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, iso.toString())
                            if (exposureTimeNs != null) {
                                exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, (exposureTimeNs / 1_000_000_000.0).toString())
                            }
                            if (focalLengthMm != null) {
                                val num = (focalLengthMm * 100).toInt()
                                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "$num/100")
                            }
                            if (livePairId != null) {
                                exif.setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, livePairId)
                            }
                            if (latitude != null && longitude != null) {
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, decimalToDms(latitude))
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (latitude >= 0) "N" else "S")
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, decimalToDms(longitude))
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (longitude >= 0) "E" else "W")
                            }
                            exif.saveAttributes()
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publish = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, publish, null, null)
                }
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }

            val mediaType = when {
                isRawDng -> MediaItemType.RAW_DNG
                livePairId != null && liveVideoUri != null -> MediaItemType.LIVE_PHOTO
                else -> MediaItemType.PHOTO
            }

            val entity = MediaAssetEntity(
                mediaStoreUri = uri.toString(),
                mediaType = mediaType.name,
                displayName = displayName,
                width = width,
                height = height,
                fileSizeBytes = bytes.size.toLong(),
                captureDateMs = System.currentTimeMillis(),
                livePairId = livePairId,
                liveVideoUri = liveVideoUri,
                liveDurationSec = liveDurationSec,
                liveHasAudio = liveHasAudio,
                shutterOffsetMs = shutterOffsetMs,
                originalAssetId = originalAssetId,
                aiEnhancementTag = aiTag,
                lensLabel = lensLabel,
                iso = iso,
                exposureTimeNs = exposureTimeNs,
                focalLengthMm = focalLengthMm
            )
            val id = dao.insert(entity)
            entity.copy(id = id)
        }
    }

    suspend fun saveBitmapAsImage(
        bitmap: Bitmap,
        aiTag: String? = null,
        originalAssetId: Long? = null,
        quality: Int = 95
    ): Result<MediaAssetEntity> = withContext(Dispatchers.IO) {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        saveCapturedPhotoBytes(
            bytes = stream.toByteArray(),
            mimeType = "image/jpeg",
            extension = "jpg",
            width = bitmap.width,
            height = bitmap.height,
            lensLabel = "HUNG CAM Editor",
            iso = null,
            exposureTimeNs = null,
            focalLengthMm = null,
            aiTag = aiTag ?: "Edited",
            originalAssetId = originalAssetId
        )
    }

    suspend fun saveVideoFileToMediaStore(
        sourceFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrateBps: Int,
        lensLabel: String,
        isLiveCompanion: Boolean = false,
        livePairId: String? = null,
        aiTag: String? = null,
        originalAssetId: Long? = null,
        gyroTrackPath: String? = null
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (!hasSufficientStorage(50L)) {
            return@withContext Result.failure(IllegalStateException("Insufficient storage space (< 50 MB free)"))
        }
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val prefix = when {
                isLiveCompanion -> "HUNG_LIVE_CLIP_"
                aiTag != null -> "HUNG_AI_VID_"
                else -> "HUNG_VID_"
            }
            val displayName = "$prefix$timestamp.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val subDir = when {
                        isLiveCompanion -> "${Environment.DIRECTORY_MOVIES}/HUNG CAM/LiveClips"
                        aiTag != null -> "${Environment.DIRECTORY_MOVIES}/HUNG CAM/Enhanced"
                        else -> "${Environment.DIRECTORY_MOVIES}/HUNG CAM"
                    }
                    put(MediaStore.Video.Media.RELATIVE_PATH, subDir)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create MediaStore video entry")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                    out.flush()
                } ?: throw IllegalStateException("Failed to write video output stream")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                }
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }

            if (!isLiveCompanion) {
                val meta = extractVideoMetadata(uri)
                val entity = MediaAssetEntity(
                    mediaStoreUri = uri.toString(),
                    mediaType = MediaItemType.VIDEO.name,
                    displayName = displayName,
                    width = meta.first.takeIf { it > 0 } ?: width,
                    height = meta.second.takeIf { it > 0 } ?: height,
                    fps = fps,
                    durationMs = meta.third,
                    bitrateBps = bitrateBps,
                    fileSizeBytes = sourceFile.length(),
                    captureDateMs = System.currentTimeMillis(),
                    livePairId = livePairId,
                    originalAssetId = originalAssetId,
                    aiEnhancementTag = aiTag,
                    lensLabel = lensLabel,
                    gyroTrackPath = gyroTrackPath
                )
                dao.insert(entity)
            }
            uri
        }
    }

    suspend fun importExternalMediaUri(uri: Uri): Result<MediaAssetEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = resolver.getType(uri).orEmpty()
            val isVideo = mime.startsWith("video/")
            var name = "Imported_${UUID.randomUUID().toString().take(6)}"
            var sizeBytes = 0L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) sizeBytes = c.getLong(sizeIdx)
                }
            }

            val (w, h, dur) = if (isVideo) {
                extractVideoMetadata(uri)
            } else {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                Triple(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0), 0L)
            }

            val entity = MediaAssetEntity(
                mediaStoreUri = uri.toString(),
                mediaType = if (isVideo) MediaItemType.VIDEO.name else MediaItemType.PHOTO.name,
                displayName = name,
                width = w,
                height = h,
                durationMs = dur,
                fileSizeBytes = sizeBytes,
                captureDateMs = System.currentTimeMillis(),
                lensLabel = "Imported"
            )
            val id = dao.insert(entity)
            entity.copy(id = id)
        }
    }

    suspend fun toggleFavorite(asset: MediaAssetEntity) {
        dao.setFavorite(asset.id, !asset.isFavorite)
    }

    suspend fun moveToTrash(asset: MediaAssetEntity) {
        dao.softDelete(asset.id, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(asset: MediaAssetEntity) {
        dao.restore(asset.id)
    }

    suspend fun permanentlyDelete(asset: MediaAssetEntity) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(Uri.parse(asset.mediaStoreUri), null, null)
        }
        if (!asset.liveVideoUri.isNullOrBlank()) {
            runCatching {
                resolver.delete(Uri.parse(asset.liveVideoUri), null, null)
            }
        }
        if (!asset.gyroTrackPath.isNullOrBlank()) {
            runCatching { File(asset.gyroTrackPath).delete() }
        }
        dao.hardDeleteById(asset.id)
    }

    fun buildShareIntent(asset: MediaAssetEntity): Intent {
        val uri = Uri.parse(asset.mediaStoreUri)
        val mime = when (asset.typeEnum) {
            MediaItemType.VIDEO -> "video/mp4"
            MediaItemType.RAW_DNG -> "image/x-adobe-dng"
            else -> "image/jpeg"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun extractVideoMetadata(uri: Uri): Triple<Int, Int, Long> {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            Triple(w, h, d)
        }.getOrDefault(Triple(0, 0, 0L))
    }

    private fun decimalToDms(coord: Double): String {
        val absVal = kotlin.math.abs(coord)
        val deg = absVal.toInt()
        val minFull = (absVal - deg) * 60.0
        val min = minFull.toInt()
        val sec = ((minFull - min) * 60.0 * 1000.0).toInt()
        return "$deg/1,$min/1,$sec/1000"
    }
}
