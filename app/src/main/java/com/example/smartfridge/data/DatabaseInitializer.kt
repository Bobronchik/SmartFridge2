package com.example.smartfridge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DatabaseInitializer(
    private val database: AppDatabase
) {
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val dao = database.productDao()
        val defaults = DatabaseSeeder.products()

        // Remove old seed/test suggestions while preserving user-created catalog items.
        dao.deleteNonStandardDefaults(defaults.map { it.name })

        val existingNames = dao.observeAll().first()
            .map { it.name.trim().lowercase() }
            .toSet()
        val missingDefaults = defaults.filterNot { it.name.trim().lowercase() in existingNames }
        if (missingDefaults.isNotEmpty()) {
            dao.insertAll(missingDefaults)
        }
    }
}
