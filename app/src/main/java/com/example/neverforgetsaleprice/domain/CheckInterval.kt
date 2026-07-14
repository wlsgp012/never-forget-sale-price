package com.example.neverforgetsaleprice.domain

object CheckInterval {
    const val DEFAULT_SECONDS = 6L * 60L * 60L
    const val MIN_SECONDS = 1L
    const val MAX_SECONDS = 24L * 60L * 60L

    fun clamp(seconds: Long): Long {
        return seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
    }

    fun format(seconds: Long): String {
        return when {
            seconds % 3600L == 0L -> "${seconds / 3600L}시간"
            seconds % 60L == 0L -> "${seconds / 60L}분"
            else -> "${seconds}초"
        }
    }
}

enum class CheckIntervalUnit(
    val label: String,
    val multiplier: Long
) {
    Seconds("초", 1L),
    Minutes("분", 60L),
    Hours("시간", 3600L)
}
