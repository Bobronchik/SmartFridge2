package com.example.smartfridge.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smartfridge.data.AppDatabase
import java.util.Calendar

class ExpiryNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            createNotificationChannel()

            val now = System.currentTimeMillis()
            val startOfTomorrow = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val limit = (startOfTomorrow.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val products = AppDatabase.getInstance(applicationContext)
                .fridgeDao()
                .getExpiringBetween(startOfTomorrow.timeInMillis, limit)

            val manager = NotificationManagerCompat.from(applicationContext)
            products.forEach { product ->
                val remaining = remainingTime(product.expiresAt, now)
                val notification = NotificationCompat.Builder(
                    applicationContext,
                    ExpiryNotificationScheduler.CHANNEL_ID
                )
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Скоро истекает срок: ${product.name}")
                    .setContentText("Осталось: $remaining. Используйте продукт вовремя.")
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            "${product.name}: осталось $remaining. Самое время приготовить блюдо."
                        )
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

                try {
                    manager.notify(product.id.toInt(), notification)
                } catch (_: SecurityException) {
                    // Android 13+: уведомления останутся выключенными, пока пользователь
                    // не предоставит POST_NOTIFICATIONS в системном диалоге.
                }
            }

            Result.success()
        } catch (error: Exception) {
            error.printStackTrace()
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ExpiryNotificationScheduler.CHANNEL_ID,
                "Срок годности продуктов",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Напоминания о продуктах, срок годности которых скоро истекает"
            }

            val manager = applicationContext.getSystemService(
                NotificationManager::class.java
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun remainingTime(expiresAt: Long, now: Long): String {
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expiry = Calendar.getInstance().apply {
            timeInMillis = expiresAt
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val days = ((expiry.timeInMillis - start.timeInMillis) / 86_400_000L).toInt()
        return when (days) {
            0 -> "срок истекает сегодня"
            1 -> "остался 1 день"
            else -> "осталось $days дня"
        }
    }
}
