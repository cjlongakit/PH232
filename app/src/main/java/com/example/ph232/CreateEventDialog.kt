package com.example.ph232

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class CreateEventDialog : DialogFragment() {

    private lateinit var etEventName: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etTime: TextInputEditText
    private lateinit var etLocation: TextInputEditText
    private lateinit var cbGenerateQr: MaterialCheckBox
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnCreateEvent: MaterialButton

    private lateinit var repository: FirebaseRepository
    private var onEventCreatedListener: ((Boolean, String, String?) -> Unit)? = null

    private val calendar = Calendar.getInstance()

    companion object {
        fun newInstance(): CreateEventDialog {
            return CreateEventDialog()
        }
    }

    fun setOnEventCreatedListener(listener: (Boolean, String, String?) -> Unit) {
        onEventCreatedListener = listener
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
        return inflater.inflate(R.layout.dialog_create_event, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        // Initialize views
        etEventName = view.findViewById(R.id.etEventName)
        etDescription = view.findViewById(R.id.etDescription)
        etDate = view.findViewById(R.id.etDate)
        etTime = view.findViewById(R.id.etTime)
        etLocation = view.findViewById(R.id.etLocation)
        cbGenerateQr = view.findViewById(R.id.cbGenerateQr)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnCreateEvent = view.findViewById(R.id.btnCreateEvent)

        // Date picker
        etDate.setOnClickListener {
            showDatePicker()
        }

        // Time picker
        etTime.setOnClickListener {
            showTimePicker()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            dismiss()
        }

        // Create Event button
        btnCreateEvent.setOnClickListener {
            createEvent()
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
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                etDate.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                etTime.setText(timeFormat.format(calendar.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun createEvent() {
        val eventName = etEventName.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val date = etDate.text.toString().trim()
        val time = etTime.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val generateQr = cbGenerateQr.isChecked

        // Validation
        if (eventName.isEmpty()) {
            etEventName.error = "Event name is required"
            return
        }

        if (date.isEmpty()) {
            etDate.error = "Date is required"
            return
        }

        // Generate QR code if needed
        val qrCode = if (generateQr) {
            "EVENT_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        } else {
            ""
        }

        // Parse day from date
        val day = try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate = dateFormat.parse(date)
            val cal = Calendar.getInstance()
            if (parsedDate != null) {
                cal.time = parsedDate
                cal.get(Calendar.DAY_OF_MONTH)
            } else 0
        } catch (e: Exception) {
            0
        }

        // Create event object
        val event = Event(
            name = eventName,
            title = eventName,
            description = description,
            date = date,
            time = time,
            location = location,
            qrCode = qrCode,
            day = day,
            isActive = true,
            createdBy = "admin"
        )

        // Disable button while saving
        btnCreateEvent.isEnabled = false
        btnCreateEvent.text = "Creating..."

        // Save to Firestore using repository
        repository.addEvent(
            event = event,
            onSuccess = { eventId ->
                onEventCreatedListener?.invoke(true, "Event '$eventName' created successfully!", if (generateQr) qrCode else null)
                dismiss()
            },
            onFailure = { e ->
                btnCreateEvent.isEnabled = true
                btnCreateEvent.text = "Create Event"
                onEventCreatedListener?.invoke(false, "Error: ${e.message}", null)
            }
        )
    }
}

