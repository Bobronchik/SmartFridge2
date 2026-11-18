package com.example.smartfridge.ml

import android.graphics.Bitmap
import android.util.Log
import com.example.smartfridge.data.ProductCatalogEntity
import com.example.smartfridge.data.ProductDao
import com.paddle.ocr.PaddleOCR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.util.Locale

data class RecognizedProduct(
    val name: String,
    val category: String,
    val icon: String,
    val brand: String?,
    val quantity: Float,
    val expiresAt: Long,
    val isExactDate: Boolean,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val confidence: Float = 0f
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

            // Read the catalog once per frame instead of issuing a Room Flow query
            // for every detected object.
            val catalog = productDao.observeAll().first()
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
                val product = findCatalogProduct(detection.label, ocrText, catalog)
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
                        isExactDate = expiry != null,
                        left = detection.left,
                        top = detection.top,
                        right = detection.right,
                        bottom = detection.bottom,
                        confidence = detection.confidence
                    )
                )
            }
            results
        }

    private fun findCatalogProduct(
        yoloLabel: String,
        ocrText: String,
        catalog: List<ProductCatalogEntity>
    ): ProductCatalogEntity? {
        val normalizedText = normalize(ocrText)
        if (normalizedText.length >= 4) {
            val normalizedLabel = normalize(yoloLabel)
            val candidates = catalog.filter { product ->
                    val normalizedName = normalize(product.name)
                    normalizedName.length >= 4 && normalizedText.contains(normalizedName)
                }

            // Prefer the longest OCR name match; use the YOLO class only as a tie-breaker.
            val ocrMatch = candidates.maxWithOrNull(
                compareBy<ProductCatalogEntity>(
                    { if (normalize(it.category) == normalizedLabel) 1 else 0 },
                    { normalize(it.name).length }
                )
            )
            if (ocrMatch != null) return ocrMatch
        }

        // A detector class is a broad category, not a product name. Never use LIKE
        // here: it used to return an arbitrary alphabetically-first catalog product.
        return productDao.findByName(yoloLabel)
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

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

        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        // Android may return the source bitmap when the crop covers the full frame.
        // The caller owns and recycles this crop, never the camera's original bitmap.
        return if (crop === bitmap) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            crop
        }
    }

    suspend fun release() {
        try {
            yoloDetector.close()
        } finally {
            ocr.release()
        }
    }
}
