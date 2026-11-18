package com.example.smartfridge.ml

import android.graphics.Bitmap
import com.example.smartfridge.data.ProductCatalogEntity
import com.example.smartfridge.data.ProductDao
import com.paddle.ocr.PaddleOCR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class RecognizedProduct(
    val name: String,
    val category: String,
    val icon: String,
    val brand: String?,
    val quantity: Float,
    val expiresAt: Long,
    val isExactDate: Boolean
)

class FridgeVisionPipeline(
    private val yoloDetector: YOLODetector,
    private val ocr: PaddleOCR,
    private val productDao: ProductDao
) {

    suspend fun analyze(
        bitmap: Bitmap
    ): List<RecognizedProduct> =
        withContext(Dispatchers.Default) {

            val detections =
                yoloDetector.detect(bitmap)

            if (detections.isEmpty()) {
                return@withContext emptyList()
            }

            val results =
                mutableListOf<RecognizedProduct>()

            for (detection in detections) {

                val catalogProduct =
                    findCatalogProduct(
                        detection.label
                    )

                val crop =
                    cropDetection(
                        bitmap,
                        detection
                    )

                val ocrText =
                    try {

                        val ocrResult =
                            ocr.recognize(crop)

                        ocrResult.results
                            .joinToString(" ") {
                                it.text
                            }

                    } catch (_: Exception) {

                        ""
                    }

                crop.recycle()

                val expiry =
                    ExpiryDateParser
                        .findExpiryDate(
                            ocrText
                        )

                val product =
                    catalogProduct
                        ?: createFallbackProduct(
                            detection.label
                        )

                val finalExpiry =
                    expiry
                        ?: calculateEstimatedExpiry(
                            product.defaultShelfLifeDays
                        )

                results.add(
                    RecognizedProduct(

                        name =
                            product.name,

                        category =
                            product.category,

                        icon =
                            product.icon,

                        brand =
                            extractBrand(
                                ocrText
                            ),

                        quantity = 1f,

                        expiresAt =
                            finalExpiry,

                        isExactDate =
                            expiry != null
                    )
                )
            }

            results
        }

    private suspend fun findCatalogProduct(
        yoloLabel: String
    ): ProductCatalogEntity? {

        val normalized =
            normalize(yoloLabel)

        val exact =
            productDao.findByName(
                yoloLabel
            )

        if (exact != null) {
            return exact
        }

        val products =
            productDao.search(
                yoloLabel
            )

        if (products.isNotEmpty()) {
            return products.first()
        }

        return products.firstOrNull {
            normalize(it.name)
                .contains(normalized)
        }
    }

    private fun createFallbackProduct(
        label: String
    ): ProductCatalogEntity {

        return ProductCatalogEntity(

            id = 0,

            name = label,

            category = "Другое",

            defaultShelfLifeDays = 7,

            icon = "📦",

            isCustom = false
        )
    }

    private fun calculateEstimatedExpiry(
        days: Int
    ): Long {

        return System.currentTimeMillis() +
                days * 24L * 60L * 60L * 1000L
    }

    private fun extractBrand(
        text: String
    ): String? {

        val clean =
            text.trim()

        if (clean.isEmpty()) {
            return null
        }

        val lines =
            clean
                .split(
                    Regex("[\\n\\r]+")
                )
                .map {
                    it.trim()
                }
                .filter {
                    it.length >= 3
                }

        return lines
            .firstOrNull {
                !it.matches(
                    Regex(
                        """.*\d{1,2}[./-]\d{1,2}.*"""
                    )
                )
            }
    }

    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase(Locale.getDefault())
            .replace(
                Regex("[^а-яa-z0-9 ]"),
                ""
            )
            .trim()
    }

    private fun cropDetection(
        bitmap: Bitmap,
        detection: YoloDetection
    ): Bitmap {

        val left =
            detection.left
                .toInt()
                .coerceIn(
                    0,
                    bitmap.width - 1
                )

        val top =
            detection.top
                .toInt()
                .coerceIn(
                    0,
                    bitmap.height - 1
                )

        val right =
            detection.right
                .toInt()
                .coerceIn(
                    left + 1,
                    bitmap.width
                )

        val bottom =
            detection.bottom
                .toInt()
                .coerceIn(
                    top + 1,
                    bitmap.height
                )

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )
    }

    fun close() {

        yoloDetector.close()
    }
}

