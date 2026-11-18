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
    private val message = MutableStateFlow<String?>(null)
    private val suggestions = MutableStateFlow<List<ProductCatalogEntity>>(emptyList())

    private val productFilters = combine(repository.products, search, category) { products, query, selectedCategory ->
        val filtered = products.filter { product ->
            (query.isBlank() || product.name.contains(query, ignoreCase = true)) &&
                (selectedCategory == null || product.category == selectedCategory)
        }
        Triple(filtered, query, selectedCategory) to products
    }

    private val cameraState = combine(
        cameraConnected, cameraSearching, scanning, lastImage, pendingProducts, message
    ) { connected, searching, scanningNow, image, pending, msg ->
        FridgeUiState(
            cameraConnected = connected,
            cameraSearching = searching,
            scanning = scanningNow,
            lastImage = image,
            pendingProducts = pending,
            message = msg
        )
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

    fun scan(address: String) {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            pendingProducts.value = emptyList()
            message.value = "Получаем снимок и распознаём продукты…"
            try {
                val bitmap = repository.capture(address)
                if (bitmap == null) {
                    message.value = "Не удалось получить снимок. Проверьте адрес и подключение камеры."
                    return@launch
                }
                lastImage.value = bitmap
                val recognized = repository.analyze(bitmap)
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

    fun updatePendingProduct(index: Int, name: String, expiresAt: Long, isExactDate: Boolean) {
        val current = pendingProducts.value.toMutableList()
        if (index !in current.indices || name.isBlank()) return
        current[index] = current[index].copy(
            name = name.trim(),
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
        val approved = pendingProducts.value.toList()
        if (approved.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.saveRecognizedProducts(approved)
                pendingProducts.value = emptyList()
                message.value = "Добавлено продуктов: ${approved.size}"
            } catch (e: Exception) {
                android.util.Log.e("FridgeViewModel", "Saving scan review failed", e)
                message.value = "Не удалось сохранить распознанные продукты."
            }
        }
    }

    fun addProduct(name: String, category: String, icon: String, quantity: Float, expiresAt: Long, exact: Boolean) {
        viewModelScope.launch {
            try {
                repository.addProduct(name, category, icon, quantity, expiresAt, exact)
                message.value = "$name добавлен в холодильник"
            } catch (e: Exception) {
                android.util.Log.e("FridgeViewModel", "Adding product failed", e)
                message.value = e.message ?: "Не удалось добавить продукт"
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

    override fun onCleared() {
        lastImage.value?.let { if (!it.isRecycled) it.recycle() }
        repository.close()
        super.onCleared()
    }
}
