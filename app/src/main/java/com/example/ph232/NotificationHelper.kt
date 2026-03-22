package com.example.ph232

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    private const val CHANNEL_ID = "ph232_notifications"
    private const val CHANNEL_NAME = "PH232 Notifications"
    private const val CHANNEL_DESC = "Notifications for letters, events, and attendance"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        type: String,
        notificationId: Int
    ) {
        val prefs = context.getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)

        // Check master toggle
        if (!prefs.getBoolean("NOTIFICATIONS_ENABLED", true)) return

        // Check type-specific toggles
        when (type) {
            "attendance" -> {
                if (!prefs.getBoolean("ATTENDANCE_ALERTS", true)) return
            }
            "letter" -> {
                if (!prefs.getBoolean("LETTER_REMINDERS", true)) return
            }
            // "event" notifications always show when master is enabled
        }

        // Determine which activity to open based on user role
        val userRole = prefs.getString("USER_ROLE", "user") ?: "user"
        val targetActivity = when (userRole) {
            "admin" -> AdminDashboardActivity::class.java
            "staff" -> StaffDashboardActivity::class.java
            else -> DashboardActivity::class.java
        }

        val intent = Intent(context, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NOTIFICATION_TYPE", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Choose icon based on type
        val icon = when (type) {
            "letter" -> R.drawable.ic_letters
            "event" -> R.drawable.ic_events
            "attendance" -> R.drawable.ic_qr_scan
            else -> R.drawable.ic_dashboard
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        try {
            // Check permission at runtime for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted - silently ignore
        }
    }
}

