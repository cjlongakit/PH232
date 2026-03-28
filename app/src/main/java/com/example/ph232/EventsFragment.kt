package com.example.ph232

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class EventsFragment : Fragment() {

    private lateinit var tvMonthYear: TextView
    private lateinit var btnPrevMonth: MaterialButton
    private lateinit var btnNextMonth: MaterialButton
    private lateinit var eventsContainer: LinearLayout
    private lateinit var repository: FirebaseRepository

    private var currentCalendar = Calendar.getInstance()
    private var events = mutableListOf<Event>()
    private var eventsListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_events, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        tvMonthYear = view.findViewById(R.id.tvMonthYear)
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)
        eventsContainer = view.findViewById(R.id.eventsContainer)

        updateMonthYearText()

        // Setup real-time listener for events
        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading events...")
        setupEventsListener()

        // Month navigation
        btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateMonthYearText()
            filterAndDisplayEvents()
        }

        btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateMonthYearText()
            filterAndDisplayEvents()
        }
    }

    private fun setupEventsListener() {
        eventsListener = repository.listenToEvents { eventsList ->
            events.clear()
            events.addAll(eventsList)
            filterAndDisplayEvents()
            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
            }
        }
    }

    private fun filterAndDisplayEvents() {
        eventsContainer.removeAllViews()

        val monthEvents = events.filter { event ->
            isEventInCurrentMonth(event.date)
        }.sortedBy { it.date }

        if (monthEvents.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = "No events this month"
                textSize = 15f
                setTextColor(resources.getColor(R.color.gray_text, null))
                setPadding(0, 32, 0, 32)
                gravity = android.view.Gravity.CENTER
            }
            eventsContainer.addView(emptyView)
        } else {
            for (event in monthEvents) {
                addEventCard(event)
            }
        }
    }

    private fun addEventCard(event: Event) {
        val eventName = event.name.ifEmpty { event.title.ifEmpty { "Untitled Event" } }
        val rawDate = event.date.ifEmpty { "No date" }

        // Format date to friendly format
        val friendlyDate = formatFriendlyDate(rawDate)

        val cardView = LayoutInflater.from(requireContext()).inflate(R.layout.item_dashboard_event, eventsContainer, false)
        cardView.findViewById<TextView>(R.id.tvEventName).text = eventName
        cardView.findViewById<TextView>(R.id.tvEventDate).text = friendlyDate
        cardView.findViewById<TextView>(R.id.tvEventDate).maxLines = 1
        eventsContainer.addView(cardView)
    }

    private fun formatFriendlyDate(dateStr: String): String {
        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        )
        val outputFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        for (fmt in formats) {
            try {
                val date = fmt.parse(dateStr)
                if (date != null) return outputFormat.format(date)
            } catch (_: Exception) {}
        }
        return dateStr
    }

    private fun isEventInCurrentMonth(dateStr: String): Boolean {
        if (dateStr.isEmpty()) return false
        try {
            val formats = listOf(
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()),
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()),
                SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            )
            for (format in formats) {
                try {
                    val date = format.parse(dateStr)
                    if (date != null) {
                        val eventCalendar = Calendar.getInstance()
                        eventCalendar.time = date
                        return eventCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                                eventCalendar.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH)
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return false
    }

    private fun updateMonthYearText() {
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvMonthYear.text = dateFormat.format(currentCalendar.time)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        eventsListener?.remove()
    }
}
