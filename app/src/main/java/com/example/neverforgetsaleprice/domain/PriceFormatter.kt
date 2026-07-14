package com.example.neverforgetsaleprice.domain

import java.text.NumberFormat
import java.util.Locale

object PriceFormatter {
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    fun format(price: Long?): String {
        return price?.let { "${numberFormat.format(it)}원" } ?: "-"
    }
}
