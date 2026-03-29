package com.example.ph232

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.ListenerRegistration
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
    private lateinit var repository: FirebaseRepository

    private var lettersListener: ListenerRegistration? = null
    private var eventsListener: ListenerRegistration? = null
    private var studentId: String = ""
    private var progressManager: ProgressManager? = null
    private var dataLoadedCount = 0

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
        repository = FirebaseRepository.getInstance()
        studentId = sharedPreferences.getString("USER_PH", "") ?: ""

        // Load user greeting
        loadUserGreeting()

        // Show loading while data loads
        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading dashboard...")
        dataLoadedCount = 0

        // Setup real-time listeners
        setupDashboardListeners()
    }

    private fun onDataLoaded() {
        dataLoadedCount++
        if (dataLoadedCount >= 2) { // letters + events
            progressManager?.dismiss()
        }
    }

    private fun loadUserGreeting() {
        val userName = sharedPreferences.getString("USER_NAME", "User") ?: "User"
        val firstName = userName.split(" ").firstOrNull() ?: userName
        tvGreeting.text = "Hello, $firstName"
    }

    private fun setupDashboardListeners() {
        // Listen to letters for real-time updates
        lettersListener = repository.listenToLetters { letters ->
            // Filter letters for this student
            val studentLetters = if (studentId.isNotEmpty()) {
                letters.filter { it.studentId == studentId || it.studentId.isEmpty() }
            } else {
                letters
            }

            var pendingCount = 0
            var turnedInCount = 0

            for (letter in studentLetters) {
                val status = letter.status.lowercase()
                when {
                    status == "turned in" || status == "turned_in" || status == "completed" || letter.isCompleted -> turnedInCount++
                    status == "pending" || status == "on hand" -> pendingCount++
                    else -> pendingCount++ // Default to pending
                }
            }

            tvCurrentStatus.text = when (pendingCount) {
                0 -> "No pending letters"
                1 -> "1 letter pending"
                else -> "$pendingCount letters pending"
            }
            tvTurnedInCount.text = turnedInCount.toString()
            onDataLoaded()
        }

        // Listen to upcoming events for real-time updates
        eventsListener = repository.listenToUpcomingEvents { events ->
            tvUpcomingEventsCount.text = events.size.toString()

            // Get the next upcoming event
            if (events.isNotEmpty()) {
                val nextEvent = events.first()
                val eventDate = nextEvent.date
                val eventName = nextEvent.name.ifEmpty { nextEvent.title.ifEmpty { "Event" } }

                // Format the date for display
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
                    val date = inputFormat.parse(eventDate)
                    val formattedDate = if (date != null) outputFormat.format(date) else eventDate
                    tvNextEventDetails.text = "$formattedDate - $eventName"
                    eventStatusIndicator.visibility = View.VISIBLE
                } catch (e: Exception) {
                    tvNextEventDetails.text = "$eventDate - $eventName"
                    eventStatusIndicator.visibility = View.VISIBLE
                }
            } else {
                tvNextEventDetails.text = "No upcoming events"
                eventStatusIndicator.visibility = View.GONE
            }
            onDataLoaded()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        lettersListener?.remove()
        eventsListener?.remove()
    }
}
