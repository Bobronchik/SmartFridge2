package com.example.smartfridge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseInitializer(
    private val database: AppDatabase
) {

    suspend fun initialize() {

        withContext(Dispatchers.IO) {

            val dao =
                database.productDao()

            if (dao.count() == 0) {

                dao.insertAll(
                    DatabaseSeeder.products()
                )
            }
        }
    }
}