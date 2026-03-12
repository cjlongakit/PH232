package com.example.ph232

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

/**
 * Firebase Repository for syncing data between users and admin
 * This class handles all database operations for attendance, letters, events, and students
 */
class FirebaseRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()

    // Collection references
    private val studentsCollection = db.collection("students")
    private val lettersCollection = db.collection("letters")
    private val eventsCollection = db.collection("events")
    private val attendanceCollection = db.collection("attendance")
    private val notificationsCollection = db.collection("notifications")
    private val settingsCollection = db.collection("user_settings")
    private val adminsCollection = db.collection("admins")

    // Active listeners for real-time updates
    private val activeListeners = mutableListOf<ListenerRegistration>()

    companion object {
        @Volatile
        private var INSTANCE: FirebaseRepository? = null

        fun getInstance(): FirebaseRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseRepository().also { INSTANCE = it }
            }
        }
    }

    // ==================== STUDENTS ====================

    fun addStudent(student: Student, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val studentData = hashMapOf(
            "name" to student.name,
            "email" to student.email,
            "section" to student.section,
            "birthday" to student.birthday,
            "year" to student.year,
            "status" to student.status,
            "phoneNumber" to student.phoneNumber,
            "address" to student.address,
            "profileImageUrl" to student.profileImageUrl,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        studentsCollection.add(studentData)
            .addOnSuccessListener { docRef -> onSuccess(docRef.id) }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun updateStudent(studentId: String, updates: Map<String, Any>, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val updatesWithTimestamp = updates.toMutableMap()
        updatesWithTimestamp["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

        studentsCollection.document(studentId)
            .update(updatesWithTimestamp)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getStudentById(studentId: String, onSuccess: (Student?) -> Unit, onFailure: (Exception) -> Unit) {
        studentsCollection.document(studentId)
            .get()
            .addOnSuccessListener { doc ->
                val student = doc.toObject(Student::class.java)?.copy(id = doc.id)
                onSuccess(student)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToStudents(onUpdate: (List<Student>) -> Unit): ListenerRegistration {
        val listener = studentsCollection
            .orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val students = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Student::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(students)
            }
        activeListeners.add(listener)
        return listener
    }

    // ==================== LETTERS ====================

    fun addLetter(letter: Letter, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val letterData = hashMapOf(
            "title" to letter.title,
            "name" to letter.name,
            "description" to letter.description,
            "deadline" to letter.deadline,
            "status" to letter.status,
            "dateCreated" to currentDate,
            "isCompleted" to letter.isCompleted,
            "studentId" to letter.studentId,
            "studentName" to letter.studentName,
            "assignedBy" to letter.assignedBy,
            "turnedInDate" to letter.turnedInDate,
            "notes" to letter.notes,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        lettersCollection.add(letterData)
            .addOnSuccessListener { docRef ->
                // Create notification for the student
                if (letter.studentId.isNotEmpty()) {
                    createNotification(
                        userId = letter.studentId,
                        title = "New Letter Assigned",
                        message = "You have been assigned a new letter: ${letter.title}",
                        type = "letter",
                        relatedId = docRef.id
                    )
                }
                onSuccess(docRef.id)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun updateLetterStatus(letterId: String, status: String, isCompleted: Boolean, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "isCompleted" to isCompleted,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        if (status.lowercase() == "turned_in" || status.lowercase() == "turned in") {
            updates["turnedInDate"] = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }

        lettersCollection.document(letterId)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToLetters(onUpdate: (List<Letter>) -> Unit): ListenerRegistration {
        val listener = lettersCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val letters = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Letter::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(letters)
            }
        activeListeners.add(listener)
        return listener
    }

    fun listenToLettersByStudent(studentId: String, onUpdate: (List<Letter>) -> Unit): ListenerRegistration {
        val listener = lettersCollection
            .whereEqualTo("studentId", studentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val letters = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Letter::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(letters)
            }
        activeListeners.add(listener)
        return listener
    }

    // ==================== EVENTS ====================

    fun addEvent(event: Event, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        // Generate unique QR code for the event
        val qrCode = "EVENT_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

        val eventData = hashMapOf(
            "name" to event.name,
            "title" to event.title,
            "subtitle" to event.subtitle,
            "description" to event.description,
            "date" to event.date,
            "time" to event.time,
            "subTime" to event.subTime,
            "location" to event.location,
            "qrCode" to qrCode,
            "day" to event.day,
            "createdBy" to event.createdBy,
            "isActive" to true,
            "maxAttendees" to event.maxAttendees,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        eventsCollection.add(eventData)
            .addOnSuccessListener { docRef -> onSuccess(docRef.id) }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun updateEvent(eventId: String, updates: Map<String, Any>, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val updatesWithTimestamp = updates.toMutableMap()
        updatesWithTimestamp["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

        eventsCollection.document(eventId)
            .update(updatesWithTimestamp)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getEventByQRCode(qrCode: String, onSuccess: (Event?) -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection
            .whereEqualTo("qrCode", qrCode)
            .get()
            .addOnSuccessListener { snapshot ->
                val event = snapshot.documents.firstOrNull()?.let { doc ->
                    doc.toObject(Event::class.java)?.copy(id = doc.id)
                }
                onSuccess(event)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToEvents(onUpdate: (List<Event>) -> Unit): ListenerRegistration {
        val listener = eventsCollection
            .orderBy("date", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Event::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(events)
            }
        activeListeners.add(listener)
        return listener
    }

    fun listenToUpcomingEvents(onUpdate: (List<Event>) -> Unit): ListenerRegistration {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val listener = eventsCollection
            .whereGreaterThanOrEqualTo("date", currentDate)
            .orderBy("date", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Event::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(events)
            }
        activeListeners.add(listener)
        return listener
    }

    // ==================== ATTENDANCE ====================

    fun recordAttendance(
        studentId: String,
        studentName: String,
        eventId: String,
        eventName: String,
        eventQR: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        // First check if attendance already exists
        attendanceCollection
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("eventQR", eventQR)
            .whereEqualTo("date", currentDate)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // No existing attendance, create new record
                    val attendanceData = hashMapOf(
                        "studentId" to studentId,
                        "studentName" to studentName,
                        "eventId" to eventId,
                        "eventName" to eventName,
                        "eventQR" to eventQR,
                        "date" to currentDate,
                        "time" to currentTime,
                        "timestamp" to System.currentTimeMillis(),
                        "status" to "present",
                        "notes" to "",
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )

                    attendanceCollection.add(attendanceData)
                        .addOnSuccessListener { docRef ->
                            // Create notification for admin
                            createNotification(
                                userId = "admin",
                                title = "New Attendance",
                                message = "$studentName attended $eventName",
                                type = "attendance",
                                relatedId = docRef.id
                            )
                            onSuccess(docRef.id)
                        }
                        .addOnFailureListener { e -> onFailure(e) }
                } else {
                    onFailure(Exception("Attendance already recorded for this event today"))
                }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToAttendance(onUpdate: (List<Attendance>) -> Unit): ListenerRegistration {
        val listener = attendanceCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val attendanceList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Attendance::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(attendanceList)
            }
        activeListeners.add(listener)
        return listener
    }

    fun listenToAttendanceByStudent(studentId: String, onUpdate: (List<Attendance>) -> Unit): ListenerRegistration {
        val listener = attendanceCollection
            .whereEqualTo("studentId", studentId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val attendanceList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Attendance::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(attendanceList)
            }
        activeListeners.add(listener)
        return listener
    }

    fun listenToAttendanceByEvent(eventId: String, onUpdate: (List<Attendance>) -> Unit): ListenerRegistration {
        val listener = attendanceCollection
            .whereEqualTo("eventId", eventId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val attendanceList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Attendance::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(attendanceList)
            }
        activeListeners.add(listener)
        return listener
    }

    fun getAttendanceStats(studentId: String, onSuccess: (Int, Int, Int) -> Unit, onFailure: (Exception) -> Unit) {
        attendanceCollection
            .whereEqualTo("studentId", studentId)
            .get()
            .addOnSuccessListener { snapshot ->
                var present = 0
                var late = 0
                var absent = 0

                for (doc in snapshot.documents) {
                    when (doc.getString("status")?.lowercase()) {
                        "present" -> present++
                        "late" -> late++
                        "absent" -> absent++
                    }
                }

                onSuccess(present, late, absent)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ==================== NOTIFICATIONS ====================

    private fun createNotification(userId: String, title: String, message: String, type: String, relatedId: String = "") {
        val notificationData = hashMapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "type" to type,
            "isRead" to false,
            "relatedId" to relatedId,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        notificationsCollection.add(notificationData)
    }

    fun listenToNotifications(userId: String, onUpdate: (List<Notification>) -> Unit): ListenerRegistration {
        val listener = notificationsCollection
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Notification::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(notifications)
            }
        activeListeners.add(listener)
        return listener
    }

    fun markNotificationAsRead(notificationId: String) {
        notificationsCollection.document(notificationId)
            .update("isRead", true)
    }

    // ==================== USER SETTINGS ====================

    fun saveUserSettings(settings: UserSettings, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val settingsData = hashMapOf(
            "userId" to settings.userId,
            "notificationsEnabled" to settings.notificationsEnabled,
            "emailNotifications" to settings.emailNotifications,
            "darkModeEnabled" to settings.darkModeEnabled,
            "language" to settings.language,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        settingsCollection.document(settings.userId)
            .set(settingsData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun getUserSettings(userId: String, onSuccess: (UserSettings?) -> Unit, onFailure: (Exception) -> Unit) {
        settingsCollection.document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val settings = doc.toObject(UserSettings::class.java)?.copy(id = doc.id)
                onSuccess(settings)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ==================== ADMIN ====================

    fun getAdminById(adminId: String, onSuccess: (Admin?) -> Unit, onFailure: (Exception) -> Unit) {
        adminsCollection.document(adminId)
            .get()
            .addOnSuccessListener { doc ->
                val admin = doc.toObject(Admin::class.java)?.copy(id = doc.id)
                onSuccess(admin)
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ==================== CLEANUP ====================

    fun removeAllListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    fun removeListener(listener: ListenerRegistration) {
        listener.remove()
        activeListeners.remove(listener)
    }
}

