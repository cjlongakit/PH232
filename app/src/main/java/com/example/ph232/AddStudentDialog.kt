package com.example.ph232

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
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AddStudentDialog : DialogFragment() {

    // Beneficiary fields
    private lateinit var etBenId: TextInputEditText
    private lateinit var etBenFirstName: TextInputEditText
    private lateinit var etBenLastName: TextInputEditText
    private lateinit var etBenBirthdate: TextInputEditText
    private lateinit var etBenSchoolName: TextInputEditText
    private lateinit var etBenSchoolAddress: TextInputEditText
    private lateinit var etBenGrade: TextInputEditText

    // Guardian fields
    private lateinit var etGuardFirstName: TextInputEditText
    private lateinit var etGuardLastName: TextInputEditText
    private lateinit var etGuardMobile: TextInputEditText
    private lateinit var etGuardAddress: TextInputEditText
    private lateinit var etGuardOccupation: TextInputEditText
    private lateinit var etGuardBirthdate: TextInputEditText
    private lateinit var etGuardEmail: TextInputEditText

    // Account fields
    private lateinit var etRegUsername: TextInputEditText
    private lateinit var etRegPassword: TextInputEditText
    private lateinit var etRegConfirmPass: TextInputEditText

    private lateinit var btnCancel: MaterialButton
    private lateinit var btnAddStudent: MaterialButton

    private val db = FirebaseFirestore.getInstance()
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

        // Initialize beneficiary views
        etBenId = view.findViewById(R.id.etBenId)
        etBenFirstName = view.findViewById(R.id.etBenFirstName)
        etBenLastName = view.findViewById(R.id.etBenLastName)
        etBenBirthdate = view.findViewById(R.id.etBenBirthdate)
        etBenSchoolName = view.findViewById(R.id.etBenSchoolName)
        etBenSchoolAddress = view.findViewById(R.id.etBenSchoolAddress)
        etBenGrade = view.findViewById(R.id.etBenGrade)

        // Initialize guardian views
        etGuardFirstName = view.findViewById(R.id.etGuardFirstName)
        etGuardLastName = view.findViewById(R.id.etGuardLastName)
        etGuardMobile = view.findViewById(R.id.etGuardMobile)
        etGuardAddress = view.findViewById(R.id.etGuardAddress)
        etGuardOccupation = view.findViewById(R.id.etGuardOccupation)
        etGuardBirthdate = view.findViewById(R.id.etGuardBirthdate)
        etGuardEmail = view.findViewById(R.id.etGuardEmail)

        // Initialize account views
        etRegUsername = view.findViewById(R.id.etRegUsername)
        etRegPassword = view.findViewById(R.id.etRegPassword)
        etRegConfirmPass = view.findViewById(R.id.etRegConfirmPass)

        btnCancel = view.findViewById(R.id.btnCancel)
        btnAddStudent = view.findViewById(R.id.btnAddStudent)

        // Setup PH323- prefix for ID and username
        setupPrefix(etBenId)
        setupPrefix(etRegUsername)

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

    private fun setupPrefix(editText: TextInputEditText) {
        editText.setText("PH323-")
        Selection.setSelection(editText.text, editText.text?.length ?: 0)
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.toString().startsWith("PH323-")) {
                    editText.setText("PH323-")
                    Selection.setSelection(editText.text, editText.text?.length ?: 0)
                }
            }
        })
    }

    private fun addStudent() {
        val benId = etBenId.text.toString().trim()
        val firstName = etBenFirstName.text.toString().trim()
        val lastName = etBenLastName.text.toString().trim()
        val birthdate = etBenBirthdate.text.toString().trim()
        val schoolName = etBenSchoolName.text.toString().trim()
        val schoolAddress = etBenSchoolAddress.text.toString().trim()
        val grade = etBenGrade.text.toString().trim()

        val guardFirstName = etGuardFirstName.text.toString().trim()
        val guardLastName = etGuardLastName.text.toString().trim()
        val guardMobile = etGuardMobile.text.toString().trim()
        val guardAddress = etGuardAddress.text.toString().trim()
        val guardOccupation = etGuardOccupation.text.toString().trim()
        val guardBirthdate = etGuardBirthdate.text.toString().trim()
        val guardEmail = etGuardEmail.text.toString().trim()

        val username = etRegUsername.text.toString().trim()
        val password = etRegPassword.text.toString().trim()
        val confirmPass = etRegConfirmPass.text.toString().trim()

        // Validation
        if (benId.isEmpty() || benId == "PH323-") {
            etBenId.error = "PH323 ID is required"
            return
        }
        if (firstName.isEmpty()) {
            etBenFirstName.error = "First Name is required"
            return
        }
        if (lastName.isEmpty()) {
            etBenLastName.error = "Last Name is required"
            return
        }
        if (username.isEmpty() || username == "PH323-") {
            etRegUsername.error = "Username is required"
            return
        }
        if (password.isEmpty()) {
            etRegPassword.error = "Password is required"
            return
        }
        if (password != confirmPass) {
            etRegConfirmPass.error = "Passwords do not match"
            return
        }

        // Disable button while saving
        btnAddStudent.isEnabled = false
        btnAddStudent.text = "Adding..."

        // Check if username already exists
        db.collection("users").document(username).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    btnAddStudent.isEnabled = true
                    btnAddStudent.text = "Add Student"
                    etRegUsername.error = "Username already in use"
                    Toast.makeText(requireContext(), "Username is already in use", Toast.LENGTH_SHORT).show()
                } else {
                    val approvalStatus = "pending"
                    val fullName = "$firstName $lastName".trim()
                    val studentData = hashMapOf(
                        "benId" to benId,
                        "name" to fullName,
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "birthday" to birthdate,
                        "schoolName" to schoolName,
                        "schoolAddress" to schoolAddress,
                        "gradeLevel" to grade,
                        "guardianFirstName" to guardFirstName,
                        "guardianLastName" to guardLastName,
                        "phoneNumber" to guardMobile,
                        "address" to guardAddress,
                        "guardianOccupation" to guardOccupation,
                        "guardianBirthday" to guardBirthdate,
                        "email" to guardEmail,
                        "username" to username,
                        "password" to password,
                        "role" to "student",
                        "status" to approvalStatus,
                        "approvalStatus" to approvalStatus,
                        "assignedCaseworkerId" to null,
                        "assignedCaseworkerName" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    val userMap = hashMapOf(
                        "id" to username,
                        "name" to fullName,
                        "benId" to benId,
                        "FirstName" to firstName,
                        "LastName" to lastName,
                        "Birthdate" to birthdate,
                        "SchoolName" to schoolName,
                        "SchoolAddress" to schoolAddress,
                        "Grade" to grade,

                        "guardFirstName" to guardFirstName,
                        "guardLastName" to guardLastName,
                        "guardMobile" to guardMobile,
                        "guardAddress" to guardAddress,
                        "guardOccupation" to guardOccupation,
                        "guardBirthdate" to guardBirthdate,
                        "guardEmail" to guardEmail,

                        "email" to guardEmail,
                        "password" to password,
                        "role" to "student",
                        "status" to approvalStatus,
                        "approvalStatus" to approvalStatus,
                        "assignedCaseworkerId" to null,
                        "assignedCaseworkerName" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    val batch = db.batch()
                    batch.set(db.collection("users").document(username), userMap)
                    batch.set(db.collection("students").document(username), studentData)
                    batch.commit()
                        .addOnSuccessListener {
                            onStudentAddedListener?.invoke(true, "Student account created. Approval is still required.")
                            dismiss()
                        }
                        .addOnFailureListener { e ->
                            btnAddStudent.isEnabled = true
                            btnAddStudent.text = "Add Student"
                            onStudentAddedListener?.invoke(false, "Error: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                btnAddStudent.isEnabled = true
                btnAddStudent.text = "Add Student"
                onStudentAddedListener?.invoke(false, "Error: ${e.message}")
            }
    }
}
