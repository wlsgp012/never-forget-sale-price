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
import org.json.JSONArray
import org.json.JSONObject

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
            val metadata = metadataExtractor.extract(html, url)
            if (metadata.price == null && isXboxAuthUrl(url)) {
                ProductFetchResult.Failure("Xbox 로그인 완료 URL에는 상품 가격 정보가 없습니다. Xbox 또는 Microsoft Store의 상품 상세 URL을 등록해 주세요.")
            } else {
                ProductFetchResult.Success(metadata)
            }
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

    suspend fun exportProductsJson(): String = withContext(Dispatchers.IO) {
        val products = productDao.getAllProducts()
        JSONObject()
            .put("version", 1)
            .put("exportedAtMillis", clock())
            .put(
                "products",
                JSONArray().apply {
                    products.forEach { put(it.toJson()) }
                }
            )
            .toString(2)
    }

    suspend fun importProductsJson(json: String): ImportProductsResult = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(json)
            val products = root.optJSONArray("products")
                ?: return@withContext ImportProductsResult.Failure("상품 목록을 찾지 못했습니다.")
            var inserted = 0
            var updated = 0
            val now = clock()

            for (index in 0 until products.length()) {
                val productJson = products.optJSONObject(index) ?: continue
                val url = productJson.optString("url").trim()
                val name = productJson.optString("name").trim()
                val originalPrice = productJson.optLong("originalPrice", 0L)
                val checkIntervalSeconds = productJson.optLong(
                    "checkIntervalSeconds",
                    CheckInterval.DEFAULT_SECONDS
                )
                val currentPrice = productJson.optNullableLong("lastCheckedPrice")
                val validationError = validateProduct(
                    url = url,
                    name = name,
                    originalPrice = originalPrice,
                    checkIntervalSeconds = checkIntervalSeconds,
                    currentPrice = currentPrice
                )
                if (validationError != null) continue

                val existing = productDao.getProductByUrl(url)
                val imported = productJson.toProductEntity(
                    id = existing?.id ?: 0L,
                    now = now
                )
                if (existing == null) {
                    productDao.insert(imported)
                    inserted += 1
                } else {
                    productDao.update(
                        imported.copy(
                            id = existing.id,
                            createdAtMillis = existing.createdAtMillis
                        )
                    )
                    updated += 1
                }
            }

            ImportProductsResult.Success(inserted = inserted, updated = updated)
        }.getOrElse { error ->
            ImportProductsResult.Failure(error.message ?: "JSON 파일을 읽지 못했습니다.")
        }
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

    private fun isXboxAuthUrl(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.contains("xbox.com/") && trimmed.contains("/auth/msa")
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

    private fun ProductEntity.toJson(): JSONObject {
        return JSONObject()
            .put("url", url)
            .put("name", name)
            .put("originalPrice", originalPrice)
            .put("checkIntervalSeconds", checkIntervalSeconds)
            .put("imageUrl", imageUrl)
            .put("isActive", isActive)
            .put("lastCheckedPrice", lastCheckedPrice)
            .put("lastCheckedAtMillis", lastCheckedAtMillis)
            .put("lastCheckStatus", lastCheckStatus.name)
            .put("lastCheckError", lastCheckError)
            .put("lastNotifiedPrice", lastNotifiedPrice)
            .put("lastNotifiedDiscountPercent", lastNotifiedDiscountPercent)
            .put("lastNotifiedAtMillis", lastNotifiedAtMillis)
            .put("createdAtMillis", createdAtMillis)
            .put("updatedAtMillis", updatedAtMillis)
    }

    private fun JSONObject.toProductEntity(id: Long, now: Long): ProductEntity {
        return ProductEntity(
            id = id,
            url = optString("url").trim(),
            name = optString("name").trim(),
            originalPrice = optLong("originalPrice"),
            checkIntervalSeconds = CheckInterval.clamp(
                optLong("checkIntervalSeconds", CheckInterval.DEFAULT_SECONDS)
            ),
            imageUrl = optStringOrNull("imageUrl"),
            isActive = optBoolean("isActive", true),
            lastCheckedPrice = optNullableLong("lastCheckedPrice"),
            lastCheckedAtMillis = optNullableLong("lastCheckedAtMillis"),
            lastCheckStatus = optString("lastCheckStatus")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { CheckStatus.valueOf(it) }.getOrDefault(CheckStatus.NeverChecked) }
                ?: CheckStatus.NeverChecked,
            lastCheckError = optStringOrNull("lastCheckError"),
            lastNotifiedPrice = optNullableLong("lastNotifiedPrice"),
            lastNotifiedDiscountPercent = optNullableInt("lastNotifiedDiscountPercent"),
            lastNotifiedAtMillis = optNullableLong("lastNotifiedAtMillis"),
            createdAtMillis = optNullableLong("createdAtMillis") ?: now,
            updatedAtMillis = now
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (isNull(key) || !has(key)) return null
        return runCatching { getLong(key) }.getOrNull()
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (isNull(key) || !has(key)) return null
        return runCatching { getInt(key) }.getOrNull()
    }
}

sealed interface SaveProductResult {
    data class Success(val productId: Long) : SaveProductResult
    data class ValidationError(val message: String) : SaveProductResult
}

sealed interface ImportProductsResult {
    data class Success(val inserted: Int, val updated: Int) : ImportProductsResult
    data class Failure(val message: String) : ImportProductsResult
}
