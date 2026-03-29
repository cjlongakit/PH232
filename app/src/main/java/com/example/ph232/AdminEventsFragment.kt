package com.example.ph232

import android.text.TextUtils
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminEventsFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private lateinit var tvMonth: TextView
    private lateinit var tvYear: TextView
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var rvCalendar: RecyclerView
    private lateinit var eventsContainer: LinearLayout
    private lateinit var fabAddEvent: ExtendedFloatingActionButton
    private lateinit var tvMonthSummary: TextView
    private lateinit var tvEventsSubtitle: TextView
    private lateinit var tvEventCount: TextView

    private var currentCalendar = Calendar.getInstance()
    private var events = mutableListOf<Event>()
    private var eventDays = mutableMapOf<Int, MutableList<Event>>()
    private var eventsListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

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

        tvMonth = view.findViewById(R.id.tvMonth)
        tvYear = view.findViewById(R.id.tvYear)
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)
        rvCalendar = view.findViewById(R.id.rvCalendar)
        eventsContainer = view.findViewById(R.id.eventsContainer)
        fabAddEvent = view.findViewById(R.id.fabAddEvent)
        tvMonthSummary = view.findViewById(R.id.tvMonthSummary)
        tvEventsSubtitle = view.findViewById(R.id.tvEventsSubtitle)
        tvEventCount = view.findViewById(R.id.tvEventCount)

        updateMonthYearText()
        setupCalendar()

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading events...")
        setupEventsListener()

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

        tvYear.setOnClickListener {
            showYearPickerDialog()
        }

        fabAddEvent.setOnClickListener {
            showEventDialog(date = null, day = 0, event = null)
        }
    }

    private fun setupEventsListener() {
        eventsListener = repository.listenToEvents { eventsList ->
            events.clear()
            events.addAll(eventsList)
            updateEventDaysForMonth()
            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
            }
        }
    }

    private fun updateMonthYearText() {
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        tvMonth.text = monthFormat.format(currentCalendar.time)
        tvYear.text = yearFormat.format(currentCalendar.time)
        tvMonthSummary.text = "Tap a date to add an event or review scheduled items for ${monthFormat.format(currentCalendar.time)}."
    }

    private fun updateEventDaysForMonth() {
        eventDays.clear()

        for (event in events) {
            val eventDay = parseEventDay(event.date)
            if (eventDay > 0) {
                eventDays.getOrPut(eventDay) { mutableListOf() }.add(event)
            }
        }

        setupCalendar()
        updateEventsThisMonth()
    }

    private fun parseEventDay(dateStr: String): Int {
        if (dateStr.isEmpty()) return 0

        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        )

        for (format in formats) {
            try {
                val date = format.parse(dateStr) ?: continue
                val eventCalendar = Calendar.getInstance().apply { time = date }
                if (eventCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                    eventCalendar.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH)
                ) {
                    return eventCalendar.get(Calendar.DAY_OF_MONTH)
                }
            } catch (_: Exception) {
            }
        }
        return 0
    }

    private fun setupCalendar() {
        val days = generateCalendarDays()
        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
            today.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH)
        val todayDay = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else -1

        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.adapter = AdminCalendarAdapter(
            days = days,
            eventDays = eventDays.keys,
            today = todayDay,
            onDayClick = { day, isCurrentMonthDay ->
                if (isCurrentMonthDay && day > 0) {
                    onCalendarDayClicked(day)
                }
            }
        )
    }

    private fun onCalendarDayClicked(day: Int) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val selectedCalendar = currentCalendar.clone() as Calendar
        selectedCalendar.set(Calendar.DAY_OF_MONTH, day)
        val dateStr = dateFormat.format(selectedCalendar.time)
        showEventDialog(date = dateStr, day = day, event = null)
    }

    private fun showEventDialog(date: String?, day: Int, event: Event?) {
        val dateToUse = event?.date?.takeIf { it.isNotBlank() } ?: date ?: run {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        }
        val dayToUse = if (event?.day ?: 0 > 0) event?.day ?: 0 else if (day > 0) day else Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        val dialog = AddEventCalendarDialog.newInstance(
            date = dateToUse,
            day = dayToUse,
            eventId = event?.id.orEmpty(),
            eventName = event?.name?.ifBlank { event.title }.orEmpty(),
            description = event?.description.orEmpty()
        )
        dialog.setOnEventAddedListener { _, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "AddEventCalendarDialog")
    }

    private fun updateEventsThisMonth() {
        eventsContainer.removeAllViews()

        val monthEvents = events
            .filter { parseEventDay(it.date) > 0 }
            .sortedBy { parseEventDay(it.date) }

        if (monthEvents.isEmpty()) {
            tvEventCount.text = "0 events"
            tvEventsSubtitle.text = "No scheduled items for this month."
            val emptyCard = MaterialCardView(requireContext()).apply {
                radius = 20f
                cardElevation = 0f
                setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_light))
                strokeWidth = 1
                strokeColor = ContextCompat.getColor(requireContext(), R.color.gray_200)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val emptyText = TextView(requireContext()).apply {
                text = "No events scheduled for this month"
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
                setPadding(24, 28, 24, 28)
                gravity = Gravity.CENTER
            }
            emptyCard.addView(emptyText)
            eventsContainer.addView(emptyCard)
            return
        }

        val count = monthEvents.size
        tvEventCount.text = if (count == 1) "1 event" else "$count events"
        tvEventsSubtitle.text = "Use each card to review, edit, or remove scheduled events."

        monthEvents.forEach { event ->
            addEventItem(event)
        }
    }

    private fun showYearPickerDialog() {
        val currentYear = currentCalendar.get(Calendar.YEAR)
        val years = (2020..2035).toList()
        val selectedIndex = years.indexOf(currentYear).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Year")
            .setSingleChoiceItems(years.map { it.toString() }.toTypedArray(), selectedIndex) { dialog, which ->
                currentCalendar.set(Calendar.YEAR, years[which])
                updateMonthYearText()
                updateEventDaysForMonth()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addEventItem(event: Event) {
        val day = parseEventDay(event.date)
        val eventName = event.name.ifEmpty { event.title.ifEmpty { "Untitled Event" } }

        val cardView = MaterialCardView(requireContext()).apply {
            radius = 18f
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            strokeWidth = 1
            strokeColor = ContextCompat.getColor(requireContext(), R.color.gray_200)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
        }

        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 12
            }
        }

        val dateView = TextView(requireContext()).apply {
            text = formatFriendlyDay(day)
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val nameView = TextView(requireContext()).apply {
            text = eventName
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        titleColumn.addView(dateView)
        titleColumn.addView(nameView)

        val editButton = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Edit"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_primary))
            strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.purple_primary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
            setOnClickListener {
                showEventDialog(date = event.date, day = day, event = event)
            }
        }

        val deleteButton = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.red_pending))
            strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.red_pending)
            setOnClickListener {
                confirmDeleteEvent(event)
            }
        }

        headerRow.addView(titleColumn)

        val metaText = buildString {
            if (event.time.isNotBlank()) append(event.time)
            if (event.location.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(event.location)
            }
        }

        val metaView = TextView(requireContext()).apply {
            text = if (metaText.isBlank()) "No time or location set" else metaText
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
            setPadding(0, 10, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val descriptionView = TextView(requireContext()).apply {
            text = if (event.description.isBlank()) "No description provided." else event.description
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))
            setPadding(0, 8, 0, 0)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = 14
            }
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_200))
        }

        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 14, 0, 0)
        }

        actionRow.addView(editButton)
        actionRow.addView(deleteButton)

        contentLayout.addView(headerRow)
        contentLayout.addView(metaView)
        contentLayout.addView(descriptionView)
        contentLayout.addView(divider)
        contentLayout.addView(actionRow)
        cardView.addView(contentLayout)
        eventsContainer.addView(cardView)
    }

    private fun confirmDeleteEvent(event: Event) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Event")
            .setMessage("Delete ${event.name.ifEmpty { event.title.ifEmpty { "this event" } }}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEvent(event)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatFriendlyDay(day: Int): String {
        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, day)
        return SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(cal.time)
    }

    private fun deleteEvent(event: Event) {
        repository.deleteEvent(
            eventId = event.id,
            onSuccess = {
                Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
            },
            onFailure = { exception ->
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
        val offset = firstDayOfWeek - 1

        calendar.add(Calendar.MONTH, -1)
        val prevMonthDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in offset downTo 1) {
            days.add(AdminCalendarDay(prevMonthDays - i + 1, false))
        }

        for (i in 1..daysInMonth) {
            days.add(AdminCalendarDay(i, true))
        }

        val remaining = 42 - days.size
        for (i in 1..remaining) {
            days.add(AdminCalendarDay(i, false))
        }

        return days
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
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
        holder.tvDay.background = null

        if (calendarDay.isCurrentMonth) {
            holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.black))
            val hasEvent = eventDays.contains(calendarDay.day)
            if (calendarDay.day == today) {
                holder.tvDay.setBackgroundResource(R.drawable.circle_today)
            }

            if (hasEvent) {
                if (calendarDay.day != today) {
                    holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.purple_primary))
                }
                holder.eventIndicator.visibility = View.VISIBLE
            } else {
                holder.eventIndicator.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onDayClick(calendarDay.day, true)
            }
        } else {
            holder.tvDay.setTextColor(holder.itemView.context.getColor(R.color.gray_400))
            holder.eventIndicator.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount() = days.size
}
