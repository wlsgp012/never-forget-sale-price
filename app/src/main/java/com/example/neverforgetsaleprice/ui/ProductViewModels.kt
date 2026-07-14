package com.example.neverforgetsaleprice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neverforgetsaleprice.data.ProductEntity
import com.example.neverforgetsaleprice.data.ProductRepository
import com.example.neverforgetsaleprice.data.SaveProductResult
import com.example.neverforgetsaleprice.domain.CheckInterval
import com.example.neverforgetsaleprice.domain.CheckIntervalUnit
import com.example.neverforgetsaleprice.domain.PriceNormalizer
import com.example.neverforgetsaleprice.network.ProductFetchResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductListViewModel(repository: ProductRepository) : ViewModel() {
    val state: StateFlow<ProductListState> = repository.observeProducts()
        .map { ProductListState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductListState())
}

data class ProductListState(
    val products: List<ProductEntity> = emptyList()
)

class AddProductViewModel(
    private val repository: ProductRepository
) : ViewModel() {
    var state = androidx.compose.runtime.mutableStateOf(AddProductState())
        private set

    fun updateUrl(value: String) {
        state.value = state.value.copy(url = value, errorMessage = null)
    }

    fun updateName(value: String) {
        state.value = state.value.copy(name = value, errorMessage = null)
    }

    fun updateOriginalPrice(value: String) {
        state.value = state.value.copy(originalPrice = value, errorMessage = null)
    }

    fun updateCurrentPrice(value: String) {
        state.value = state.value.copy(currentPrice = value, errorMessage = null)
    }

    fun updateImageUrl(value: String) {
        state.value = state.value.copy(imageUrl = value, errorMessage = null)
    }

    fun updateCheckIntervalValue(value: String) {
        state.value = state.value.copy(checkIntervalValue = value.filter { it.isDigit() }, errorMessage = null)
    }

    fun updateCheckIntervalUnit(unit: CheckIntervalUnit) {
        state.value = state.value.copy(checkIntervalUnit = unit, errorMessage = null)
    }

    fun fetch() {
        val url = state.value.url.trim()
        state.value = state.value.copy(isLoading = true, errorMessage = null, confidenceNote = null)
        viewModelScope.launch {
            when (val result = repository.fetchMetadata(url)) {
                is ProductFetchResult.Success -> {
                    val metadata = result.metadata
                    state.value = state.value.copy(
                        isLoading = false,
                        name = metadata.title.orEmpty(),
                        currentPrice = metadata.price?.toString().orEmpty(),
                        imageUrl = metadata.imageUrl.orEmpty(),
                        confidenceNote = metadata.confidenceNote,
                        errorMessage = null
                    )
                }
                is ProductFetchResult.Failure -> {
                    state.value = state.value.copy(
                        isLoading = false,
                        errorMessage = result.message,
                        confidenceNote = null
                    )
                }
            }
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val current = state.value
        val originalPrice = PriceNormalizer.parsePrice(current.originalPrice) ?: 0L
        val currentPrice = PriceNormalizer.parsePrice(current.currentPrice)
        val checkIntervalSeconds = current.checkIntervalSeconds()
        state.value = current.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = repository.createProduct(
                url = current.url,
                name = current.name,
                originalPrice = originalPrice,
                checkIntervalSeconds = checkIntervalSeconds,
                currentPrice = currentPrice,
                imageUrl = current.imageUrl
            )) {
                is SaveProductResult.Success -> onSaved(result.productId)
                is SaveProductResult.ValidationError -> {
                    state.value = state.value.copy(isSaving = false, errorMessage = result.message)
                }
            }
        }
    }
}

data class AddProductState(
    val url: String = "",
    val name: String = "",
    val originalPrice: String = "",
    val currentPrice: String = "",
    val imageUrl: String = "",
    val checkIntervalValue: String = "6",
    val checkIntervalUnit: CheckIntervalUnit = CheckIntervalUnit.Hours,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val confidenceNote: String? = null,
    val errorMessage: String? = null
) {
    fun checkIntervalSeconds(): Long {
        val value = checkIntervalValue.toLongOrNull() ?: 0L
        return CheckInterval.clamp(value * checkIntervalUnit.multiplier)
    }
}

class ProductDetailViewModel(
    private val repository: ProductRepository,
    productId: Long
) : ViewModel() {
    val state: StateFlow<ProductDetailState> = repository.observeProduct(productId)
        .map { ProductDetailState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductDetailState())

    fun setActive(product: ProductEntity, active: Boolean) {
        viewModelScope.launch {
            repository.setProductActive(product, active)
        }
    }

    fun delete(product: ProductEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            onDeleted()
        }
    }
}

data class ProductDetailState(
    val product: ProductEntity? = null
)

class RepositoryViewModelFactory<T : ViewModel>(
    private val create: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}
