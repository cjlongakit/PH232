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
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class AddEventCalendarDialog : DialogFragment() {

    private lateinit var tvDialogTitle: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var etEventName: TextInputEditText
    private lateinit var etEventDate: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnAddEvent: MaterialButton

    private lateinit var repository: FirebaseRepository
    private var selectedDate: String = ""
    private var selectedDay: Int = 0
    private var onEventAddedListener: ((Boolean, String) -> Unit)? = null

    companion object {
        private const val ARG_DATE = "arg_date"
        private const val ARG_DAY = "arg_day"

        fun newInstance(date: String, day: Int): AddEventCalendarDialog {
            return AddEventCalendarDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DATE, date)
                    putInt(ARG_DAY, day)
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

        // Get arguments
        selectedDate = arguments?.getString(ARG_DATE) ?: ""
        selectedDay = arguments?.getInt(ARG_DAY) ?: 0

        // Initialize views
        tvDialogTitle = view.findViewById(R.id.tvDialogTitle)
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate)
        etEventName = view.findViewById(R.id.etEventName)
        etEventDate = view.findViewById(R.id.etEventDate)
        etDescription = view.findViewById(R.id.etDescription)
        btnAddEvent = view.findViewById(R.id.btnAddEvent)

        // Set initial date
        if (selectedDate.isNotEmpty()) {
            etEventDate.setText(selectedDate)
            tvSelectedDate.text = "No events this day."
        }

        // Date picker click
        etEventDate.setOnClickListener {
            showDatePicker()
        }

        // Add event button
        btnAddEvent.setOnClickListener {
            addEvent()
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

        // Parse existing date if available
        if (selectedDate.isNotEmpty()) {
            try {
                val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = format.parse(selectedDate)
                if (date != null) {
                    calendar.time = date
                }
            } catch (e: Exception) {
                // Use current date
            }
        }

        val datePickerDialog = DatePickerDialog(
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
        )
        datePickerDialog.show()
    }

    private fun addEvent() {
        val eventName = etEventName.text.toString().trim()
        val eventDate = etEventDate.text.toString().trim()
        val description = etDescription.text.toString().trim()

        // Validation
        if (eventName.isEmpty()) {
            etEventName.error = "Event name is required"
            return
        }

        if (eventDate.isEmpty()) {
            etEventDate.error = "Date is required"
            return
        }

        // Generate QR code for the event
        val qrCode = "EVENT_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

        // Create event object
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

        // Disable button while saving
        btnAddEvent.isEnabled = false
        btnAddEvent.text = "Adding..."

        // Save to Firestore
        repository.addEvent(
            event = event,
            onSuccess = { eventId ->
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
}

