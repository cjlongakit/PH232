package com.example.ph232

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private lateinit var tvGreeting: TextView
    private lateinit var tvCurrentStatus: TextView
    private lateinit var tvTurnedInCount: TextView
    private lateinit var tvUpcomingEventsCount: TextView
    private lateinit var tvNextEventDetails: TextView
    private lateinit var eventStatusIndicator: View
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvCurrentStatus = view.findViewById(R.id.tvCurrentStatus)
        tvTurnedInCount = view.findViewById(R.id.tvTurnedInCount)
        tvUpcomingEventsCount = view.findViewById(R.id.tvUpcomingEventsCount)
        tvNextEventDetails = view.findViewById(R.id.tvNextEventDetails)
        eventStatusIndicator = view.findViewById(R.id.eventStatusIndicator)

        sharedPreferences = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        db = FirebaseFirestore.getInstance()

        // Load user data and dashboard stats
        loadUserGreeting()
        loadDashboardStats()
        loadNextEvent()
    }

    private fun loadUserGreeting() {
        val userName = sharedPreferences.getString("USER_NAME", "User") ?: "User"
        val firstName = userName.split(" ").firstOrNull() ?: userName
        tvGreeting.text = "Hello, $firstName!!"
    }

    private fun loadDashboardStats() {
        val studentId = sharedPreferences.getString("USER_PH", "") ?: ""

        // Load letters count (pending and turned in)
        if (studentId.isNotEmpty()) {
            // Get pending letters count
            db.collection("letters")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener { result ->
                    val pendingCount = result.size()
                    tvCurrentStatus.text = "$pendingCount Letter Pending"
                }
                .addOnFailureListener {
                    tvCurrentStatus.text = "0 Letter Pending"
                }

            // Get turned in letters count
            db.collection("letters")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("status", "turned_in")
                .get()
                .addOnSuccessListener { result ->
                    tvTurnedInCount.text = result.size().toString()
                }
                .addOnFailureListener {
                    tvTurnedInCount.text = "0"
                }
        }

        // Get upcoming events count
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.collection("events")
            .whereGreaterThanOrEqualTo("date", currentDate)
            .get()
            .addOnSuccessListener { result ->
                tvUpcomingEventsCount.text = result.size().toString()
            }
            .addOnFailureListener {
                tvUpcomingEventsCount.text = "0"
            }
    }

    private fun loadNextEvent() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        db.collection("events")
            .whereGreaterThanOrEqualTo("date", currentDate)
            .orderBy("date")
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val event = result.documents[0]
                    val eventDate = event.getString("date") ?: ""
                    val eventName = event.getString("name") ?: event.getString("title") ?: "Event"

                    // Format the date for display
                    try {
                        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val outputFormat = SimpleDateFormat("EEEE MMM dd, yyyy", Locale.getDefault())
                        val date = inputFormat.parse(eventDate)
                        val formattedDate = if (date != null) outputFormat.format(date) else eventDate
                        tvNextEventDetails.text = "$formattedDate: $eventName"
                        eventStatusIndicator.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        tvNextEventDetails.text = "$eventDate: $eventName"
                        eventStatusIndicator.visibility = View.VISIBLE
                    }
                } else {
                    tvNextEventDetails.text = "No upcoming events"
                    eventStatusIndicator.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                tvNextEventDetails.text = "No upcoming events"
                eventStatusIndicator.visibility = View.GONE
            }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when fragment becomes visible
        loadDashboardStats()
        loadNextEvent()
    }
}
