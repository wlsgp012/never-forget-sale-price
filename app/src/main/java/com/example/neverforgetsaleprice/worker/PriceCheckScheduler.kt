package com.example.neverforgetsaleprice.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PriceCheckScheduler {
    private const val WORK_NAME = "price-check-work"

    fun scheduleNow(context: Context) {
        schedule(context, 0L, ExistingWorkPolicy.REPLACE)
    }

    fun scheduleAfter(context: Context, delaySeconds: Long) {
        schedule(context, delaySeconds, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun schedule(context: Context, delaySeconds: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setInitialDelay(delaySeconds.coerceAtLeast(0L), TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            policy,
            request
        )
    }
}
