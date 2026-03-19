package com.example.ph232

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
       import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class AdminEventsFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private lateinit var tvMonth: TextView
    private lateinit var tvYear: TextView
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var rvCalendar: RecyclerView
    private lateinit var eventsContainer: LinearLayout
    private lateinit var fabAddEvent: ExtendedFloatingActionButton

    private var currentCalendar = Calendar.getInstance()
    private var events = mutableListOf<Event>()
    private var eventDays = mutableMapOf<Int, MutableList<Event>>() // Day -> List of events
    private var eventsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_events_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        // Initialize views
        tvMonth = view.findViewById(R.id.tvMonth)
        tvYear = view.findViewById(R.id.tvYear)
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)
        rvCalendar = view.findViewById(R.id.rvCalendar)
        eventsContainer = view.findViewById(R.id.eventsContainer)
        fabAddEvent = view.findViewById(R.id.fabAddEvent)

        // Setup calendar
        updateMonthYearText()
        setupCalendar()

        // Setup real-time listener for events
        setupEventsListener()

        // Month navigation
        btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateMonthYearText()
            updateEventDaysForMonth()
        }

        btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateMonthYearText()
            updateEventDaysForMonth()
        }

        // Add event FAB
        fabAddEvent.setOnClickListener {
            showAddEventDialog(null, 0)
        }
    }

    private fun setupEventsListener() {
        eventsListener = repository.listenToEvents { eventsList ->
            events.clear()
            events.addAll(eventsList)
            updateEventDaysForMonth()
        }
    }

    private fun updateMonthYearText() {
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        tvMonth.text = monthFormat.format(currentCalendar.time)
        tvYear.text = yearFormat.format(currentCalendar.time)
    }

    private fun updateEventDaysForMonth() {
        eventDays.clear()

        for (event in events) {
            val eventDay = parseEventDay(event.date)
            if (eventDay > 0) {
                if (eventDays[eventDay] == null) {
                    eventDays[eventDay] = mutableListOf()
                }
                eventDays[eventDay]?.add(event)
            }
        }

        setupCalendar()
        updateEventsThisMonth()
    }

    private fun parseEventDay(dateStr: String): Int {
        if (dateStr.isEmpty()) return 0

        try {
            val formats = listOf(
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
            )

            for (format in formats) {
                try {
                    val date = format.parse(dateStr)
                    if (date != null) {
                        val eventCalendar = Calendar.getInstance()
                        eventCalendar.time = date

                        if (eventCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                            eventCalendar.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH)) {
                            return eventCalendar.get(Calendar.DAY_OF_MONTH)
                        }
                    }
                } catch (e: Exception) {
                    // Try next format
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return 0
    }

    private fun setupCalendar() {
        val days = generateCalendarDays()
        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH)
        val todayDay = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else -1

        val adapter = AdminCalendarAdapter(
            days = days,
            eventDays = eventDays.keys,
            today = todayDay,
            onDayClick = { day, isCurrentMonth ->
                if (isCurrentMonth && day > 0) {
                    onCalendarDayClicked(day)
                }
            }
        )

        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.adapter = adapter
    }

    private fun onCalendarDayClicked(day: Int) {
        // Format the date
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val selectedCalendar = currentCalendar.clone() as Calendar
        selectedCalendar.set(Calendar.DAY_OF_MONTH, day)
        val dateStr = dateFormat.format(selectedCalendar.time)

        // Show add event dialog with selected date
        showAddEventDialog(dateStr, day)
    }

    private fun showAddEventDialog(date: String?, day: Int) {
        val dateToUse = date ?: run {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            dateFormat.format(Calendar.getInstance().time)
        }
        val dayToUse = if (day > 0) day else Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        val dialog = AddEventCalendarDialog.newInstance(dateToUse, dayToUse)
        dialog.setOnEventAddedListener { success, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "AddEventCalendarDialog")
    }

    private fun updateEventsThisMonth() {
        eventsContainer.removeAllViews()

        val monthEvents = events.filter { parseEventDay(it.date) > 0 }
            .sortedBy { parseEventDay(it.date) }

        if (monthEvents.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = "No events this month"
                textSize = 14f
                setTextColor(resources.getColor(R.color.gray_text, null))
            }
            eventsContainer.addView(emptyView)
            return
        }

        for (event in monthEvents) {
            addEventItem(event)
        }
    }

    private fun addEventItem(event: Event) {
        val day = parseEventDay(event.date)
        val eventName = event.name.ifEmpty { event.title.ifEmpty { "Untitled Event" } }

        val itemLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setPadding(16, 12, 16, 12)
            setBackgroundColor(resources.getColor(R.color.gray_light, null))
        }

        val displayText = "$day - $eventName"
        val textView = TextView(requireContext()).apply {
            text = displayText
            textSize = 14f
            setTextColor(resources.getColor(R.color.black, null))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val deleteButton = TextView(requireContext()).apply {
            text = "✕"
            textSize = 16f
            setTextColor(resources.getColor(R.color.red_pending, null))
            setPadding(16, 0, 0, 0)
            setOnClickListener {
                deleteEvent(event)
            }
        }

        itemLayout.addView(textView)
        itemLayout.addView(deleteButton)
        eventsContainer.addView(itemLayout)
    }

    private fun deleteEvent(event: Event) {
        repository.deleteEvent(
            eventId = event.id,
            onSuccess = {
                Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
            },
            onFailure = { exception: Exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun generateCalendarDays(): List<AdminCalendarDay> {
        val days = mutableListOf<AdminCalendarDay>()

        val calendar = currentCalendar.clone() as Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Previous month days (Sunday = 1, so offset = firstDayOfWeek - 1)
        val offset = firstDayOfWeek - 1
        calendar.add(Calendar.MONTH, -1)
        val prevMonthDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in offset downTo 1) {
            days.add(AdminCalendarDay(prevMonthDays - i + 1, false))
        }

        // Current month days
        for (i in 1..daysInMonth) {
            days.add(AdminCalendarDay(i, true))
        }

        // Next month days
        val remaining = 42 - days.size
        for (i in 1..remaining) {
            days.add(AdminCalendarDay(i, false))
        }

        return days
    }

    override fun onDestroyView() {
        super.onDestroyView()
        eventsListener?.remove()
    }
}

data class AdminCalendarDay(val day: Int, val isCurrentMonth: Boolean)

class AdminCalendarAdapter(
    private val days: List<AdminCalendarDay>,
    private val eventDays: Set<Int>,
    private val today: Int,
    private val onDayClick: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<AdminCalendarAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tvDay)
        val eventIndicator: View = view.findViewById(R.id.eventIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val calendarDay = days[position]
        holder.tvDay.text = calendarDay.day.toString()

        // Reset background
        holder.tvDay.background = null

        if (calendarDay.isCurrentMonth) {
            holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.black))

            // Highlight today
            if (calendarDay.day == today) {
                holder.tvDay.setBackgroundResource(R.drawable.circle_today)
            }

            // Show event indicator (gray background for admin)
            if (eventDays.contains(calendarDay.day)) {
                holder.tvDay.setBackgroundResource(R.drawable.circle_event_day)
                holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.white))
                holder.eventIndicator.visibility = View.GONE
            } else {
                holder.eventIndicator.visibility = View.GONE
            }

            // Click listener
            holder.itemView.setOnClickListener {
                onDayClick(calendarDay.day, true)
            }
        } else {
            holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.gray_text))
            holder.eventIndicator.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount() = days.size
}
