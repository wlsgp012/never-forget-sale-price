package com.example.neverforgetsaleprice

import android.app.Application
import androidx.room.Room
import com.example.neverforgetsaleprice.data.AppDatabase
import com.example.neverforgetsaleprice.data.AppMigrations
import com.example.neverforgetsaleprice.data.ProductRepository
import com.example.neverforgetsaleprice.network.ProductMetadataExtractor
import com.example.neverforgetsaleprice.network.ProductPageFetcher
import com.example.neverforgetsaleprice.worker.PriceCheckScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SalePriceApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        PriceCheckScheduler.scheduleNow(this)
    }
}

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "sale-price.db"
    ).addMigrations(AppMigrations.MIGRATION_1_2).build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val repository = ProductRepository(
        productDao = database.productDao(),
        pageFetcher = ProductPageFetcher(okHttpClient),
        metadataExtractor = ProductMetadataExtractor()
    )
}
