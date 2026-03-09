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
import com.google.firebase.firestore.FirebaseFirestore

class AdminDashboardFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvTotalLetters: TextView
    private lateinit var tvTurnedIn: TextView
    private lateinit var tvOnHand: TextView
    private lateinit var tvUpcomingEvents: TextView
    private lateinit var btnAddStudent: MaterialButton
    private lateinit var btnCreateEvent: MaterialButton
    private lateinit var eventsContainer: LinearLayout
    private lateinit var recentlyContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        // Initialize views
        tvTotalLetters = view.findViewById(R.id.tvTotalLetters)
        tvTurnedIn = view.findViewById(R.id.tvTurnedIn)
        tvOnHand = view.findViewById(R.id.tvOnHand)
        tvUpcomingEvents = view.findViewById(R.id.tvUpcomingEvents)
        btnAddStudent = view.findViewById(R.id.btnAddStudent)
        btnCreateEvent = view.findViewById(R.id.btnCreateEvent)
        eventsContainer = view.findViewById(R.id.eventsContainer)
        recentlyContainer = view.findViewById(R.id.recentlyContainer)

        // Load data from Firestore
        loadDashboardData()

        // Button click listeners
        btnAddStudent.setOnClickListener {
            Toast.makeText(requireContext(), "Add Student feature coming soon", Toast.LENGTH_SHORT).show()
        }

        btnCreateEvent.setOnClickListener {
            Toast.makeText(requireContext(), "Create Event feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDashboardData() {
        // Load letters data with status counts
        db.collection("letters")
            .get()
            .addOnSuccessListener { result ->
                val total = result.size()
                var turnedIn = 0
                var onHand = 0

                for (document in result) {
                    val status = document.getString("status") ?: ""
                    when (status.lowercase()) {
                        "turned in", "completed" -> turnedIn++
                        "on hand", "pending" -> onHand++
                    }
                }

                tvTotalLetters.text = total.toString()
                tvTurnedIn.text = turnedIn.toString()
                tvOnHand.text = onHand.toString()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading letters: ${e.message}", Toast.LENGTH_SHORT).show()
                tvTotalLetters.text = "0"
                tvTurnedIn.text = "0"
                tvOnHand.text = "0"
            }

        // Load events data
        db.collection("events")
            .get()
            .addOnSuccessListener { result ->
                tvUpcomingEvents.text = result.size().toString()

                // Clear container and add event items
                eventsContainer.removeAllViews()
                for (document in result) {
                    val eventName = document.getString("name") ?: "Unknown Event"
                    val eventDate = document.getString("date") ?: ""
                    addEventToContainer(eventName, eventDate)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading events: ${e.message}", Toast.LENGTH_SHORT).show()
                tvUpcomingEvents.text = "0"
            }

        // Load recent attendance data
        db.collection("attendance")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { result ->
                recentlyContainer.removeAllViews()
                for (document in result) {
                    val studentId = document.getString("studentId") ?: "Unknown"
                    val date = document.getString("date") ?: ""
                    addRecentAttendanceToContainer(studentId, date)
                }
            }
            .addOnFailureListener { e ->
                // Silent fail for recent attendance
            }
    }

    private fun addEventToContainer(name: String, date: String) {
        val textView = TextView(requireContext()).apply {
            text = "$name - $date"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        eventsContainer.addView(textView)
    }

    private fun addRecentAttendanceToContainer(studentId: String, date: String) {
        val textView = TextView(requireContext()).apply {
            text = "PH $studentId attended on $date"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        recentlyContainer.addView(textView)
    }
}
