package com.example.ph232

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class EditStudentDialog : DialogFragment() {

    private lateinit var etStudentName: TextInputEditText
    private lateinit var etStudentEmail: TextInputEditText
    private lateinit var etSection: TextInputEditText
    private lateinit var etYear: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSave: MaterialButton

    private lateinit var repository: FirebaseRepository
    private val db = FirebaseFirestore.getInstance()
    private var onStudentUpdatedListener: ((Boolean, String) -> Unit)? = null

    companion object {
        fun newInstance(student: Student): EditStudentDialog {
            val dialog = EditStudentDialog()
            val args = Bundle()
            args.putString("studentId", student.id)
            args.putString("studentName", student.name)
            args.putString("studentEmail", student.email)
            args.putString("studentSection", student.section)
            args.putString("studentYear", student.year)
            args.putString("studentPhone", student.phoneNumber)
            args.putString("studentStatus", student.status)
            dialog.arguments = args
            return dialog
        }
    }

    fun setOnStudentUpdatedListener(listener: (Boolean, String) -> Unit) {
        onStudentUpdatedListener = listener
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
        return inflater.inflate(R.layout.dialog_edit_student, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        etStudentName = view.findViewById(R.id.etStudentName)
        etStudentEmail = view.findViewById(R.id.etStudentEmail)
        etSection = view.findViewById(R.id.etSection)
        etYear = view.findViewById(R.id.etYear)
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnSave = view.findViewById(R.id.btnSaveStudent)

        // Pre-fill fields
        arguments?.let { args ->
            etStudentName.setText(args.getString("studentName", ""))
            etStudentEmail.setText(args.getString("studentEmail", ""))
            etSection.setText(args.getString("studentSection", ""))
            etYear.setText(args.getString("studentYear", ""))
            etPhoneNumber.setText(args.getString("studentPhone", ""))
        }

        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener { saveStudent() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun saveStudent() {
        val studentId = arguments?.getString("studentId") ?: return
        val name = etStudentName.text.toString().trim()
        val email = etStudentEmail.text.toString().trim()
        val section = etSection.text.toString().trim().uppercase()
        val year = etYear.text.toString().trim()
        val phoneNumber = etPhoneNumber.text.toString().trim()

        if (name.isEmpty()) {
            etStudentName.error = "Name is required"
            return
        }

        val updates = mapOf<String, Any>(
            "name" to name,
            "email" to email,
            "section" to section,
            "year" to year,
            "phoneNumber" to phoneNumber
        )

        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        val userUpdates = mutableMapOf<String, Any>(
            "name" to name,
            "email" to email,
            "phone" to phoneNumber,
            "phoneNumber" to phoneNumber,
            "section" to section,
            "year" to year
        )
        val nameParts = name.split(" ").filter { it.isNotBlank() }
        if (nameParts.isNotEmpty()) {
            userUpdates["FirstName"] = nameParts.first()
            userUpdates["LastName"] = nameParts.drop(1).joinToString(" ")
        }

        val batch = db.batch()
        batch.set(db.collection("students").document(studentId), updates, com.google.firebase.firestore.SetOptions.merge())
        batch.set(db.collection("users").document(studentId), userUpdates, com.google.firebase.firestore.SetOptions.merge())
        batch.commit()
            .addOnSuccessListener {
                onStudentUpdatedListener?.invoke(true, "Student updated successfully!")
                dismiss()
            }
            .addOnFailureListener { e ->
                btnSave.isEnabled = true
                btnSave.text = "Save Changes"
                onStudentUpdatedListener?.invoke(false, "Error: ${e.message}")
            }
    }
}

