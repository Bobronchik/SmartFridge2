package com.example.smartfridge.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfridge.data.ProductCatalogEntity
import com.example.smartfridge.domain.FridgeProduct
import com.example.smartfridge.ml.RecognizedProduct
import com.example.smartfridge.repository.SmartFridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        combine(scanning, lastImage, pendingProducts, previewDetections) {
                scanningNow, image, pending, detections ->
            FridgeUiState(
                scanning = scanningNow,
                lastImage = image,
                pendingProducts = pending,
                previewDetections = detections
            )
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

    fun scan(address: String, intervalMinutes: Int = 1) {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            pendingProducts.value = emptyList()
            previewDetections.value = emptyList()
            val recognizedAcrossFrames = mutableListOf<RecognizedProduct>()
            try {
                // The current camera firmware exposes /capture (single JPEG), not an
                // RTSP/WebRTC stream. Poll a small burst of frames using the user interval.
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
                        val frameProducts = repository.analyze(bitmap)
                        previewDetections.value = frameProducts
                        frameProducts.forEach { item ->
                            // Match the same physical package by its bounding box, not by a
                            // coarse fixed-size grid. Separate packages of the same class remain.
                            val duplicateIndex = recognizedAcrossFrames.indexOfFirst { previous ->
                                isSamePhysicalProduct(previous, item)
                            }
                            if (duplicateIndex < 0) {
                                recognizedAcrossFrames.add(item)
                            } else {
                                val previous = recognizedAcrossFrames[duplicateIndex]
                                if (
                                    (!previous.isExactDate && item.isExactDate) ||
                                    item.confidence > previous.confidence
                                ) {
                                    recognizedAcrossFrames[duplicateIndex] = item
                                }
                            }
                        }
                    }
                    if (frameIndex < 2) delay(intervalMinutes.coerceIn(1, 1440) * 60_000L)
                }
                val recognized = recognizedAcrossFrames.toList()
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
                repository.saveRecognizedProducts(approved)
                pendingProducts.value = emptyList()
                message.value = "Добавлено продуктов: ${approved.size}"
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
