package com.example.neverforgetsaleprice.domain

object PriceNormalizer {
    fun parsePrice(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw
            .replace(",", "")
            .replace("KRW", "", ignoreCase = true)
            .replace("원", "")
            .replace(Regex("[^0-9.]"), "")
            .trim()

        if (normalized.isBlank()) return null
        val integerPart = normalized.substringBefore(".")
        return integerPart.toLongOrNull()?.takeIf { it > 0L }
    }
}
