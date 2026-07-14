package com.example.neverforgetsaleprice.domain

object DiscountPolicy {
    fun discountPercent(originalPrice: Long, currentPrice: Long): Int {
        if (originalPrice <= 0L || currentPrice >= originalPrice) return 0
        return (((originalPrice - currentPrice) * 100) / originalPrice).toInt()
    }

    fun shouldNotify(
        isActive: Boolean,
        originalPrice: Long,
        currentPrice: Long?,
        lastNotifiedPrice: Long?,
        lastNotifiedDiscountPercent: Int?
    ): Boolean {
        if (!isActive || originalPrice <= 0L || currentPrice == null) return false
        if (currentPrice >= originalPrice) return false

        val discountPercent = discountPercent(originalPrice, currentPrice)
        return currentPrice != lastNotifiedPrice ||
            discountPercent != lastNotifiedDiscountPercent
    }
}
