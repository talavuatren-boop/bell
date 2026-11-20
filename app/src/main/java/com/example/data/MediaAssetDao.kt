package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAssetDao {
    @Query("SELECT * FROM media_assets WHERE isDeleted = 0 ORDER BY captureDateMs DESC")
    fun observeActiveAssets(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE isDeleted = 1 ORDER BY deletedAtMs DESC")
    fun observeDeletedAssets(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MediaAssetEntity?

    @Query("SELECT * FROM media_assets WHERE livePairId = :pairId LIMIT 1")
    suspend fun getByLivePairId(pairId: String): MediaAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: MediaAssetEntity): Long

    @Update
    suspend fun update(asset: MediaAssetEntity)

    @Query("UPDATE media_assets SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE media_assets SET isDeleted = 1, deletedAtMs = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE media_assets SET isDeleted = 0, deletedAtMs = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun hardDeleteById(id: Long)
}
