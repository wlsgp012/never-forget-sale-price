package com.example.neverforgetsaleprice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceNormalizerTest {
    @Test
    fun parsesKoreanWonFormats() {
        assertEquals(12900L, PriceNormalizer.parsePrice("12,900원"))
        assertEquals(399000L, PriceNormalizer.parsePrice("KRW 399,000"))
        assertEquals(8800L, PriceNormalizer.parsePrice("₩8,800"))
    }

    @Test
    fun returnsNullForMissingPrice() {
        assertNull(PriceNormalizer.parsePrice(""))
        assertNull(PriceNormalizer.parsePrice("가격 없음"))
        assertNull(PriceNormalizer.parsePrice("0원"))
    }
}
