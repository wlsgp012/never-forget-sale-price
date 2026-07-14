package com.example.neverforgetsaleprice.network

data class ProductMetadata(
    val title: String?,
    val price: Long?,
    val imageUrl: String?,
    val confidenceNote: String
)

sealed interface ProductFetchResult {
    data class Success(val metadata: ProductMetadata) : ProductFetchResult
    data class Failure(val message: String) : ProductFetchResult
}
