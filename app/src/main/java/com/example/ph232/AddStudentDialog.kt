package com.example.ph232

import android.app.Dialog
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
import com.google.android.material.textfield.TextInputEditText

class AddStudentDialog : DialogFragment() {

    private lateinit var etStudentId: TextInputEditText
    private lateinit var etStudentName: TextInputEditText
    private lateinit var etStudentEmail: TextInputEditText
    private lateinit var etSection: TextInputEditText
    private lateinit var etYear: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnAddStudent: MaterialButton
    
    private lateinit var repository: FirebaseRepository
    private var onStudentAddedListener: ((Boolean, String) -> Unit)? = null

    companion object {
        fun newInstance(): AddStudentDialog {
            return AddStudentDialog()
        }
    }

    fun setOnStudentAddedListener(listener: (Boolean, String) -> Unit) {
        onStudentAddedListener = listener
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
        return inflater.inflate(R.layout.dialog_add_student, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        // Initialize views
        etStudentId = view.findViewById(R.id.etStudentId)
        etStudentName = view.findViewById(R.id.etStudentName)
        etStudentEmail = view.findViewById(R.id.etStudentEmail)
        etSection = view.findViewById(R.id.etSection)
        etYear = view.findViewById(R.id.etYear)
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnAddStudent = view.findViewById(R.id.btnAddStudent)

        // Cancel button
        btnCancel.setOnClickListener {
            dismiss()
        }

        // Add Student button
        btnAddStudent.setOnClickListener {
            addStudent()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addStudent() {
        val studentId = etStudentId.text.toString().trim()
        val name = etStudentName.text.toString().trim()
        val email = etStudentEmail.text.toString().trim()
        val section = etSection.text.toString().trim().uppercase()
        val year = etYear.text.toString().trim()
        val phoneNumber = etPhoneNumber.text.toString().trim()

        // Validation
        if (studentId.isEmpty()) {
            etStudentId.error = "Student ID is required"
            return
        }
        
        if (studentId.length != 3 || !studentId.all { it.isDigit() }) {
            etStudentId.error = "Student ID must be 3 digits (e.g., 001)"
            return
        }

        if (name.isEmpty()) {
            etStudentName.error = "Name is required"
            return
        }

        // Create student object
        val student = Student(
            id = studentId,
            name = name,
            email = email,
            section = section,
            year = year,
            phoneNumber = phoneNumber,
            status = "active"
        )

        // Disable button while saving
        btnAddStudent.isEnabled = false
        btnAddStudent.text = "Adding..."

        // Save to Firestore using repository
        repository.addStudent(
            student = student,
            onSuccess = { docId ->
                onStudentAddedListener?.invoke(true, "Student $name added successfully!")
                dismiss()
            },
            onFailure = { e ->
                btnAddStudent.isEnabled = true
                btnAddStudent.text = "Add Student"
                onStudentAddedListener?.invoke(false, "Error: ${e.message}")
            }
        )
    }
}

