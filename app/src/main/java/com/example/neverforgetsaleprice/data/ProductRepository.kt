package com.example.neverforgetsaleprice.data

import android.webkit.URLUtil
import com.example.neverforgetsaleprice.domain.CheckInterval
import com.example.neverforgetsaleprice.domain.CheckStatus
import com.example.neverforgetsaleprice.domain.DiscountPolicy
import com.example.neverforgetsaleprice.network.HttpStatusException
import com.example.neverforgetsaleprice.network.ProductFetchResult
import com.example.neverforgetsaleprice.network.ProductMetadataExtractor
import com.example.neverforgetsaleprice.network.ProductPageFetcher
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProductRepository(
    private val productDao: ProductDao,
    private val pageFetcher: ProductPageFetcher,
    private val metadataExtractor: ProductMetadataExtractor,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun observeProducts(): Flow<List<ProductEntity>> = productDao.observeProducts()

    fun observeProduct(id: Long): Flow<ProductEntity?> = productDao.observeProduct(id)

    suspend fun getProduct(id: Long): ProductEntity? = productDao.getProduct(id)

    suspend fun fetchMetadata(url: String): ProductFetchResult = withContext(Dispatchers.IO) {
        if (!isValidHttpUrl(url)) {
            return@withContext ProductFetchResult.Failure("http 또는 https URL을 입력해 주세요.")
        }

        runCatching {
            val html = pageFetcher.fetch(url)
            ProductFetchResult.Success(metadataExtractor.extract(html, url))
        }.getOrElse { error ->
            ProductFetchResult.Failure(error.toUserMessage())
        }
    }

    suspend fun createProduct(
        url: String,
        name: String,
        originalPrice: Long,
        checkIntervalSeconds: Long,
        currentPrice: Long?,
        imageUrl: String?
    ): SaveProductResult {
        val validationError = validateProduct(url, name, originalPrice, checkIntervalSeconds, currentPrice)
        if (validationError != null) return SaveProductResult.ValidationError(validationError)

        val now = clock()
        val product = ProductEntity(
            url = url.trim(),
            name = name.trim(),
            originalPrice = originalPrice,
            checkIntervalSeconds = CheckInterval.clamp(checkIntervalSeconds),
            imageUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() },
            lastCheckedPrice = currentPrice,
            lastCheckedAtMillis = currentPrice?.let { now },
            lastCheckStatus = currentPrice?.let { CheckStatus.Success } ?: CheckStatus.NeverChecked,
            lastCheckError = null,
            lastNotifiedPrice = null,
            lastNotifiedDiscountPercent = null,
            lastNotifiedAtMillis = null,
            createdAtMillis = now,
            updatedAtMillis = now
        )

        val id = productDao.insert(product)
        return SaveProductResult.Success(id)
    }

    suspend fun updateProduct(
        product: ProductEntity,
        name: String,
        originalPrice: Long,
        checkIntervalSeconds: Long,
        imageUrl: String?,
        isActive: Boolean
    ): SaveProductResult {
        val validationError = validateProduct(
            product.url,
            name,
            originalPrice,
            checkIntervalSeconds,
            product.lastCheckedPrice
        )
        if (validationError != null) return SaveProductResult.ValidationError(validationError)

        productDao.update(
            product.copy(
                name = name.trim(),
                originalPrice = originalPrice,
                checkIntervalSeconds = CheckInterval.clamp(checkIntervalSeconds),
                imageUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() },
                isActive = isActive,
                updatedAtMillis = clock()
            )
        )
        return SaveProductResult.Success(product.id)
    }

    suspend fun deleteProduct(product: ProductEntity) {
        productDao.delete(product)
    }

    suspend fun setProductActive(product: ProductEntity, active: Boolean) {
        productDao.update(product.copy(isActive = active, updatedAtMillis = clock()))
    }

    suspend fun checkActiveProducts(onNotificationNeeded: suspend (ProductEntity, Int, Long) -> Boolean) {
        val now = clock()
        productDao.getActiveProducts().filter { it.isDueForCheck(now) }.forEach { product ->
            checkProduct(product, onNotificationNeeded)
        }
    }

    suspend fun nextDelaySeconds(): Long {
        val now = clock()
        val activeProducts = productDao.getActiveProducts()
        if (activeProducts.isEmpty()) return CheckInterval.DEFAULT_SECONDS

        val nextDelayMillis = activeProducts.minOf { product ->
            val lastCheckedAt = product.lastCheckedAtMillis ?: return 1L
            val nextDueAt = lastCheckedAt + CheckInterval.clamp(product.checkIntervalSeconds) * 1000L
            (nextDueAt - now).coerceAtLeast(0L)
        }

        return ((nextDelayMillis + 999L) / 1000L).coerceAtLeast(1L)
    }

    suspend fun checkProduct(
        product: ProductEntity,
        onNotificationNeeded: suspend (ProductEntity, Int, Long) -> Boolean
    ) = withContext(Dispatchers.IO) {
        val now = clock()
        val checkedProduct = runCatching {
            val html = pageFetcher.fetch(product.url)
            val metadata = metadataExtractor.extract(html, product.url)
            val currentPrice = metadata.price
                ?: return@runCatching product.copy(
                    lastCheckedAtMillis = now,
                    lastCheckStatus = CheckStatus.ParseError,
                    lastCheckError = "가격을 찾지 못했습니다.",
                    updatedAtMillis = now
                )

            product.copy(
                lastCheckedPrice = currentPrice,
                lastCheckedAtMillis = now,
                lastCheckStatus = CheckStatus.Success,
                lastCheckError = null,
                updatedAtMillis = now
            )
        }.getOrElse { error ->
            product.copy(
                lastCheckedAtMillis = now,
                lastCheckStatus = error.toCheckStatus(),
                lastCheckError = error.toUserMessage(),
                updatedAtMillis = now
            )
        }

        productDao.update(checkedProduct)

        val currentPrice = checkedProduct.lastCheckedPrice
        if (DiscountPolicy.shouldNotify(
                isActive = checkedProduct.isActive,
                originalPrice = checkedProduct.originalPrice,
                currentPrice = currentPrice,
                lastNotifiedPrice = checkedProduct.lastNotifiedPrice,
                lastNotifiedDiscountPercent = checkedProduct.lastNotifiedDiscountPercent
            )
        ) {
            val notifiedPrice = currentPrice ?: return@withContext
            val discountPercent = DiscountPolicy.discountPercent(checkedProduct.originalPrice, notifiedPrice)
            val posted = onNotificationNeeded(checkedProduct, discountPercent, notifiedPrice)
            if (posted) {
                productDao.update(
                    checkedProduct.copy(
                        lastNotifiedPrice = notifiedPrice,
                        lastNotifiedDiscountPercent = discountPercent,
                        lastNotifiedAtMillis = clock(),
                        updatedAtMillis = clock()
                    )
                )
            }
        }
    }

    private fun validateProduct(
        url: String,
        name: String,
        originalPrice: Long,
        checkIntervalSeconds: Long,
        currentPrice: Long?
    ): String? {
        return when {
            !isValidHttpUrl(url) -> "http 또는 https URL을 입력해 주세요."
            name.isBlank() -> "상품명을 입력해 주세요."
            originalPrice <= 0L -> "원래 가격은 0보다 커야 합니다."
            checkIntervalSeconds !in CheckInterval.MIN_SECONDS..CheckInterval.MAX_SECONDS -> "조회 주기는 1초에서 24시간 사이로 입력해 주세요."
            currentPrice != null && currentPrice <= 0L -> "현재 가격은 0보다 커야 합니다."
            else -> null
        }
    }

    private fun ProductEntity.isDueForCheck(nowMillis: Long): Boolean {
        val lastCheckedAt = lastCheckedAtMillis ?: return true
        val intervalMillis = CheckInterval.clamp(checkIntervalSeconds) * 1000L
        return nowMillis - lastCheckedAt >= intervalMillis
    }

    private fun isValidHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        return URLUtil.isHttpUrl(trimmed) || URLUtil.isHttpsUrl(trimmed)
    }

    private fun Throwable.toCheckStatus(): CheckStatus {
        return when (this) {
            is HttpStatusException -> CheckStatus.HttpError
            is IllegalArgumentException -> CheckStatus.InvalidUrl
            is IOException -> CheckStatus.NetworkError
            else -> CheckStatus.ParseError
        }
    }

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            is HttpStatusException -> message ?: "페이지 응답이 올바르지 않습니다."
            is IOException -> "네트워크 연결 또는 페이지 요청에 실패했습니다."
            else -> message ?: "페이지를 분석하지 못했습니다."
        }
    }
}

sealed interface SaveProductResult {
    data class Success(val productId: Long) : SaveProductResult
    data class ValidationError(val message: String) : SaveProductResult
}
