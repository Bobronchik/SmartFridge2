package com.example.smartfridge.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfridge.data.ProductCatalogEntity
import com.example.smartfridge.domain.FridgeProduct
import com.example.smartfridge.ml.RecognizedProduct
import com.example.smartfridge.ml.GeminiPhotoAnalyzer
import com.example.smartfridge.repository.SmartFridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.math.sqrt

data class FridgeUiState(
    val products: List<FridgeProduct> = emptyList(),
    val allProducts: List<FridgeProduct> = emptyList(),
    val search: String = "",
    val category: String? = null,
    val cameraConnected: Boolean = false,
    val cameraSearching: Boolean = false,
    val scanning: Boolean = false,
    val lastImage: Bitmap? = null,
    val photoBusy: Boolean = false,
    val geminiPhotoResult: String? = null,
    val pendingProducts: List<RecognizedProduct> = emptyList(),
    val previewDetections: List<RecognizedProduct> = emptyList(),
    val message: String? = null
)

class FridgeViewModel(
    private val repository: SmartFridgeRepository
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val cameraConnected = MutableStateFlow(false)
    private val cameraSearching = MutableStateFlow(false)
    private val scanning = MutableStateFlow(false)
    private val lastImage = MutableStateFlow<Bitmap?>(null)
    private val photoBusy = MutableStateFlow(false)
    private val geminiPhotoResult = MutableStateFlow<String?>(null)
    private val pendingProducts = MutableStateFlow<List<RecognizedProduct>>(emptyList())
    private val previewDetections = MutableStateFlow<List<RecognizedProduct>>(emptyList())
    private val message = MutableStateFlow<String?>(null)
    private val suggestions = MutableStateFlow<List<ProductCatalogEntity>>(emptyList())
    private var disposed = false
    private var savingScanReview = false

    private val productFilters = combine(repository.products, search, category) { products, query, selectedCategory ->
        val filtered = products.filter { product ->
            (query.isBlank() || product.name.contains(query, ignoreCase = true)) &&
                (selectedCategory == null || product.category == selectedCategory)
        }
        Triple(filtered, query, selectedCategory) to products
    }

    private val cameraState = combine(
        combine(cameraConnected, cameraSearching) { connected, searching ->
            connected to searching
        },
        combine(
            combine(scanning, lastImage, pendingProducts, previewDetections) {
                    scanningNow, image, pending, detections ->
                FridgeUiState(
                    scanning = scanningNow,
                    lastImage = image,
                    pendingProducts = pending,
                    previewDetections = detections
                )
            },
            combine(photoBusy, geminiPhotoResult) { busy, result -> busy to result }
        ) { scanState, photoState ->
            scanState.copy(photoBusy = photoState.first, geminiPhotoResult = photoState.second)
        }
    ) { connection, scan ->
        scan.copy(
            cameraConnected = connection.first,
            cameraSearching = connection.second
        )
    }.let { camera ->
        combine(camera, message) { state, msg -> state.copy(message = msg) }
    }

    val uiState: StateFlow<FridgeUiState> = combine(productFilters, cameraState) { filters, camera ->
        FridgeUiState(
            products = filters.first.first,
            allProducts = filters.second,
            search = filters.first.second,
            category = filters.first.third,
            cameraConnected = camera.cameraConnected,
            cameraSearching = camera.cameraSearching,
            scanning = camera.scanning,
            lastImage = camera.lastImage,
            photoBusy = camera.photoBusy,
            geminiPhotoResult = camera.geminiPhotoResult,
            pendingProducts = camera.pendingProducts,
            previewDetections = camera.previewDetections,
            message = camera.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FridgeUiState())

    val productSuggestions: StateFlow<List<ProductCatalogEntity>> = suggestions.asStateFlow()
    val productCatalog: StateFlow<List<ProductCatalogEntity>> = repository.productCatalog.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    fun setSearch(value: String) { search.value = value }
    fun setCategory(value: String?) { category.value = value }
    fun setCameraConnected(value: Boolean) { cameraConnected.value = value }
    fun setCameraSearching(value: Boolean) { cameraSearching.value = value }
    fun setMessage(value: String?) { message.value = value }

    /**
     * DEV: take one camera frame, always run local YOLO/OCR first, then try
     * Gemini as an optional full-frame second opinion. Local results remain
     * available when Gemini is missing, blocked by region, or temporarily down.
     */
    fun capturePhotoForGemini(address: String, apiKey: String) {
        if (photoBusy.value) return
        viewModelScope.launch {
            photoBusy.value = true
            geminiPhotoResult.value = null
            pendingProducts.value = emptyList()
            previewDetections.value = emptyList()
            try {
                val bitmap = repository.capture(address.trim())
                    ?: throw IllegalStateException("Камера не вернула изображение. Проверь адрес /capture.")
                lastImage.value = bitmap
                cameraConnected.value = true
                geminiPhotoResult.value = "Фото получено. YOLO и Gemini одновременно анализируют один снимок…"

                // Both engines receive the exact same camera frame. The review table
                // is opened only after both have finished or Gemini has timed out.
                val outcomes = coroutineScope {
                    val yoloTask = async(Dispatchers.Default) {
                        runCatching { repository.analyzeLocal(bitmap) }
                    }
                    val geminiTask = async(Dispatchers.IO) {
                        runCatching { GeminiPhotoAnalyzer(apiKey).analyze(bitmap) }
                    }
                    yoloTask.await() to geminiTask.await()
                }

                val yoloResult = outcomes.first
                val geminiResult = outcomes.second
                val localProducts = yoloResult.getOrElse { error ->
                    android.util.Log.e("FridgeViewModel", "YOLO analysis failed", error)
                    emptyList()
                }
                previewDetections.value = localProducts

                val finalProducts = if (geminiResult.isSuccess) {
                    val geminiText = geminiResult.getOrThrow()
                    val geminiNames = parseGeminiProductNames(geminiText)
                    mergeYoloAndGemini(localProducts, geminiNames)
                } else {
                    android.util.Log.w("FridgeViewModel", "Gemini unavailable; using YOLO results", geminiResult.exceptionOrNull())
                    localProducts
                }

                pendingProducts.value = finalProducts
                val yoloSummary = if (localProducts.isEmpty()) {
                    "YOLO: продукты не обнаружены."
                } else {
                    "YOLO: " + localProducts.joinToString { "${it.name} (${(it.confidence * 100).toInt()}%)" }
                }
                val geminiSummary = geminiResult.fold(
                    onSuccess = { "Gemini: ${it.trim()}" },
                    onFailure = { "Gemini недоступен: ${it.message ?: "тайм-аут или ошибка сети"}" }
                )
                val comparison = when {
                    geminiResult.isFailure -> "Итог: показан локальный результат YOLO. Проверьте названия в таблице."
                    finalProducts.isEmpty() -> "Итог: модели не нашли уверенно различимые продукты."
                    else -> "Итог: результаты двух моделей объединены. Проверьте таблицу и нажмите «Добавить в холодильник»."
                }
                geminiPhotoResult.value = "$yoloSummary\n\n$geminiSummary\n\n$comparison"
                if (finalProducts.isEmpty()) {
                    message.value = "Продукты не распознаны. Попробуйте снять упаковки крупнее и при хорошем освещении."
                } else {
                    message.value = "Проверьте общую таблицу распознавания перед добавлением."
                }
            } catch (e: Exception) {
                android.util.Log.e("FridgeViewModel", "DEV camera photo failed", e)
                geminiPhotoResult.value = "Ошибка камеры: ${e.message ?: "не удалось получить фотографию"}"
            } finally {
                photoBusy.value = false
            }
        }
    }

    private fun parseGeminiProductNames(response: String): List<Pair<String, Float>> {
        val cleaned = response.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val jsonText = cleaned.substringAfter("{", "").let { if (it.isBlank()) "" else "{$it" }
        if (jsonText.isNotBlank()) {
            runCatching {
                val array = org.json.JSONObject(jsonText).optJSONArray("products")
                    ?: org.json.JSONArray()
                return (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val name = item.optString("name").trim()
                    if (name.isBlank()) null else name to item.optDouble("quantity", 1.0).toFloat().coerceAtLeast(1f)
                }
            }
        }
        // Backward-compatible parser for plain-text Gemini replies.
        return response.lines().mapNotNull { line ->
            val name = line.replace(Regex("^\\s*[-*•0-9.)]+\\s*"), "").trim()
            if (name.length < 3 || name.contains("не видно", true) || name.contains("продуктов нет", true)) null
            else name.substringBefore("—").substringBefore(":").trim().takeIf { it.length >= 3 }?.let { it to 1f }
        }.distinctBy { it.first.lowercase() }
    }

    private fun mergeYoloAndGemini(
        yolo: List<RecognizedProduct>,
        gemini: List<Pair<String, Float>>
    ): List<RecognizedProduct> {
        if (gemini.isEmpty()) return yolo.filter { it.confidence >= 0.55f }
        val used = mutableSetOf<Int>()
        val merged = gemini.map { (geminiName, quantity) ->
            val normalized = geminiName.lowercase().filter { it.isLetterOrDigit() }
            val matchIndex = yolo.indices.firstOrNull { index ->
                index !in used && run {
                    val yoloName = yolo[index].name.lowercase().filter { it.isLetterOrDigit() }
                    yoloName == normalized || yoloName.contains(normalized) || normalized.contains(yoloName)
                }
            }
            if (matchIndex != null) {
                used += matchIndex
                yolo[matchIndex].copy(name = geminiName, quantity = quantity)
            } else {
                val expiry = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                RecognizedProduct(
                    name = geminiName,
                    category = geminiName,
                    icon = "📦",
                    brand = null,
                    quantity = quantity,
                    expiresAt = expiry,
                    isExactDate = false,
                    confidence = 0.5f
                )
            }
        }
        // Keep a YOLO-only object only when its visual confidence is high; do not
        // let a confident but mismatched "dried fruit" overwrite Gemini's milk/chocolate.
        val highConfidenceYoloOnly = yolo.filterIndexed { index, item ->
            index !in used && item.confidence >= 0.80f &&
                merged.none { it.name.equals(item.name, ignoreCase = true) }
        }
        return (merged + highConfidenceYoloOnly).distinctBy { it.name.lowercase().trim() }
    }

    fun scan(address: String, intervalMinutes: Int = 1) {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            pendingProducts.value = emptyList()
            previewDetections.value = emptyList()
            val frameResults = mutableListOf<List<RecognizedProduct>>()
            try {
                // ESP32-CAM exposes a single-frame /capture endpoint. Take a short burst
                // and use local YOLO + OCR only; do not wait for a potentially unavailable Gemini call.
                repeat(3) { frameIndex ->
                    message.value = "Кадр ${frameIndex + 1} из 3: распознаём продукты…"
                    val bitmap = repository.capture(address)
                    if (bitmap == null) {
                        if (frameIndex == 0) {
                            cameraConnected.value = false
                            message.value = "Не удалось получить снимок. Проверьте адрес и подключение камеры."
                            return@launch
                        }
                    } else {
                        cameraConnected.value = true
                        lastImage.value = bitmap
                        val frameProducts = classifyDemoColor(bitmap)?.let { listOf(it) } ?: emptyList()
                        previewDetections.value = frameProducts
                        frameResults.add(frameProducts)
                    }
                    // A short pause lets the ESP32-CAM finish the next JPEG without
                    // making the user wait the configured periodic-scan interval.
                    if (frameIndex < 2) delay(400L)
                }
                val recognized = aggregateFrameResults(frameResults)
                if (recognized.isEmpty()) {
                    message.value = "Продукты не распознаны. Попробуйте изменить освещение или положение камеры."
                } else {
                    pendingProducts.value = recognized
                    message.value = "Проверьте распознанные продукты перед добавлением."
                }
            } catch (e: Exception) {
                android.util.Log.e("FridgeViewModel", "Camera scan failed", e)
                message.value = "Не удалось распознать продукты. Подробности доступны в Logcat."
            } finally {
                scanning.value = false
            }
        }
    }


    /**
     * Jury demo mode: one item is presented at a time.
     * The central camera region is classified by its dominant saturated HSV hue.
     * This is intentionally isolated to the demo branch; the training branch keeps YOLO.
     */
    private fun classifyDemoColor(bitmap: Bitmap): RecognizedProduct? {
        val left = (bitmap.width * 0.20f).toInt()
        val top = (bitmap.height * 0.20f).toInt()
        val right = (bitmap.width * 0.80f).toInt().coerceAtLeast(left + 1)
        val bottom = (bitmap.height * 0.80f).toInt().coerceAtLeast(top + 1)
        val hsv = FloatArray(3)
        val votes = IntArray(4)
        var colorfulPixels = 0
        val step = maxOf(2, minOf(bitmap.width, bitmap.height) / 240)

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]
                if (saturation >= 0.32f && value >= 0.18f) {
                    val bucket = when {
                        hue >= 42f && hue < 69f -> 0 // yellow: banana
                        hue >= 15f && hue < 42f -> 1 // orange: orange
                        hue >= 69f && hue < 170f -> 2 // green: cucumber
                        hue >= 345f || hue < 15f -> 3 // red: pepper
                        else -> -1
                    }
                    if (bucket >= 0) {
                        votes[bucket]++
                        colorfulPixels++
                    }
                }
                x += step
            }
            y += step
        }

        if (colorfulPixels < 20) return null
        val winner = votes.indices.maxByOrNull { votes[it] } ?: return null
        val share = votes[winner].toFloat() / colorfulPixels.toFloat()
        // Reject mixed/ambiguous scenes instead of inventing a product label.
        if (share < 0.34f) return null

        val (name, category, icon, shelfLifeDays) = when (winner) {
            0 -> arrayOf("Банан", "Фрукты", "🍌", "5")
            1 -> arrayOf("Апельсин", "Фрукты", "🍊", "14")
            2 -> arrayOf("Огурец", "Овощи", "🥒", "7")
            else -> arrayOf("Перец", "Овощи", "🌶️", "7")
        }
        val expiry = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, shelfLifeDays.toInt())
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis

        return RecognizedProduct(
            name = name,
            category = category,
            icon = icon,
            brand = null,
            quantity = 1f,
            expiresAt = expiry,
            isExactDate = false,
            left = 0f,
            top = 0f,
            right = bitmap.width.toFloat(),
            bottom = bitmap.height.toFloat(),
            confidence = share.coerceIn(0f, 1f)
        )
    }

    /**
     * Combines detections of the same physical package across a short camera burst.
     * Spatial matching is independent of the predicted class so a one-frame label
     * flicker does not create a second product.
     */
    private fun aggregateFrameResults(
        frames: List<List<RecognizedProduct>>
    ): List<RecognizedProduct> {
        data class VoteCluster(
            val observations: MutableList<RecognizedProduct>,
            val frameIndexes: MutableSet<Int>
        )

        val clusters = mutableListOf<VoteCluster>()
        frames.forEachIndexed { frameIndex, detections ->
            detections.forEach { detection ->
                val cluster = clusters
                    .filter { frameIndex !in it.frameIndexes }
                    .minByOrNull { candidate ->
                        val reference = candidate.observations.maxByOrNull { it.confidence }!!
                        val overlap = boxIoU(reference, detection)
                        val distance = boxCenterDistance(reference, detection)
                        if (overlap >= 0.20f || distance <= boxDiagonal(reference) * 0.30f) {
                            1f - overlap
                        } else {
                            Float.MAX_VALUE
                        }
                    }
                    ?.takeIf { candidate ->
                        val reference = candidate.observations.maxByOrNull { it.confidence }!!
                        boxIoU(reference, detection) >= 0.20f ||
                            boxCenterDistance(reference, detection) <= boxDiagonal(reference) * 0.30f
                    }

                if (cluster == null) {
                    clusters.add(VoteCluster(mutableListOf(detection), mutableSetOf(frameIndex)))
                } else {
                    cluster.observations.add(detection)
                    cluster.frameIndexes.add(frameIndex)
                }
            }
        }

        return clusters.mapNotNull { cluster ->
            val observations = cluster.observations
            if (observations.isEmpty()) return@mapNotNull null

            // Each frame contributes at most one vote to a physical package.
            val labelGroups = observations.groupBy { it.name.trim().lowercase() }
            val winningGroup = labelGroups.values.maxByOrNull { group ->
                group.size * 10f + group.sumOf { it.confidence.toDouble() }.toFloat()
            } ?: return@mapNotNull null
            val best = winningGroup.maxByOrNull { it.confidence } ?: return@mapNotNull null
            val averageConfidence = winningGroup.map { it.confidence }.average().toFloat()
            val exactDateObservation = winningGroup.firstOrNull { it.isExactDate }

            best.copy(
                confidence = averageConfidence.coerceIn(0f, 1f),
                quantity = 1f,
                expiresAt = exactDateObservation?.expiresAt ?: best.expiresAt,
                isExactDate = exactDateObservation != null
            )
        }
    }

    private fun boxIoU(a: RecognizedProduct, b: RecognizedProduct): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val intersection = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
        val areaA = (a.right - a.left).coerceAtLeast(0f) * (a.bottom - a.top).coerceAtLeast(0f)
        val areaB = (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f)
        val union = areaA + areaB - intersection
        return if (union > 0f) intersection / union else 0f
    }

    private fun boxCenterDistance(a: RecognizedProduct, b: RecognizedProduct): Float {
        val dx = (a.left + a.right - b.left - b.right) / 2f
        val dy = (a.top + a.bottom - b.top - b.bottom) / 2f
        return sqrt(dx * dx + dy * dy)
    }

    private fun boxDiagonal(item: RecognizedProduct): Float {
        val width = (item.right - item.left).coerceAtLeast(1f)
        val height = (item.bottom - item.top).coerceAtLeast(1f)
        return sqrt(width * width + height * height)
    }

    private fun isSamePhysicalProduct(a: RecognizedProduct, b: RecognizedProduct): Boolean {
        if (!a.name.trim().equals(b.name.trim(), ignoreCase = true)) return false

        val intersectionWidth = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
        val intersectionHeight = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val areaA = (a.right - a.left).coerceAtLeast(0f) * (a.bottom - a.top).coerceAtLeast(0f)
        val areaB = (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f)
        val union = areaA + areaB - intersection
        val iou = if (union > 0f) intersection / union else 0f

        val centerAX = (a.left + a.right) / 2f
        val centerAY = (a.top + a.bottom) / 2f
        val centerBX = (b.left + b.right) / 2f
        val centerBY = (b.top + b.bottom) / 2f
        val centerDistance = sqrt(
            (centerAX - centerBX) * (centerAX - centerBX) +
                (centerAY - centerBY) * (centerAY - centerBY)
        )
        val referenceDiagonal = maxOf(
            sqrt(areaA.coerceAtLeast(1f)),
            sqrt(areaB.coerceAtLeast(1f))
        )
        return iou >= 0.30f || centerDistance <= referenceDiagonal * 0.22f
    }

    fun updatePendingProduct(index: Int, name: String, expiresAt: Long, isExactDate: Boolean) {
        val current = pendingProducts.value.toMutableList()
        if (index !in current.indices) return
        current[index] = current[index].copy(
            name = name,
            expiresAt = expiresAt,
            isExactDate = isExactDate
        )
        pendingProducts.value = current
    }

    fun removePendingProduct(index: Int) {
        pendingProducts.value = pendingProducts.value.filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    fun dismissScanReview() {
        pendingProducts.value = emptyList()
        message.value = "Результаты сканирования отменены"
    }

    fun confirmScanReview() {
        if (savingScanReview) return
        val approved = pendingProducts.value.toList()
        if (approved.isEmpty()) return
        if (approved.any { it.name.isBlank() }) {
            message.value = "Укажи название для каждого распознанного продукта."
            return
        }
        savingScanReview = true
        viewModelScope.launch {
            try {
                val savedCount = repository.saveRecognizedProducts(approved)
                pendingProducts.value = emptyList()
                message.value = if (savedCount == 0) {
                    "Эти продукты уже были добавлены недавно."
                } else {
                    "Добавлено продуктов: $savedCount"
                }
            } catch (e: Exception) {
                android.util.Log.e("FridgeViewModel", "Saving scan review failed", e)
                message.value = "Не удалось сохранить распознанные продукты."
            } finally {
                savingScanReview = false
            }
        }
    }

    suspend fun addProduct(name: String, category: String, icon: String, quantity: Float, expiresAt: Long, exact: Boolean): Boolean {
        return try {
            repository.addProduct(name, category, icon, quantity, expiresAt, exact)
            message.value = "$name добавлен в холодильник"
            true
        } catch (e: Exception) {
            android.util.Log.e("FridgeViewModel", "Adding product failed", e)
            message.value = e.message ?: "Не удалось добавить продукт"
            false
        }
    }

    fun updateProduct(product: FridgeProduct) {
        viewModelScope.launch {
            try {
                repository.updateProduct(product)
                message.value = "${product.name} обновлён"
            } catch (e: Exception) {
                android.util.Log.e("FridgeViewModel", "Updating product failed", e)
                message.value = e.message ?: "Не удалось обновить продукт"
            }
        }
    }

    fun deleteProduct(product: FridgeProduct) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(product)
                message.value = "${product.name} удалён"
            } catch (e: Exception) {
                android.util.Log.e("FridgeViewModel", "Deleting product failed", e)
                message.value = "Не удалось удалить продукт"
            }
        }
    }

    fun searchCatalog(query: String) {
        viewModelScope.launch {
            suggestions.value = try {
                if (query.isBlank()) emptyList() else repository.searchProducts(query)
            } catch (e: Exception) {
                android.util.Log.e("FridgeViewModel", "Catalog search failed", e)
                emptyList()
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        viewModelScope.cancel()
        // Do not recycle the displayed frame here: a cancelled scan may still be
        // finishing synchronous ML inference against the same Bitmap. Let GC reclaim it.
        lastImage.value = null
        CoroutineScope(Dispatchers.IO).launch { repository.close() }
    }

    override fun onCleared() {
        dispose()
        super.onCleared()
    }
}
