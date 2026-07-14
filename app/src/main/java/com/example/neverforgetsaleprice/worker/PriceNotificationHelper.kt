package com.example.neverforgetsaleprice.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.neverforgetsaleprice.MainActivity
import com.example.neverforgetsaleprice.R
import com.example.neverforgetsaleprice.data.ProductEntity
import com.example.neverforgetsaleprice.domain.PriceFormatter

class PriceNotificationHelper(
    private val context: Context
) {
    fun notifyDiscount(product: ProductEntity, discountPercent: Int, currentPrice: Long): Boolean {
        if (!canPostNotifications()) return false

        createChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PRODUCT_ID, product.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            product.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(product.name)
            .setContentText("${discountPercent}% 할인, ${PriceFormatter.format(currentPrice)}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(product.id.toInt(), notification)
        return true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sale price alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CHANNEL_ID = "sale_price_alerts"
    }
}
