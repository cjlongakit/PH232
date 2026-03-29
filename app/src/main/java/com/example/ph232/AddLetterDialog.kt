package com.example.ph232

import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RadioButton
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddLetterDialog : DialogFragment() {

    private lateinit var etLetterTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDeadline: TextInputEditText
    private lateinit var rbAllStudents: RadioButton
    private lateinit var rbSpecificStudent: RadioButton
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

        etLetterTitle = view.findViewById(R.id.etLetterTitle)
        etDescription = view.findViewById(R.id.etDescription)
        etDeadline = view.findViewById(R.id.etDeadline)
        rbAllStudents = view.findViewById(R.id.rbAllStudents)
        rbSpecificStudent = view.findViewById(R.id.rbSpecificStudent)
        tilStudentId = view.findViewById(R.id.tilStudentId)
        etStudentId = view.findViewById(R.id.etStudentId)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnAddLetter = view.findViewById(R.id.btnAddLetter)

        etDeadline.setOnClickListener {
            showDatePicker()
        }

        rbAllStudents.setOnClickListener {
            tilStudentId.visibility = View.GONE
            etStudentId.text?.clear()
        }

        rbSpecificStudent.setOnClickListener {
            tilStudentId.visibility = View.VISIBLE
            setupPrefixIfNeeded()
        }

        setupPrefixIfNeeded()

        btnCancel.setOnClickListener {
            dismiss()
        }

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

    private fun setupPrefixIfNeeded() {
        if (etStudentId.tag == "prefix_bound") {
            if (!etStudentId.text.isNullOrEmpty() || tilStudentId.visibility == View.VISIBLE) {
                ensurePrefix()
            }
            return
        }

        etStudentId.tag = "prefix_bound"
        ensurePrefix()
        etStudentId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (tilStudentId.visibility == View.VISIBLE && !s.toString().startsWith("PH323-")) {
                    ensurePrefix()
                }
            }
        })
    }

    private fun ensurePrefix() {
        etStudentId.setText("PH323-")
        Selection.setSelection(etStudentId.text, etStudentId.text?.length ?: 0)
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
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun addLetter() {
        val title = etLetterTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val deadline = etDeadline.text.toString().trim()
        val isForAllStudents = rbAllStudents.isChecked
        val studentId = if (!isForAllStudents) etStudentId.text.toString().trim() else ""

        if (title.isEmpty()) {
            etLetterTitle.error = "Letter title is required"
            return
        }

        if (deadline.isEmpty()) {
            etDeadline.error = "Deadline is required"
            return
        }

        if (!isForAllStudents && (studentId.isEmpty() || studentId == "PH323-")) {
            etStudentId.error = "Student ID is required"
            return
        }

        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val letter = Letter(
            title = title,
            name = title,
            description = description,
            deadline = deadline,
            status = "pending",
            dateCreated = currentDate,
            isCompleted = false,
            studentId = studentId,
            studentName = "",
            assignedBy = "admin",
            turnedInDate = "",
            notes = ""
        )

        btnAddLetter.isEnabled = false
        btnAddLetter.text = "Adding..."

        repository.addLetter(
            letter = letter,
            onSuccess = {
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
