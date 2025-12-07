package com.chukotnet.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "payment_reminder_channel"

        // 1. Создаем канал уведомлений (нужно для Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания об оплате",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для напоминаний об оплате интернета"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Интент для открытия приложения при клике на уведомление
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, appIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Создаем само уведомление
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Потом заменим на вашу иконку
            .setContentTitle("Напоминание об оплате")
            .setContentText("Не забудьте оплатить интернет до начала месяца!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // 4. Показываем
        notificationManager.notify(1001, notification)
    }
}