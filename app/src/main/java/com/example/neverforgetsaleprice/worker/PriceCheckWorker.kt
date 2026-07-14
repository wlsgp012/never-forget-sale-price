package com.example.neverforgetsaleprice.worker

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.neverforgetsaleprice.data.AppDatabase
import com.example.neverforgetsaleprice.data.AppMigrations
import com.example.neverforgetsaleprice.data.ProductRepository
import com.example.neverforgetsaleprice.network.ProductMetadataExtractor
import com.example.neverforgetsaleprice.network.ProductPageFetcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PriceCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "sale-price.db"
        ).addMigrations(AppMigrations.MIGRATION_1_2).build()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val repository = ProductRepository(
            productDao = database.productDao(),
            pageFetcher = ProductPageFetcher(client),
            metadataExtractor = ProductMetadataExtractor()
        )
        val notifier = PriceNotificationHelper(applicationContext)

        return runCatching {
            repository.checkActiveProducts { product, discountPercent, currentPrice ->
                notifier.notifyDiscount(product, discountPercent, currentPrice)
            }
            val nextDelaySeconds = repository.nextDelaySeconds()
            database.close()
            PriceCheckScheduler.scheduleAfter(applicationContext, nextDelaySeconds)
            Result.success()
        }.getOrElse {
            database.close()
            Result.retry()
        }
    }
}
