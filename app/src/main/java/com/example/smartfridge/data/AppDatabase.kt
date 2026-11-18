package com.example.smartfridge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductCatalogEntity::class,
        FridgeItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    abstract fun fridgeDao(): FridgeDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(
            context: Context
        ): AppDatabase {

            return INSTANCE
                ?: synchronized(this) {

                    INSTANCE
                        ?: Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "smartfridge.db"
                        )
                            .fallbackToDestructiveMigration()
                            .build()
                            .also {
                                INSTANCE = it
                            }
                }
        }
    }
}