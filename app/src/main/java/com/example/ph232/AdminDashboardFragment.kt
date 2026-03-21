package com.example.ph232

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration

class AdminDashboardFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private lateinit var tvTotalLetters: TextView
    private lateinit var tvTurnedIn: TextView
    private lateinit var tvOnHand: TextView
    private lateinit var tvUpcomingEvents: TextView
    private lateinit var btnAddStudent: MaterialButton
    private lateinit var btnCreateEvent: MaterialButton
    private lateinit var btnAddLetter: MaterialButton
    private lateinit var eventsContainer: LinearLayout
    private lateinit var recentlyContainer: LinearLayout

    private var lettersListener: ListenerRegistration? = null
    private var eventsListener: ListenerRegistration? = null
    private var attendanceListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        // Initialize views
        tvTotalLetters = view.findViewById(R.id.tvTotalLetters)
        tvTurnedIn = view.findViewById(R.id.tvTurnedIn)
        tvOnHand = view.findViewById(R.id.tvOnHand)
        tvUpcomingEvents = view.findViewById(R.id.tvUpcomingEvents)
        btnAddStudent = view.findViewById(R.id.btnAddStudent)
        btnCreateEvent = view.findViewById(R.id.btnCreateEvent)
        btnAddLetter = view.findViewById(R.id.btnAddLetter)
        eventsContainer = view.findViewById(R.id.eventsContainer)
        recentlyContainer = view.findViewById(R.id.recentlyContainer)

        // Setup real-time listeners for dashboard data
        setupDashboardListeners()

        // Button click listeners
        btnAddStudent.setOnClickListener {
            showAddStudentDialog()
        }

        btnCreateEvent.setOnClickListener {
            showCreateEventDialog()
        }

        btnAddLetter.setOnClickListener {
            showAddLetterDialog()
        }
    }

    private fun showAddStudentDialog() {
        val dialog = AddStudentDialog.newInstance()
        dialog.setOnStudentAddedListener { success, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "AddStudentDialog")
    }

    private fun showCreateEventDialog() {
        val dialog = CreateEventDialog.newInstance()
        dialog.setOnEventCreatedListener { success, message, qrCode ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "CreateEventDialog")
    }

    private fun showAddLetterDialog() {
        val dialog = AddLetterDialog.newInstance()
        dialog.setOnLetterAddedListener { success, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "AddLetterDialog")
    }

    private fun setupDashboardListeners() {
        // Listen to letters for real-time updates
        lettersListener = repository.listenToLetters { letters ->
            val total = letters.size
            var turnedIn = 0
            var onHand = 0

            for (letter in letters) {
                when (letter.status.lowercase()) {
                    "turned in", "turned_in", "completed" -> turnedIn++
                    "on hand", "pending" -> onHand++
                }
            }

            tvTotalLetters.text = total.toString()
            tvTurnedIn.text = turnedIn.toString()
            tvOnHand.text = onHand.toString()
        }

        // Listen to events for real-time updates
        eventsListener = repository.listenToUpcomingEvents { events ->
            tvUpcomingEvents.text = events.size.toString()

            // Clear container and add event items
            eventsContainer.removeAllViews()
            for (event in events.take(5)) { // Show only first 5 events
                val eventName = event.name.ifEmpty { event.title.ifEmpty { "Unknown Event" } }
                val eventDate = event.date.ifEmpty { "" }
                addEventToContainer(eventName, eventDate)
            }

            if (events.isEmpty()) {
                val textView = TextView(requireContext()).apply {
                    text = "No upcoming events"
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                }
                eventsContainer.addView(textView)
            }
        }

        // Listen to attendance for real-time updates
        attendanceListener = repository.listenToAttendance { attendanceList ->
            recentlyContainer.removeAllViews()
            for (attendance in attendanceList.take(5)) { // Show only first 5 recent attendance
                val studentName = attendance.studentName.ifEmpty { "PH ${attendance.studentId}" }
                val eventName = attendance.eventName.ifEmpty { "Event" }
                val date = attendance.date
                addRecentAttendanceToContainer(studentName, eventName, date)
            }

            if (attendanceList.isEmpty()) {
                val textView = TextView(requireContext()).apply {
                    text = "No recent attendance"
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                }
                recentlyContainer.addView(textView)
            }
        }
    }

    private fun addEventToContainer(name: String, date: String) {
        val eventView = LayoutInflater.from(requireContext()).inflate(R.layout.item_dashboard_event, eventsContainer, false)
        eventView.findViewById<TextView>(R.id.tvEventName).text = name
        eventView.findViewById<TextView>(R.id.tvEventDate).text = date
        eventsContainer.addView(eventView)
    }

    private fun addRecentAttendanceToContainer(studentName: String, eventName: String, date: String) {
        val attendanceView = LayoutInflater.from(requireContext()).inflate(R.layout.item_recent_attendance, recentlyContainer, false)
        attendanceView.findViewById<TextView>(R.id.tvStudentName).text = studentName
        attendanceView.findViewById<TextView>(R.id.tvAttendanceDetail).text = "Attended $eventName"
        attendanceView.findViewById<TextView>(R.id.tvTime).text = date
        recentlyContainer.addView(attendanceView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lettersListener?.remove()
        eventsListener?.remove()
        attendanceListener?.remove()
    }
}
