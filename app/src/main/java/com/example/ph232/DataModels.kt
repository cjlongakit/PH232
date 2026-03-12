package com.example.ph232

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Student(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val section: String = "",
    val birthday: String = "",
    val year: String = "",
    val status: String = "active",
    val phoneNumber: String = "",
    val address: String = "",
    val profileImageUrl: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

data class Letter(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val name: String = "",
    val description: String = "",
    val deadline: String = "",
    val status: String = "pending", // pending, turned_in, completed
    val dateCreated: String = "",
    val isCompleted: Boolean = false,
    val studentId: String = "",
    val studentName: String = "",
    val assignedBy: String = "", // admin who assigned this letter
    val turnedInDate: String = "",
    val notes: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

data class Event(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val date: String = "",
    val time: String = "",
    val subTime: String = "",
    val location: String = "",
    val qrCode: String = "",
    val day: Int = 0,
    val createdBy: String = "", // admin who created this event
    val isActive: Boolean = true,
    val maxAttendees: Int = 0,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

data class Attendance(
    @DocumentId
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val eventId: String = "",
    val eventName: String = "",
    val eventQR: String = "",
    val date: String = "",
    val time: String = "",
    val timestamp: Long = 0,
    val status: String = "present", // present, late, absent
    val notes: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)

// Notification model for real-time updates
data class Notification(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "", // letter, event, attendance, announcement
    val isRead: Boolean = false,
    val relatedId: String = "", // ID of the related document (letter, event, etc.)
    @ServerTimestamp
    val createdAt: Date? = null
)

// User Settings model
data class UserSettings(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val notificationsEnabled: Boolean = true,
    val emailNotifications: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val language: String = "en",
    @ServerTimestamp
    val updatedAt: Date? = null
)

// Admin model
data class Admin(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "admin", // admin, super_admin
    val profileImageUrl: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)
