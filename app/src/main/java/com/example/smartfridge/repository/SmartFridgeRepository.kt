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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class SmartFridgeRepository(
    private val fridgeDao: FridgeDao,
    private val productDao: ProductDao,
    private val pipeline: FridgeVisionPipeline,
    private val cameraRepository: CameraRepository
) {

    val productCatalog: Flow<List<ProductCatalogEntity>> =
        productDao.observeAll()

    val products: Flow<List<FridgeProduct>> =
        fridgeDao
            .observeAll()
            .map { items ->
                items.map {
                    it.toDomain()
                }
            }

    suspend fun capture(
        address: String
    ): Bitmap? {

        return cameraRepository
            .capture(address)
            .bitmap
    }

    suspend fun analyze(
        bitmap: Bitmap
    ) {

        val recognized =
            pipeline.analyze(bitmap)

        val now =
            System.currentTimeMillis()

        val entities =
            recognized.map { product ->

                FridgeItemEntity(

                    productId =
                        productDao
                            .findByName(
                                product.name
                            )
                            ?.id,

                    name =
                        product.name,

                    category =
                        product.category,

                    icon =
                        product.icon,

                    quantity =
                        product.quantity,

                    addedAt =
                        now,

                    expiresAt =
                        product.expiresAt,

                    isExactDate =
                        product.isExactDate,

                    brand =
                        product.brand
                )
            }

        if (entities.isNotEmpty()) {

            fridgeDao.insertAll(
                entities
            )
        }
    }

    suspend fun addProduct(
        name: String,
        category: String,
        icon: String,
        quantity: Float,
        expiresAt: Long,
        isExactDate: Boolean
    ) {

        val existing =
            productDao.findByName(name)

        val productId =
            existing?.id
                ?: productDao.insert(
                    ProductCatalogEntity(
                        name = name,
                        category = category,
                        defaultShelfLifeDays = 7,
                        icon = icon,
                        isCustom = true
                    )
                )

        fridgeDao.insert(
            FridgeItemEntity(

                productId =
                    productId,

                name =
                    name,

                category =
                    category,

                icon =
                    icon,

                quantity =
                    quantity,

                addedAt =
                    System.currentTimeMillis(),

                expiresAt =
                    expiresAt,

                isExactDate =
                    isExactDate
            )
        )
    }

    suspend fun searchProducts(
        query: String
    ): List<ProductCatalogEntity> {

        return productDao.search(query)
    }

    suspend fun deleteProduct(
        product: FridgeProduct
    ) {
        val entity = fridgeDao.getById(product.id) ?: return
        fridgeDao.delete(entity)
    }

    fun estimatedDate(
        days: Int
    ): Long {

        val calendar =
            Calendar.getInstance()

        calendar.add(
            Calendar.DAY_OF_YEAR,
            days
        )

        return calendar.timeInMillis
    }
}