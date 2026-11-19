package com.example.smartfridge.repository

import android.graphics.Bitmap
import com.example.smartfridge.camera.CameraRepository
import com.example.smartfridge.data.FridgeDao
import com.example.smartfridge.data.FridgeItemEntity
import com.example.smartfridge.data.ProductCatalogEntity
import com.example.smartfridge.data.ProductDao
import com.example.smartfridge.domain.FridgeProduct
import com.example.smartfridge.domain.toDomain
import com.example.smartfridge.ml.FridgeVisionPipeline
import com.example.smartfridge.ml.RecognizedProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SmartFridgeRepository(
    private val fridgeDao: FridgeDao,
    private val productDao: ProductDao,
    private val pipeline: FridgeVisionPipeline,
    private val cameraRepository: CameraRepository
) {
    val productCatalog: Flow<List<ProductCatalogEntity>> = productDao.observeAll()

    val products: Flow<List<FridgeProduct>> = fridgeDao.observeAll().map { items ->
        items.map { it.toDomain() }
    }

    suspend fun capture(address: String): Bitmap? = cameraRepository.capture(address).bitmap

    // Recognition is deliberately read-only. The caller must ask the user to review
    // the result and call saveRecognizedProducts only after explicit confirmation.
    suspend fun analyze(bitmap: Bitmap): List<RecognizedProduct> = pipeline.analyze(bitmap)
    // DEV comparison uses YOLO + OCR only; full-frame Gemini runs separately in parallel.
    suspend fun analyzeLocal(bitmap: Bitmap): List<RecognizedProduct> =
        pipeline.analyze(bitmap, allowGeminiVerification = false)

    suspend fun saveRecognizedProducts(products: List<RecognizedProduct>): Int {
        if (products.isEmpty()) return 0
        val now = System.currentTimeMillis()
        // Repeated camera scans should not add the same catalog item again immediately.
        // Different physical packages detected in one review are still all preserved.
        val recentWindowStart = now - 2 * 60 * 1000L
        val productsToSave = products.filter { product ->
            fridgeDao.findRecentlyAddedByName(product.name.trim(), recentWindowStart) == null
        }
        if (productsToSave.isEmpty()) return 0
        val entities = productsToSave.map { product ->
            val catalogItem = productDao.findByName(product.name)
            FridgeItemEntity(
                productId = catalogItem?.id,
                name = product.name.trim(),
                category = product.category,
                icon = product.icon,
                quantity = product.quantity.coerceAtLeast(0.01f),
                addedAt = now,
                expiresAt = product.expiresAt,
                isExactDate = product.isExactDate,
                brand = product.brand
            )
        }
        fridgeDao.insertAll(entities)
        return entities.size
    }

    suspend fun addProduct(
        name: String,
        category: String,
        icon: String,
        quantity: Float,
        expiresAt: Long,
        isExactDate: Boolean
    ) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Название продукта не может быть пустым" }
        require(quantity.isFinite() && quantity > 0f) { "Количество должно быть больше нуля" }
        require(expiresAt > 0L) { "Укажите корректный срок годности" }

        val existing = productDao.findByName(cleanName)
        val productId = existing?.id ?: productDao.insert(
            ProductCatalogEntity(
                name = cleanName,
                category = category.trim().ifBlank { "Другое" },
                defaultShelfLifeDays = 7,
                icon = icon.ifBlank { "📦" },
                isCustom = true
            )
        )

        fridgeDao.insert(
            FridgeItemEntity(
                productId = productId,
                name = cleanName,
                category = category.trim().ifBlank { "Другое" },
                icon = icon.ifBlank { "📦" },
                quantity = quantity,
                addedAt = System.currentTimeMillis(),
                expiresAt = expiresAt,
                isExactDate = isExactDate
            )
        )
    }

    suspend fun searchProducts(query: String): List<ProductCatalogEntity> =
        productDao.search(query.trim())

    suspend fun updateProduct(product: FridgeProduct) {
        val entity = fridgeDao.getById(product.id) ?: return
        require(product.name.isNotBlank()) { "Название продукта не может быть пустым" }
        require(product.quantity.isFinite() && product.quantity > 0f) { "Количество должно быть больше нуля" }
        require(product.expiresAt > 0L) { "Укажите корректный срок годности" }
        fridgeDao.update(
            entity.copy(
                name = product.name.trim(),
                category = product.category.trim().ifBlank { "Другое" },
                icon = product.icon.ifBlank { "📦" },
                quantity = product.quantity,
                expiresAt = product.expiresAt,
                isExactDate = product.isExactDate,
                brand = product.brand
            )
        )
    }

    suspend fun deleteProduct(product: FridgeProduct) {
        val entity = fridgeDao.getById(product.id) ?: return
        fridgeDao.delete(entity)
    }

    suspend fun close() = pipeline.release()
}
