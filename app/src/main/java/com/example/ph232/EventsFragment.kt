package com.example.ph232

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class EventsFragment : Fragment() {

    private lateinit var tvMonthYear: TextView
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var rvCalendar: RecyclerView
    private lateinit var rvUpcomingEvents: RecyclerView
    private lateinit var repository: FirebaseRepository

    private var currentCalendar = Calendar.getInstance()
    private lateinit var calendarAdapter: CalendarAdapter
    private var events = mutableListOf<Event>()
    private var eventDays = mutableSetOf<Int>()
    private var eventsListener: ListenerRegistration? = null

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
        rvCalendar = view.findViewById(R.id.rvCalendar)
        rvUpcomingEvents = view.findViewById(R.id.rvUpcomingEvents)

        // Setup calendar
        setupCalendar()
        updateMonthYearText()

        // Setup upcoming events
        rvUpcomingEvents.layoutManager = LinearLayoutManager(requireContext())
        rvUpcomingEvents.adapter = EventsAdapter(events)

        // Setup real-time listener for events
        setupEventsListener()

        // Month navigation
        btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateMonthYearText()
            setupCalendar()
            updateEventDaysForMonth()
        }

        btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateMonthYearText()
            setupCalendar()
            updateEventDaysForMonth()
        }
    }

    private fun setupEventsListener() {
        // Use real-time listener for automatic sync
        eventsListener = repository.listenToEvents { eventsList ->
            events.clear()
            eventDays.clear()

            for (event in eventsList) {
                events.add(event)
                parseEventDate(event.date)
            }

            // Update upcoming events list
            rvUpcomingEvents.adapter = EventsAdapter(events)

            // Update calendar with event days
            updateEventDaysForMonth()
        }
    }

    private fun parseEventDate(dateStr: String) {
        if (dateStr.isEmpty()) return

        try {
            // Try common date formats
            val formats = listOf(
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

                        // Check if the event is in the current month view
                        if (eventCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                            eventCalendar.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH)) {
                            eventDays.add(eventCalendar.get(Calendar.DAY_OF_MONTH))
                        }
                        return
                    }
                } catch (e: Exception) {
                    // Try next format
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    private fun updateEventDaysForMonth() {
        eventDays.clear()
        for (event in events) {
            parseEventDate(event.date)
        }
        setupCalendar()
    }

    private fun updateMonthYearText() {
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvMonthYear.text = dateFormat.format(currentCalendar.time)
    }

    private fun setupCalendar() {
        val days = generateCalendarDays()
        calendarAdapter = CalendarAdapter(days, eventDays, currentCalendar.get(Calendar.DAY_OF_MONTH))
        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.adapter = calendarAdapter
    }

    private fun generateCalendarDays(): List<CalendarDay> {
        val days = mutableListOf<CalendarDay>()

        val calendar = currentCalendar.clone() as Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // Adjust for Monday start (1 = Monday, 7 = Sunday)
        val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Previous month days
        calendar.add(Calendar.MONTH, -1)
        val prevMonthDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in offset downTo 1) {
            days.add(CalendarDay(prevMonthDays - i + 1, false))
        }

        // Current month days
        for (i in 1..daysInMonth) {
            days.add(CalendarDay(i, true))
        }

        // Next month days
        val remaining = 42 - days.size // 6 rows * 7 days
        for (i in 1..remaining) {
            days.add(CalendarDay(i, false))
        }

        return days
    }

    override fun onDestroyView() {
        super.onDestroyView()
        eventsListener?.remove()
    }
}

data class CalendarDay(val day: Int, val isCurrentMonth: Boolean)

class CalendarAdapter(
    private val days: List<CalendarDay>,
    private val eventDays: Set<Int>,
    private val today: Int
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                120
            )
            gravity = android.view.Gravity.CENTER
            textSize = 14f
        }
        return DayViewHolder(textView)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val calendarDay = days[position]
        holder.tvDay.text = calendarDay.day.toString()

        if (calendarDay.isCurrentMonth) {
            holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.black))

            // Check if this day has an event
            if (eventDays.contains(calendarDay.day)) {
                holder.tvDay.setBackgroundResource(R.drawable.circle_event_day)
                holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.white))
            } else {
                holder.tvDay.background = null
            }
        } else {
            holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.gray_text))
            holder.tvDay.background = null
        }
    }

    override fun getItemCount() = days.size
}

class EventsAdapter(private val events: List<Event>) : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEventDay: TextView = view.findViewById(R.id.tvEventDay)
        val tvEventTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvEventTime: TextView = view.findViewById(R.id.tvEventTime)
        val tvEventSubtitle: TextView = view.findViewById(R.id.tvEventSubtitle)
        val tvEventSubTime: TextView = view.findViewById(R.id.tvEventSubTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.tvEventDay.text = event.day.toString()
        holder.tvEventTitle.text = event.title
        holder.tvEventTime.text = event.time
        holder.tvEventSubtitle.text = event.subtitle
        holder.tvEventSubTime.text = event.subTime
    }

    override fun getItemCount() = events.size
}
