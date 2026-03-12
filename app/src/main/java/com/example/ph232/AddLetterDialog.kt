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
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*

class AddLetterDialog : DialogFragment() {

    private lateinit var etLetterTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDeadline: TextInputEditText
    private lateinit var rgAssignTo: RadioGroup
    private lateinit var tilStudentId: TextInputLayout
    private lateinit var etStudentId: TextInputEditText
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnAddLetter: MaterialButton

    private lateinit var repository: FirebaseRepository
    private var onLetterAddedListener: ((Boolean, String) -> Unit)? = null

    private val calendar = Calendar.getInstance()

    companion object {
        fun newInstance(): AddLetterDialog {
            return AddLetterDialog()
        }
    }

    fun setOnLetterAddedListener(listener: (Boolean, String) -> Unit) {
        onLetterAddedListener = listener
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
        return inflater.inflate(R.layout.dialog_add_letter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        // Initialize views
        etLetterTitle = view.findViewById(R.id.etLetterTitle)
        etDescription = view.findViewById(R.id.etDescription)
        etDeadline = view.findViewById(R.id.etDeadline)
        rgAssignTo = view.findViewById(R.id.rgAssignTo)
        tilStudentId = view.findViewById(R.id.tilStudentId)
        etStudentId = view.findViewById(R.id.etStudentId)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnAddLetter = view.findViewById(R.id.btnAddLetter)

        // Date picker for deadline
        etDeadline.setOnClickListener {
            showDatePicker()
        }

        // Toggle student ID field visibility based on radio selection
        rgAssignTo.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbAllStudents -> {
                    tilStudentId.visibility = View.GONE
                    etStudentId.text?.clear()
                }
                R.id.rbSpecificStudent -> {
                    tilStudentId.visibility = View.VISIBLE
                }
            }
        }

        // Cancel button
        btnCancel.setOnClickListener {
            dismiss()
        }

        // Add Letter button
        btnAddLetter.setOnClickListener {
            addLetter()
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
                etDeadline.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Set minimum date to today
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun addLetter() {
        val title = etLetterTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val deadline = etDeadline.text.toString().trim()
        val isForAllStudents = rgAssignTo.checkedRadioButtonId == R.id.rbAllStudents
        val studentId = if (!isForAllStudents) etStudentId.text.toString().trim() else ""

        // Validation
        if (title.isEmpty()) {
            etLetterTitle.error = "Letter title is required"
            return
        }

        if (deadline.isEmpty()) {
            etDeadline.error = "Deadline is required"
            return
        }

        if (!isForAllStudents && studentId.isEmpty()) {
            etStudentId.error = "Student ID is required"
            return
        }

        if (!isForAllStudents && (studentId.length != 3 || !studentId.all { it.isDigit() })) {
            etStudentId.error = "Student ID must be 3 digits"
            return
        }

        // Get current date as created date
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Create letter object
        val letter = Letter(
            title = title,
            name = title,
            description = description,
            deadline = deadline,
            status = "pending",
            dateCreated = currentDate,
            isCompleted = false,
            studentId = studentId, // Empty for all students
            studentName = "", // Will be populated when specific student is selected
            assignedBy = "admin",
            turnedInDate = "",
            notes = ""
        )

        // Disable button while saving
        btnAddLetter.isEnabled = false
        btnAddLetter.text = "Adding..."

        // Save to Firestore using repository
        repository.addLetter(
            letter = letter,
            onSuccess = { docId ->
                val message = if (isForAllStudents) {
                    "Letter '$title' added for all students!"
                } else {
                    "Letter '$title' added for student $studentId!"
                }
                onLetterAddedListener?.invoke(true, message)
                dismiss()
            },
            onFailure = { e ->
                btnAddLetter.isEnabled = true
                btnAddLetter.text = "Add Letter"
                onLetterAddedListener?.invoke(false, "Error: ${e.message}")
            }
        )
    }
}

