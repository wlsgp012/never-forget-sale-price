package com.example.neverforgetsaleprice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountPolicyTest {
    @Test
    fun calculatesDiscountPercent() {
        assertEquals(25, DiscountPolicy.discountPercent(20_000L, 15_000L))
        assertEquals(0, DiscountPolicy.discountPercent(20_000L, 20_000L))
    }

    @Test
    fun notifiesOnlyForChangedSaleState() {
        assertTrue(
            DiscountPolicy.shouldNotify(
                isActive = true,
                originalPrice = 20_000L,
                currentPrice = 15_000L,
                lastNotifiedPrice = null,
                lastNotifiedDiscountPercent = null
            )
        )

        assertFalse(
            DiscountPolicy.shouldNotify(
                isActive = true,
                originalPrice = 20_000L,
                currentPrice = 15_000L,
                lastNotifiedPrice = 15_000L,
                lastNotifiedDiscountPercent = 25
            )
        )

        assertFalse(
            DiscountPolicy.shouldNotify(
                isActive = true,
                originalPrice = 20_000L,
                currentPrice = 20_000L,
                lastNotifiedPrice = null,
                lastNotifiedDiscountPercent = null
            )
        )
    }
}
