package com.example.neverforgetsaleprice.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.example.neverforgetsaleprice.domain.CheckInterval
import com.example.neverforgetsaleprice.domain.CheckStatus

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val name: String,
    val originalPrice: Long,
    @ColumnInfo(defaultValue = "21600")
    val checkIntervalSeconds: Long = CheckInterval.DEFAULT_SECONDS,
    val imageUrl: String?,
    val isActive: Boolean = true,
    val lastCheckedPrice: Long?,
    val lastCheckedAtMillis: Long?,
    val lastCheckStatus: CheckStatus = CheckStatus.NeverChecked,
    val lastCheckError: String?,
    val lastNotifiedPrice: Long?,
    val lastNotifiedDiscountPercent: Int?,
    val lastNotifiedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
