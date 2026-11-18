package com.example.smartfridge.ml

import android.graphics.Bitmap
import android.util.Log
import com.example.smartfridge.data.ProductCatalogEntity
import com.example.smartfridge.data.ProductDao
import com.paddle.ocr.PaddleOCR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    companion object {
        private const val TAG = "FridgeVisionPipeline"
        private const val CROP_PADDING_RATIO = 0.08f
    }

    suspend fun analyze(bitmap: Bitmap): List<RecognizedProduct> =
        withContext(Dispatchers.Default) {
            val detections = yoloDetector.detect(bitmap)
            Log.d(TAG, "Frame ${bitmap.width}x${bitmap.height}: ${detections.size} YOLO detections")

            if (detections.isEmpty()) {
                return@withContext emptyList()
            }

            val results = mutableListOf<RecognizedProduct>()
            for (detection in detections) {
                // YOLO predicts a broad category, not necessarily an exact catalog product.
                // Only accept an exact catalog-name match; otherwise preserve the detected class.
                val crop = cropDetection(bitmap, detection)
                val ocrText = try {
                    val ocrResult = ocr.recognize(crop)
                    Log.d(
                        TAG,
                        "OCR ${detection.label} conf=${detection.confidence}: " +
                            "lines=${ocrResult.lineCount}, crop=${crop.width}x${crop.height}, " +
                            "text=${ocrResult.results.joinToString(" | ") { it.text }}"
                    )
                    ocrResult.results.joinToString("\n") { it.text }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "OCR failed for ${detection.label}, crop=${crop.width}x${crop.height}",
                        e
                    )
                    ""
                } finally {
                    crop.recycle()
                }

                val expiry = ExpiryDateParser.findExpiryDate(ocrText)
                val product = findCatalogProduct(detection.label)
                    ?: createFallbackProduct(detection.label)
                val finalExpiry = expiry
                    ?: calculateEstimatedExpiry(product.defaultShelfLifeDays)

                results.add(
                    RecognizedProduct(
                        name = product.name,
                        category = product.category,
                        icon = product.icon,
                        brand = extractBrand(ocrText),
                        quantity = 1f,
                        expiresAt = finalExpiry,
                        isExactDate = expiry != null
                    )
                )
            }
            results
        }

    private suspend fun findCatalogProduct(
        yoloLabel: String
    ): ProductCatalogEntity? {
        // A detector class such as "Молоко" is a category. A LIKE search here used
        // to select the first alphabetic catalog item in that category as if it
        // were the exact product, which produced misleading product names.
        return productDao.findByName(yoloLabel)
    }

    private fun createFallbackProduct(label: String): ProductCatalogEntity =
        ProductCatalogEntity(
            id = 0,
            name = label,
            category = label,
            defaultShelfLifeDays = 7,
            icon = "📦",
            isCustom = false
        )

    private fun calculateEstimatedExpiry(days: Int): Long =
        System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L

    private fun extractBrand(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null

        val lines = clean
            .split(Regex("[\\n\\r]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }

        return lines.firstOrNull {
            !it.matches(Regex(""".*\d{1,2}[./-]\d{1,2}.*"""))
        }
    }

    private fun cropDetection(
        bitmap: Bitmap,
        detection: YoloDetection
    ): Bitmap {
        val boxWidth = (detection.right - detection.left).coerceAtLeast(1f)
        val boxHeight = (detection.bottom - detection.top).coerceAtLeast(1f)
        val padX = boxWidth * CROP_PADDING_RATIO
        val padY = boxHeight * CROP_PADDING_RATIO

        val left = (detection.left - padX).toInt().coerceIn(0, bitmap.width - 1)
        val top = (detection.top - padY).toInt().coerceIn(0, bitmap.height - 1)
        val right = (detection.right + padX).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (detection.bottom + padY).toInt().coerceIn(top + 1, bitmap.height)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    fun close() {
        yoloDetector.close()
    }
}
