package com.example.neverforgetsaleprice.network

import com.example.neverforgetsaleprice.domain.PriceNormalizer
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ProductMetadataExtractor {
    fun extract(html: String, baseUrl: String): ProductMetadata {
        val document = Jsoup.parse(html, baseUrl)
        val jsonLd = readJsonLdProduct(document)
        val metaTitle = metaContent(document, "meta[property=og:title]") ?: document.title()
        val metaImage = metaContent(document, "meta[property=og:image]")
            ?: metaContent(document, "meta[name=twitter:image]")
        val metaPrice = findMetaPrice(document)
        val visibleTextPrice = findVisibleTextPrice(document)

        val title = jsonLd.title ?: metaTitle.takeIf { it.isNotBlank() }
        val price = jsonLd.price ?: metaPrice ?: visibleTextPrice
        val imageUrl = jsonLd.imageUrl ?: metaImage
        val confidence = when {
            jsonLd.price != null -> "structured JSON-LD product data"
            metaPrice != null -> "price-like meta tag"
            visibleTextPrice != null -> "visible page text"
            else -> "no price candidate found"
        }

        return ProductMetadata(
            title = title?.cleanTitle(),
            price = price,
            imageUrl = imageUrl,
            confidenceNote = confidence
        )
    }

    private fun readJsonLdProduct(document: Document): JsonLdCandidate {
        val scripts = document.select("script[type=application/ld+json]")
        scripts.forEach { script ->
            val candidate = parseJsonLd(script.data())
            if (candidate.hasAnyValue()) return candidate
        }
        return JsonLdCandidate()
    }

    private fun parseJsonLd(rawJson: String): JsonLdCandidate {
        return runCatching {
            val trimmed = rawJson.trim()
            when {
                trimmed.startsWith("[") -> parseJsonArray(JSONArray(trimmed))
                trimmed.startsWith("{") -> parseJsonObject(JSONObject(trimmed))
                else -> JsonLdCandidate()
            }
        }.getOrDefault(JsonLdCandidate())
    }

    private fun parseJsonArray(array: JSONArray): JsonLdCandidate {
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            val candidate = when (value) {
                is JSONObject -> parseJsonObject(value)
                is JSONArray -> parseJsonArray(value)
                else -> JsonLdCandidate()
            }
            if (candidate.hasAnyValue()) return candidate
        }
        return JsonLdCandidate()
    }

    private fun parseJsonObject(json: JSONObject): JsonLdCandidate {
        val type = json.opt("@type")
        val isProduct = when (type) {
            is String -> type.equals("Product", ignoreCase = true)
            is JSONArray -> (0 until type.length()).any {
                type.optString(it).equals("Product", ignoreCase = true)
            }
            else -> false
        }

        val directCandidate = if (isProduct) {
            JsonLdCandidate(
                title = json.optString("name").takeIf { it.isNotBlank() },
                price = readOfferPrice(json),
                imageUrl = readImage(json)
            )
        } else {
            JsonLdCandidate()
        }

        if (directCandidate.hasAnyValue()) return directCandidate

        val graph = json.optJSONArray("@graph")
        if (graph != null) return parseJsonArray(graph)

        json.keys().forEach { key ->
            when (val value = json.opt(key)) {
                is JSONObject -> parseJsonObject(value).also { if (it.hasAnyValue()) return it }
                is JSONArray -> parseJsonArray(value).also { if (it.hasAnyValue()) return it }
            }
        }

        return JsonLdCandidate()
    }

    private fun readOfferPrice(product: JSONObject): Long? {
        val offers = product.opt("offers") ?: return null
        return when (offers) {
            is JSONObject -> readPriceFromObject(offers)
            is JSONArray -> {
                for (index in 0 until offers.length()) {
                    val price = (offers.opt(index) as? JSONObject)?.let(::readPriceFromObject)
                    if (price != null) return price
                }
                null
            }
            else -> null
        }
    }

    private fun readPriceFromObject(json: JSONObject): Long? {
        val price = json.optString("price")
            .ifBlank { json.optString("lowPrice") }
            .ifBlank { json.optString("highPrice") }
        return PriceNormalizer.parsePrice(price)
    }

    private fun readImage(json: JSONObject): String? {
        return when (val image = json.opt("image")) {
            is String -> image.takeIf { it.isNotBlank() }
            is JSONArray -> image.optString(0).takeIf { it.isNotBlank() }
            is JSONObject -> image.optString("url").takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun findMetaPrice(document: Document): Long? {
        val selectors = listOf(
            "meta[property=product:price:amount]",
            "meta[property=og:price:amount]",
            "meta[itemprop=price]",
            "meta[name=price]",
            "[itemprop=price]"
        )

        return selectors.firstNotNullOfOrNull { selector ->
            val element = document.selectFirst(selector)
            val value = element?.attr("content")?.takeIf { it.isNotBlank() }
                ?: element?.attr("value")?.takeIf { it.isNotBlank() }
                ?: element?.text()?.takeIf { it.isNotBlank() }
            PriceNormalizer.parsePrice(value)
        }
    }

    private fun findVisibleTextPrice(document: Document): Long? {
        document.select("script, style, noscript").remove()
        val text = document.body()?.text().orEmpty()
        val candidates = PRICE_REGEX.findAll(text)
            .mapNotNull { PriceNormalizer.parsePrice(it.value) }
            .filter { it in MIN_REASONABLE_PRICE..MAX_REASONABLE_PRICE }
            .toList()

        return candidates
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Long, Int>> { it.value }.thenByDescending { it.key })
            ?.key
    }

    private fun metaContent(document: Document, selector: String): String? {
        return document.selectFirst(selector)
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.cleanTitle(): String {
        return replace(Regex("\\s+"), " ").trim()
    }

    private data class JsonLdCandidate(
        val title: String? = null,
        val price: Long? = null,
        val imageUrl: String? = null
    ) {
        fun hasAnyValue(): Boolean = title != null || price != null || imageUrl != null
    }

    companion object {
        private val PRICE_REGEX = Regex("""(?:₩|KRW\s*)?\d{1,3}(?:,\d{3})+(?:원)?|\d{4,9}\s*원""")
        private const val MIN_REASONABLE_PRICE = 100L
        private const val MAX_REASONABLE_PRICE = 100_000_000L
    }
}
