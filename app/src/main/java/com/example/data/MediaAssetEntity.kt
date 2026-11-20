package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.MediaItemType

@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaStoreUri: String,
    val mediaType: String, // MediaItemType.name
    val displayName: String,
    val width: Int,
    val height: Int,
    val fps: Int = 0,
    val durationMs: Long = 0L,
    val bitrateBps: Int = 0,
    val fileSizeBytes: Long = 0L,
    val captureDateMs: Long = System.currentTimeMillis(),
    // Live Photo paired metadata
    val livePairId: String? = null,
    val liveVideoUri: String? = null,
    val liveDurationSec: Int = 0,
    val liveHasAudio: Boolean = false,
    val shutterOffsetMs: Long = 0L,
    // Gallery organization
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAtMs: Long? = null,
    // AI / Editor lineage
    val originalAssetId: Long? = null,
    val aiEnhancementTag: String? = null,
    // EXIF & telemetry
    val lensLabel: String = "",
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val focalLengthMm: Float? = null,
    val gyroTrackPath: String? = null
) {
    val typeEnum: MediaItemType
        get() = runCatching { MediaItemType.valueOf(mediaType) }.getOrDefault(MediaItemType.PHOTO)
}
