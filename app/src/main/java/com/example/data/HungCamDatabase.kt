package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MediaAssetEntity::class], version = 1, exportSchema = false)
abstract class HungCamDatabase : RoomDatabase() {
    abstract fun mediaAssetDao(): MediaAssetDao

    companion object {
        @Volatile
        private var instance: HungCamDatabase? = null

        fun getInstance(context: Context): HungCamDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HungCamDatabase::class.java,
                    "hung_cam_media.db"
                ).build().also { instance = it }
            }
        }
    }
}
