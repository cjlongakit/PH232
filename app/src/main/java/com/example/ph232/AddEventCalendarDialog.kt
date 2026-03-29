package com.example.ph232

import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddEventCalendarDialog : DialogFragment() {

    private lateinit var tvDialogTitle: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var etEventName: TextInputEditText
    private lateinit var etEventDate: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var inputEventDate: TextInputLayout
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnAddEvent: MaterialButton

    private lateinit var repository: FirebaseRepository
    private var selectedDate: String = ""
    private var selectedDay: Int = 0
    private var eventId: String = ""
    private var initialEventName: String = ""
    private var initialDescription: String = ""
    private var onEventAddedListener: ((Boolean, String) -> Unit)? = null

    companion object {
        private const val ARG_DATE = "arg_date"
        private const val ARG_DAY = "arg_day"
        private const val ARG_EVENT_ID = "arg_event_id"
        private const val ARG_EVENT_NAME = "arg_event_name"
        private const val ARG_DESCRIPTION = "arg_description"

        fun newInstance(
            date: String,
            day: Int,
            eventId: String = "",
            eventName: String = "",
            description: String = ""
        ): AddEventCalendarDialog {
            return AddEventCalendarDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DATE, date)
                    putInt(ARG_DAY, day)
                    putString(ARG_EVENT_ID, eventId)
                    putString(ARG_EVENT_NAME, eventName)
                    putString(ARG_DESCRIPTION, description)
                }
            }
        }
    }

    fun setOnEventAddedListener(listener: (Boolean, String) -> Unit) {
        onEventAddedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_event_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        selectedDate = arguments?.getString(ARG_DATE) ?: ""
        selectedDay = arguments?.getInt(ARG_DAY) ?: 0
        eventId = arguments?.getString(ARG_EVENT_ID) ?: ""
        initialEventName = arguments?.getString(ARG_EVENT_NAME) ?: ""
        initialDescription = arguments?.getString(ARG_DESCRIPTION) ?: ""

        tvDialogTitle = view.findViewById(R.id.tvDialogTitle)
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate)
        etEventName = view.findViewById(R.id.etEventName)
        etEventDate = view.findViewById(R.id.etEventDate)
        etDescription = view.findViewById(R.id.etDescription)
        inputEventDate = view.findViewById(R.id.inputEventDate)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnAddEvent = view.findViewById(R.id.btnAddEvent)

        val isEditMode = eventId.isNotBlank()
        tvDialogTitle.text = if (isEditMode) "Edit Event" else "Add Event"
        tvSelectedDate.text = if (isEditMode) "Update the selected event details." else "Create an event for the selected day."
        btnAddEvent.text = if (isEditMode) "Save Changes" else "Add Event"

        setDisplayedDate(selectedDate)
        etEventName.setText(initialEventName)
        etDescription.setText(initialDescription)

        etEventDate.setOnClickListener {
            showDatePicker()
        }
        inputEventDate.setEndIconOnClickListener {
            showDatePicker()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnAddEvent.setOnClickListener {
            saveEvent(isEditMode)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        if (selectedDate.isNotEmpty()) {
            parseDate(selectedDate)?.let { calendar.time = it }
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val storageFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                selectedDate = storageFormat.format(calendar.time)
                selectedDay = dayOfMonth
                etEventDate.setText(displayFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveEvent(isEditMode: Boolean) {
        val eventName = etEventName.text.toString().trim()
        val eventDate = selectedDate.ifBlank { normalizeToStorageDate(etEventDate.text.toString().trim()) }
        val description = etDescription.text.toString().trim()

        if (eventName.isEmpty()) {
            etEventName.error = "Event name is required"
            return
        }

        if (eventDate.isEmpty()) {
            etEventDate.error = "Date is required"
            return
        }

        btnAddEvent.isEnabled = false
        btnAddEvent.text = if (isEditMode) "Saving..." else "Adding..."

        if (isEditMode) {
            repository.updateEvent(
                eventId = eventId,
                updates = mapOf(
                    "name" to eventName,
                    "title" to eventName,
                    "description" to description,
                    "date" to eventDate,
                    "day" to selectedDay
                ),
                onSuccess = {
                    onEventAddedListener?.invoke(true, "Event updated successfully!")
                    dismiss()
                },
                onFailure = { e ->
                    btnAddEvent.isEnabled = true
                    btnAddEvent.text = "Save Changes"
                    onEventAddedListener?.invoke(false, "Error: ${e.message}")
                }
            )
            return
        }

        val qrCode = "EVENT_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val event = Event(
            name = eventName,
            title = eventName,
            description = description,
            date = eventDate,
            time = "",
            location = "",
            qrCode = qrCode,
            day = selectedDay,
            isActive = true,
            createdBy = "admin"
        )

        repository.addEvent(
            event = event,
            onSuccess = {
                onEventAddedListener?.invoke(true, "Event '$eventName' added successfully!")
                dismiss()
            },
            onFailure = { e ->
                btnAddEvent.isEnabled = true
                btnAddEvent.text = "Add Event"
                onEventAddedListener?.invoke(false, "Error: ${e.message}")
            }
        )
    }

    private fun setDisplayedDate(rawDate: String) {
        if (rawDate.isBlank()) {
            inputEventDate.isVisible = true
            return
        }
        selectedDate = normalizeToStorageDate(rawDate)
        val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val parsedDate = parseDate(selectedDate)
        etEventDate.setText(parsedDate?.let { displayFormat.format(it) } ?: rawDate)
        if (selectedDay == 0 && parsedDate != null) {
            val calendar = Calendar.getInstance().apply { time = parsedDate }
            selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
        }
    }

    private fun normalizeToStorageDate(rawDate: String): String {
        val parsedDate = parseDate(rawDate) ?: return rawDate
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(parsedDate)
    }

    private fun parseDate(rawDate: String) = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    ).firstNotNullOfOrNull { format ->
        runCatching { format.parse(rawDate) }.getOrNull()
    }
}
