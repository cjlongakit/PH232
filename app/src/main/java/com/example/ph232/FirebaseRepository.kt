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
    private val qrSessionsCollection = db.collection("qr_sessions")
    private val attendanceLogsCollection = db.collection("attendance_logs")

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

    fun deleteStudent(studentId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        studentsCollection.document(studentId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun searchStudents(query: String, onSuccess: (List<Student>) -> Unit, onFailure: (Exception) -> Unit) {
        studentsCollection
            .orderBy("name")
            .get()
            .addOnSuccessListener { snapshot ->
                val students = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Student::class.java)?.copy(id = doc.id)
                }.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.email.contains(query, ignoreCase = true) ||
                    it.section.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true)
                }
                onSuccess(students)
            }
            .addOnFailureListener { e -> onFailure(e) }
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
            .addOnSuccessListener {
                // If a letter was turned in, notify admins and staff
                if (status.lowercase() == "turned_in" || status.lowercase() == "turned in") {
                    lettersCollection.document(letterId).get()
                        .addOnSuccessListener { doc ->
                            val studentName = doc.getString("studentName") ?: "A student"
                            val letterTitle = doc.getString("title") ?: "a letter"
                            notifyAllAdmins(
                                title = "Letter Turned In",
                                message = "$studentName has turned in: $letterTitle",
                                type = "letter",
                                relatedId = letterId
                            )
                            notifyAllStaff(
                                title = "Letter Turned In",
                                message = "$studentName has turned in: $letterTitle",
                                type = "letter",
                                relatedId = letterId
                            )
                        }
                }
                onSuccess()
            }
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

    // ==================== STAFF LETTERS (Separate from Admin Letters) ====================

    private val staffLettersCollection = db.collection("staff_letters")

    fun addStaffLetter(letter: StaffLetter, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val letterData = hashMapOf(
            "phNumber" to letter.phNumber,
            "studentName" to letter.studentName,
            "type" to letter.type,
            "deadline" to letter.deadline,
            "status" to letter.status,
            "caseworker" to letter.caseworker,
            "dateCreated" to currentDate,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        staffLettersCollection.add(letterData)
            .addOnSuccessListener { docRef -> onSuccess(docRef.id) }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun updateStaffLetterStatus(letterId: String, status: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        staffLettersCollection.document(letterId)
            .update("status", status)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToStaffLetters(caseworker: String, onUpdate: (List<StaffLetter>) -> Unit): ListenerRegistration {
        val listener = staffLettersCollection
            .whereEqualTo("caseworker", caseworker)
            .orderBy("deadline", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val letters = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(StaffLetter::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(letters)
            }
        activeListeners.add(listener)
        return listener
    }

    fun listenToStaffLettersByStudent(phNumber: String, onUpdate: (List<StaffLetter>) -> Unit): ListenerRegistration {
        val listener = staffLettersCollection
            .whereEqualTo("phNumber", phNumber)
            .orderBy("deadline", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val letters = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(StaffLetter::class.java)?.copy(id = doc.id)
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
            .addOnSuccessListener { docRef ->
                // Notify all students and staff about the new event
                val eventTitle = event.name.ifEmpty { event.title }
                notifyAllStudents(
                    title = "New Event",
                    message = "A new event has been created: $eventTitle on ${event.date}",
                    type = "event",
                    relatedId = docRef.id
                )
                notifyAllStaff(
                    title = "New Event",
                    message = "A new event has been created: $eventTitle on ${event.date}",
                    type = "event",
                    relatedId = docRef.id
                )
                onSuccess(docRef.id)
            }
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
        val currentDate = Calendar.getInstance()
        currentDate.set(Calendar.HOUR_OF_DAY, 0)
        currentDate.set(Calendar.MINUTE, 0)
        currentDate.set(Calendar.SECOND, 0)
        currentDate.set(Calendar.MILLISECOND, 0)

        val listener = eventsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val allEvents = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Event::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Filter upcoming events manually to handle multiple date formats
                val upcomingEvents = allEvents.filter { event ->
                    val eventDate = parseEventDate(event.date)
                    eventDate != null && !eventDate.before(currentDate.time)
                }.sortedBy { event ->
                    parseEventDate(event.date)?.time ?: Long.MAX_VALUE
                }

                onUpdate(upcomingEvents)
            }
        activeListeners.add(listener)
        return listener
    }

    private fun parseEventDate(dateStr: String): Date? {
        if (dateStr.isEmpty()) return null
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        )
        for (format in formats) {
            try {
                return format.parse(dateStr)
            } catch (_: Exception) { }
        }
        return null
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        eventsCollection.document(eventId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
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

    fun recordStaffAttendance(
        studentId: String,
        studentName: String,
        staffId: String,
        date: String,
        scanTime: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Check if attendance already exists for this student today with this staff
        attendanceCollection
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("staffId", staffId)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    val attendanceData = hashMapOf(
                        "studentId" to studentId,
                        "studentName" to studentName,
                        "staffId" to staffId,
                        "date" to date,
                        "scanTime" to scanTime,
                        "time" to scanTime,
                        "timestamp" to System.currentTimeMillis(),
                        "status" to "present",
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )

                    attendanceCollection.add(attendanceData)
                        .addOnSuccessListener { docRef -> onSuccess(docRef.id) }
                        .addOnFailureListener { e -> onFailure(e) }
                } else {
                    onFailure(Exception("Attendance already recorded for today"))
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

    /**
     * Listen to ALL attendance records with NO Firestore query filters.
     * Filtering is done in-memory by the caller. This avoids any composite index issues.
     */
    fun listenToAllAttendance(onUpdate: (List<Attendance>) -> Unit): ListenerRegistration {
        val listener = attendanceCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val attendanceList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Attendance::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
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

    fun listenToStaffAttendance(staffId: String, date: String, onUpdate: (List<Attendance>) -> Unit): ListenerRegistration {
        val listener = attendanceCollection
            .whereEqualTo("staffId", staffId)
            .whereEqualTo("date", date)
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

    /**
     * Listen to attendance records by date (from the main attendance collection)
     */
    fun listenToAttendanceByDate(date: String, onUpdate: (List<Attendance>) -> Unit): ListenerRegistration {
        val listener = attendanceCollection
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Attendance::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                onUpdate(list)
            }
        activeListeners.add(listener)
        return listener
    }

    /**
     * Listen to attendance records by QR code (eventQR field)
     */
    fun listenToAttendanceByQrCode(qrCode: String, onUpdate: (List<Attendance>) -> Unit): ListenerRegistration {
        val listener = attendanceCollection
            .whereEqualTo("eventQR", qrCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Attendance::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                onUpdate(list)
            }
        activeListeners.add(listener)
        return listener
    }

    /**
     * Delete an attendance record directly from the attendance collection
     * Also removes any matching attendance_logs entry
     */
    fun deleteAttendance(attendanceId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        attendanceCollection.document(attendanceId).delete()
            .addOnSuccessListener {
                // Also try to delete matching log entry
                attendanceLogsCollection
                    .whereEqualTo("attendanceId", attendanceId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) {
                            doc.reference.delete()
                        }
                    }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    /**
     * Update an attendance record directly in the attendance collection
     * Also updates any matching attendance_logs entry
     */
    fun updateAttendanceRecord(
        attendanceId: String,
        status: String,
        notes: String,
        modifiedBy: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val updates = hashMapOf<String, Any>(
            "status" to status,
            "notes" to notes
        )

        attendanceCollection.document(attendanceId)
            .update(updates)
            .addOnSuccessListener {
                // Also update matching log entry
                attendanceLogsCollection
                    .whereEqualTo("attendanceId", attendanceId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) {
                            doc.reference.update(
                                mapOf(
                                    "status" to status,
                                    "notes" to notes,
                                    "modifiedBy" to modifiedBy,
                                    "modifiedAt" to System.currentTimeMillis()
                                )
                            )
                        }
                    }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ==================== NOTIFICATIONS ====================

    fun createNotification(userId: String, title: String, message: String, type: String, relatedId: String = "") {
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

    /**
     * Broadcasts a notification to all users of a specific role.
     * Looks up all users with the given role and creates a notification for each.
     */
    fun broadcastNotification(role: String, title: String, message: String, type: String, relatedId: String = "") {
        db.collection("users")
            .whereEqualTo("role", role)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val userId = doc.id
                    createNotification(userId, title, message, type, relatedId)
                }
            }
        // Also create a role-wide notification for the broadcast listener
        createNotification("all_$role", title, message, type, relatedId)
    }

    /**
     * Notify all students about something (new event, etc.)
     */
    fun notifyAllStudents(title: String, message: String, type: String, relatedId: String = "") {
        broadcastNotification("user", title, message, type, relatedId)
    }

    /**
     * Notify all staff about something
     */
    fun notifyAllStaff(title: String, message: String, type: String, relatedId: String = "") {
        broadcastNotification("staff", title, message, type, relatedId)
    }

    /**
     * Notify all admins about something
     */
    fun notifyAllAdmins(title: String, message: String, type: String, relatedId: String = "") {
        broadcastNotification("admin", title, message, type, relatedId)
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

    // ==================== QR SESSIONS (Centralized QR Management) ====================

    /**
     * Creates a new QR session and deactivates all previous sessions
     * This ensures only ONE QR code is valid at any time
     */
    fun createQrSession(
        qrCode: String,
        eventId: String,
        eventName: String,
        createdBy: String,
        createdByName: String,
        expiresInMinutes: Int = 60,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val expiresAt = System.currentTimeMillis() + (expiresInMinutes * 60 * 1000)

        // First, deactivate all existing active sessions
        deactivateAllQrSessions(
            onSuccess = {
                // Create new session
                val sessionData = hashMapOf(
                    "qrCode" to qrCode,
                    "eventId" to eventId,
                    "eventName" to eventName,
                    "createdBy" to createdBy,
                    "createdByName" to createdByName,
                    "isActive" to true,
                    "expiresAt" to expiresAt,
                    "date" to currentDate,
                    "time" to currentTime,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                qrSessionsCollection.add(sessionData)
                    .addOnSuccessListener { docRef -> onSuccess(docRef.id) }
                    .addOnFailureListener { e -> onFailure(e) }
            },
            onFailure = { e ->
                // Even if deactivation fails, try to create new session
                val sessionData = hashMapOf(
                    "qrCode" to qrCode,
                    "eventId" to eventId,
                    "eventName" to eventName,
                    "createdBy" to createdBy,
                    "createdByName" to createdByName,
                    "isActive" to true,
                    "expiresAt" to expiresAt,
                    "date" to currentDate,
                    "time" to currentTime,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                qrSessionsCollection.add(sessionData)
                    .addOnSuccessListener { docRef -> onSuccess(docRef.id) }
                    .addOnFailureListener { ex -> onFailure(ex) }
            }
        )
    }

    /**
     * Deactivates all active QR sessions
     */
    private fun deactivateAllQrSessions(onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        qrSessionsCollection
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onSuccess()
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.update(doc.reference, "isActive", false)
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    /**
     * Gets the currently active QR session
     */
    fun getActiveQrSession(onSuccess: (QrSession?) -> Unit, onFailure: (Exception) -> Unit) {
        qrSessionsCollection
            .whereEqualTo("isActive", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val session = snapshot.documents.firstOrNull()?.let { doc ->
                    doc.toObject(QrSession::class.java)?.copy(id = doc.id)
                }
                // Check if session has expired
                if (session != null && session.expiresAt > 0 && System.currentTimeMillis() > session.expiresAt) {
                    // Session expired, deactivate it
                    deactivateQrSession(session.id, {}, {})
                    onSuccess(null)
                } else {
                    onSuccess(session)
                }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    /**
     * Validates a scanned QR code against active sessions
     */
    fun validateQrCode(
        scannedQrCode: String,
        onSuccess: (QrSession?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        qrSessionsCollection
            .whereEqualTo("qrCode", scannedQrCode)
            .whereEqualTo("isActive", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val session = snapshot.documents.firstOrNull()?.let { doc ->
                    doc.toObject(QrSession::class.java)?.copy(id = doc.id)
                }
                // Check expiration
                if (session != null && session.expiresAt > 0 && System.currentTimeMillis() > session.expiresAt) {
                    deactivateQrSession(session.id, {}, {})
                    onSuccess(null)
                } else {
                    onSuccess(session)
                }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun deactivateQrSession(sessionId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        qrSessionsCollection.document(sessionId)
            .update("isActive", false)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun listenToActiveQrSession(onUpdate: (QrSession?) -> Unit): ListenerRegistration {
        val listener = qrSessionsCollection
            .whereEqualTo("isActive", true)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val session = snapshot?.documents?.firstOrNull()?.let { doc ->
                    doc.toObject(QrSession::class.java)?.copy(id = doc.id)
                }
                onUpdate(session)
            }
        activeListeners.add(listener)
        return listener
    }

    // ==================== ATTENDANCE LOGS ====================

    /**
     * Records attendance with full logging - creates both attendance record and log entry
     */
    fun recordAttendanceWithLog(
        studentId: String,
        studentName: String,
        qrSession: QrSession,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val timestamp = System.currentTimeMillis()

        // First check if attendance already exists for this student on this QR session
        attendanceLogsCollection
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("qrSessionId", qrSession.id)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // Create attendance record
                    val attendanceData = hashMapOf(
                        "studentId" to studentId,
                        "studentName" to studentName,
                        "eventId" to qrSession.eventId,
                        "eventName" to qrSession.eventName,
                        "eventQR" to qrSession.qrCode,
                        "date" to currentDate,
                        "time" to currentTime,
                        "timestamp" to timestamp,
                        "status" to "present",
                        "notes" to "",
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )

                    attendanceCollection.add(attendanceData)
                        .addOnSuccessListener { attendanceRef ->
                            // Create detailed log entry
                            val logData = hashMapOf(
                                "attendanceId" to attendanceRef.id,
                                "studentId" to studentId,
                                "studentName" to studentName,
                                "eventId" to qrSession.eventId,
                                "eventName" to qrSession.eventName,
                                "qrSessionId" to qrSession.id,
                                "qrCode" to qrSession.qrCode,
                                "scanDate" to currentDate,
                                "scanTime" to currentTime,
                                "timestamp" to timestamp,
                                "status" to "present",
                                "modifiedBy" to "",
                                "modifiedAt" to 0L,
                                "notes" to "",
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )

                            attendanceLogsCollection.add(logData)
                                .addOnSuccessListener { logRef ->
                                    // Notify the student that their attendance was recorded
                                    createNotification(
                                        userId = studentId,
                                        title = "Attendance Recorded",
                                        message = "Your attendance for ${qrSession.eventName} has been recorded.",
                                        type = "attendance",
                                        relatedId = attendanceRef.id
                                    )
                                    // Notify all admins
                                    notifyAllAdmins(
                                        title = "New Attendance",
                                        message = "$studentName attended ${qrSession.eventName}",
                                        type = "attendance",
                                        relatedId = attendanceRef.id
                                    )
                                    // Notify all staff
                                    notifyAllStaff(
                                        title = "New Attendance",
                                        message = "$studentName attended ${qrSession.eventName}",
                                        type = "attendance",
                                        relatedId = attendanceRef.id
                                    )
                                    onSuccess(attendanceRef.id)
                                }
                                .addOnFailureListener { e ->
                                    // Log failed but attendance recorded
                                    onSuccess(attendanceRef.id)
                                }
                        }
                        .addOnFailureListener { e -> onFailure(e) }
                } else {
                    onFailure(Exception("Attendance already recorded for this session"))
                }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    /**
     * Listen to all attendance logs for admin management
     */
    fun listenToAttendanceLogs(onUpdate: (List<AttendanceLog>) -> Unit): ListenerRegistration {
        val listener = attendanceLogsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val logs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AttendanceLog::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(logs)
            }
        activeListeners.add(listener)
        return listener
    }

    /**
     * Listen to attendance logs by date
     */
    fun listenToAttendanceLogsByDate(date: String, onUpdate: (List<AttendanceLog>) -> Unit): ListenerRegistration {
        val listener = attendanceLogsCollection
            .whereEqualTo("scanDate", date)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val logs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AttendanceLog::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(logs)
            }
        activeListeners.add(listener)
        return listener
    }

    /**
     * Listen to attendance logs by event
     */
    fun listenToAttendanceLogsByEvent(eventId: String, onUpdate: (List<AttendanceLog>) -> Unit): ListenerRegistration {
        val listener = attendanceLogsCollection
            .whereEqualTo("eventId", eventId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val logs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AttendanceLog::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(logs)
            }
        activeListeners.add(listener)
        return listener
    }

    /**
     * Update attendance log status (edit)
     */
    fun updateAttendanceLog(
        logId: String,
        status: String,
        notes: String,
        modifiedBy: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val updates = hashMapOf<String, Any>(
            "status" to status,
            "notes" to notes,
            "modifiedBy" to modifiedBy,
            "modifiedAt" to System.currentTimeMillis()
        )

        attendanceLogsCollection.document(logId)
            .update(updates)
            .addOnSuccessListener {
                // Also update the main attendance record
                attendanceLogsCollection.document(logId).get()
                    .addOnSuccessListener { doc ->
                        val attendanceId = doc.getString("attendanceId") ?: ""
                        if (attendanceId.isNotEmpty()) {
                            attendanceCollection.document(attendanceId)
                                .update(mapOf("status" to status, "notes" to notes))
                        }
                    }
                onSuccess()
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    /**
     * Delete attendance log (remove student from attendance)
     */
    fun deleteAttendanceLog(
        logId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // First get the attendance ID to delete from main collection too
        attendanceLogsCollection.document(logId).get()
            .addOnSuccessListener { doc ->
                val attendanceId = doc.getString("attendanceId") ?: ""

                // Delete from logs collection
                attendanceLogsCollection.document(logId).delete()
                    .addOnSuccessListener {
                        // Delete from main attendance collection
                        if (attendanceId.isNotEmpty()) {
                            attendanceCollection.document(attendanceId).delete()
                        }
                        onSuccess()
                    }
                    .addOnFailureListener { e -> onFailure(e) }
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
