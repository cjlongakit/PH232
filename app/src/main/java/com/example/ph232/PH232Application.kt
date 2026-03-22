package com.example.ph232

import android.app.Application
import android.content.Context
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class PH232Application : Application() {

    private var notificationListener: ListenerRegistration? = null
    private var currentUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }

    /**
     * Starts listening for new notifications for the given user.
     * Idempotent — won't re-register if already listening for the same user.
     */
    fun startNotificationListener(userId: String) {
        if (userId.isEmpty()) return
        if (currentUserId == userId && notificationListener != null) return

        // Stop any existing listener
        stopNotificationListener()

        currentUserId = userId
        val prefs = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)

        // Get the "last seen" timestamp to avoid re-showing old notifications on restart
        val lastSeenTimestamp = prefs.getLong("LAST_NOTIFICATION_TIMESTAMP", System.currentTimeMillis())
        // Save current time as baseline for future opens
        prefs.edit().putLong("LAST_NOTIFICATION_TIMESTAMP", System.currentTimeMillis()).apply()

        val db = FirebaseFirestore.getInstance()

        notificationListener = db.collection("notifications")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                for (change in snapshots.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val isRead = doc.getBoolean("isRead") ?: false
                        if (isRead) continue

                        // Check timestamp to skip old unread notifications
                        val createdAt = doc.getTimestamp("createdAt")
                        if (createdAt != null) {
                            val notifTime = createdAt.toDate().time
                            if (notifTime < lastSeenTimestamp) continue
                        }

                        val title = doc.getString("title") ?: "PH232"
                        val message = doc.getString("message") ?: ""
                        val type = doc.getString("type") ?: ""
                        val notifId = doc.id.hashCode()

                        // Show the notification
                        NotificationHelper.showNotification(
                            context = this@PH232Application,
                            title = title,
                            message = message,
                            type = type,
                            notificationId = notifId
                        )

                        // Mark as read so it doesn't fire again
                        FirebaseRepository.getInstance().markNotificationAsRead(doc.id)
                    }
                }
            }

        // Also listen for notifications sent to "all" users (broadcast)
        listenToBroadcastNotifications(lastSeenTimestamp)
    }

    private var broadcastListener: ListenerRegistration? = null

    private fun listenToBroadcastNotifications(lastSeenTimestamp: Long) {
        broadcastListener?.remove()

        val db = FirebaseFirestore.getInstance()
        val prefs = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val userRole = prefs.getString("USER_ROLE", "user") ?: "user"

        // Listen for notifications targeted at "all" or role-specific (e.g. "all_students")
        broadcastListener = db.collection("notifications")
            .whereEqualTo("userId", "all_$userRole")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                for (change in snapshots.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val createdAt = doc.getTimestamp("createdAt")
                        if (createdAt != null && createdAt.toDate().time < lastSeenTimestamp) continue

                        val title = doc.getString("title") ?: "PH232"
                        val message = doc.getString("message") ?: ""
                        val type = doc.getString("type") ?: ""

                        NotificationHelper.showNotification(
                            context = this@PH232Application,
                            title = title,
                            message = message,
                            type = type,
                            notificationId = doc.id.hashCode()
                        )
                    }
                }
            }
    }

    fun stopNotificationListener() {
        notificationListener?.remove()
        notificationListener = null
        broadcastListener?.remove()
        broadcastListener = null
        currentUserId = null
    }
}

