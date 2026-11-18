
package com.example.smartfridge.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfridge.data.ProductCatalogEntity
import com.example.smartfridge.domain.FridgeProduct
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

    private val message = MutableStateFlow<String?>(null)

    private val suggestions =
        MutableStateFlow<List<ProductCatalogEntity>>(emptyList())

    private val productFilters = combine(
        repository.products,
        search,
        category
    ) { products, query, selectedCategory ->

        val filtered = products.filter { product ->

            val matchesSearch =
                query.isBlank() ||
                        product.name.contains(query, ignoreCase = true)

            val matchesCategory =
                selectedCategory == null ||
                        product.category == selectedCategory

            matchesSearch && matchesCategory
        }

        Triple(filtered, query, selectedCategory) to products
    }

    private val cameraState = combine(
        cameraConnected,
        cameraSearching,
        scanning,
        lastImage,
        message
    ) { connected, searching, scanningNow, image, msg ->

        FridgeUiState(
            cameraConnected = connected,
            cameraSearching = searching,
            scanning = scanningNow,
            lastImage = image,
            message = msg
        )
    }

    val uiState: StateFlow<FridgeUiState> = combine(
        productFilters,
        cameraState
    ) { filters, camera ->

        FridgeUiState(
            products = filters.first.first,
            allProducts = filters.second,
            search = filters.first.second,
            category = filters.first.third,
            cameraConnected = camera.cameraConnected,
            cameraSearching = camera.cameraSearching,
            scanning = camera.scanning,
            lastImage = camera.lastImage,
            message = camera.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FridgeUiState()
    )

    val productSuggestions: StateFlow<List<ProductCatalogEntity>> =
        suggestions.asStateFlow()

    val productCatalog: StateFlow<List<ProductCatalogEntity>> =
        repository.productCatalog.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearch(value: String) {
        search.value = value
    }

    fun setCategory(value: String?) {
        category.value = value
    }

    fun setCameraConnected(value: Boolean) {
        cameraConnected.value = value
    }

    fun setCameraSearching(value: Boolean) {
        cameraSearching.value = value
    }

    fun setScanning(value: Boolean) {
        scanning.value = value
    }

    fun setImage(bitmap: Bitmap?) {
        lastImage.value = bitmap
    }

    fun setMessage(value: String?) {
        message.value = value
    }

    fun scan(address: String) {
        viewModelScope.launch {
            scanning.value = true
            message.value = "Проверяем содержимое холодильника…"

            try {
                val bitmap = repository.capture(address)

                if (bitmap == null) {
                    message.value =
                        "Не удалось получить снимок. Проверьте подключение камеры."
                    return@launch
                }

                lastImage.value = bitmap

                repository.analyze(bitmap)

                message.value = "Холодильник обновлён"

            } catch (e: Exception) {
                e.printStackTrace()
                message.value =
                    "Не удалось распознать продукты. Попробуйте ещё раз."
            } finally {
                scanning.value = false
            }
        }
    }

    fun addProduct(
        name: String,
        category: String,
        icon: String,
        quantity: Float,
        expiresAt: Long,
        exact: Boolean
    ) {
        viewModelScope.launch {
            try {
                repository.addProduct(
                    name,
                    category,
                    icon,
                    quantity,
                    expiresAt,
                    exact
                )

                message.value = "$name добавлен в холодильник"

            } catch (e: Exception) {
                e.printStackTrace()
                message.value = "Не удалось добавить продукт"
            }
        }
    }

    fun deleteProduct(product: FridgeProduct) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(product)
                message.value = "${product.name} удалён"
            } catch (e: Exception) {
                e.printStackTrace()
                message.value = "Не удалось удалить продукт"
            }
        }
    }

    fun searchCatalog(query: String) {
        viewModelScope.launch {
            try {
                suggestions.value = if (query.isBlank()) {
                    emptyList()
                } else {
                    repository.searchProducts(query)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                suggestions.value = emptyList()
            }
        }
    }
}
