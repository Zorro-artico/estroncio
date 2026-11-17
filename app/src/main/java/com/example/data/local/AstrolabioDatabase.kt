package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ActiveSegmentMarkEntity
import com.example.data.model.ActiveTripEntity
import com.example.data.model.RouteEntity
import com.example.data.model.SegmentRecordEntity
import com.example.data.model.SegmentTemplateEntity
import com.example.data.model.TripRecordEntity

@Database(
    entities = [
        RouteEntity::class,
        SegmentTemplateEntity::class,
        TripRecordEntity::class,
        SegmentRecordEntity::class,
        ActiveTripEntity::class,
        ActiveSegmentMarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AstrolabioDatabase : RoomDatabase() {
    abstract fun astrolabioDao(): AstrolabioDao

    companion object {
        @Volatile
        private var INSTANCE: AstrolabioDatabase? = null

        fun getInstance(context: Context): AstrolabioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AstrolabioDatabase::class.java,
                    "astrolabio_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
