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
    val staffId: String = "",  // Added for staff attendance tracking
    val date: String = "",
    val time: String = "",
    val scanTime: String = "",  // Added for display in attendance list
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

// Staff Letter model - SEPARATE from admin Letter
// Used in "letters" collection with caseworker field
data class StaffLetter(
    @DocumentId
    val id: String = "",
    val phNumber: String = "",      // Student PH323 ID
    val studentName: String = "",
    val type: String = "",          // Gift, Reply, General, Final Letter, First Letter
    val deadline: String = "",
    val status: String = "PENDING", // PENDING, ON HAND, TURN IN, LATE
    val caseworker: String = "",    // Staff username who assigned this
    val dateCreated: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)

// User model for Firebase authentication
data class User(
    @DocumentId
    val id: String = "",
    val benId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val birthdate: String = "",
    val schoolName: String = "",
    val schoolAddress: String = "",
    val grade: String = "",
    val guardFirstName: String = "",
    val guardLastName: String = "",
    val guardMobile: String = "",
    val guardAddress: String = "",
    val guardOccupation: String = "",
    val guardBirthdate: String = "",
    val guardEmail: String = "",
    val password: String = "",
    val role: String = "beneficiary", // beneficiary, staff, admin
    val status: String = "pending",   // pending, approved, rejected
    @ServerTimestamp
    val createdAt: Date? = null
)

// Active QR Session model - ensures only one QR code is valid at a time
data class QrSession(
    @DocumentId
    val id: String = "",
    val qrCode: String = "",
    val eventId: String = "",
    val eventName: String = "",
    val createdBy: String = "",     // Admin who generated this QR
    val createdByName: String = "", // Admin name for display
    val isActive: Boolean = true,
    val expiresAt: Long = 0,        // Timestamp when QR expires
    val date: String = "",
    val time: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)

// Attendance Log Entry - detailed record with scan metadata
data class AttendanceLog(
    @DocumentId
    val id: String = "",
    val attendanceId: String = "",  // Reference to attendance record
    val studentId: String = "",
    val studentName: String = "",
    val eventId: String = "",
    val eventName: String = "",
    val qrSessionId: String = "",   // Reference to the QR session used
    val qrCode: String = "",
    val scanDate: String = "",
    val scanTime: String = "",
    val timestamp: Long = 0,
    val status: String = "present", // present, late, absent, removed
    val modifiedBy: String = "",    // Admin who last modified
    val modifiedAt: Long = 0,
    val notes: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)

