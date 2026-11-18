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

    suspend fun saveRecognizedProducts(products: List<RecognizedProduct>) {
        if (products.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = products.map { product ->
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

    suspend fun deleteProduct(product: FridgeProduct) {
        val entity = fridgeDao.getById(product.id) ?: return
        fridgeDao.delete(entity)
    }

    suspend fun close() = pipeline.release()
}
