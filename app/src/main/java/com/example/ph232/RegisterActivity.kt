package com.example.ph232

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var progressManager: ProgressManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        progressManager = ProgressManager(this)

        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitRegister)

        val etBenId = findViewById<TextInputEditText>(R.id.etBenId)
        val etBenFirstName = findViewById<TextInputEditText>(R.id.etBenFirstName)
        val etBenLastName = findViewById<TextInputEditText>(R.id.etBenLastName)
        val etBenBirthdate = findViewById<TextInputEditText>(R.id.etBenBirthdate)
        val etBenSchoolName = findViewById<TextInputEditText>(R.id.etBenSchoolName)
        val etBenSchoolAddress = findViewById<TextInputEditText>(R.id.etBenSchoolAddress)
        val etBenGrade = findViewById<TextInputEditText>(R.id.etBenGrade)

        val etGuardFirstName = findViewById<TextInputEditText>(R.id.etGuardFirstName)
        val etGuardLastName = findViewById<TextInputEditText>(R.id.etGuardLastName)
        val etGuardMobile = findViewById<TextInputEditText>(R.id.etGuardMobile)
        val etGuardAddress = findViewById<TextInputEditText>(R.id.etGuardAddress)
        val etGuardOccupation = findViewById<TextInputEditText>(R.id.etGuardOccupation)
        val etGuardBirthdate = findViewById<TextInputEditText>(R.id.etGuardBirthdate)
        val etGuardEmail = findViewById<TextInputEditText>(R.id.etGuardEmail)

        val etRegUsername = findViewById<TextInputEditText>(R.id.etRegUsername)
        val etRegPassword = findViewById<TextInputEditText>(R.id.etRegPassword)
        val etConfirm = findViewById<TextInputEditText>(R.id.etRegConfirmPass)

        setupPrefix(etBenId)
        setupPrefix(etRegUsername)

        // Setup date pickers for birthdate fields
        setupDatePicker(etBenBirthdate)
        setupDatePicker(etGuardBirthdate)

        val requiredFields = listOf(
            etBenId, etBenFirstName, etBenLastName, etBenBirthdate, etBenSchoolName, etBenSchoolAddress, etBenGrade,
            etGuardFirstName, etGuardLastName, etGuardMobile, etGuardAddress, etGuardOccupation, etGuardBirthdate, etGuardEmail,
            etRegUsername, etRegPassword, etConfirm
        )

        btnSubmit.setOnClickListener {
            var isFormValid = true
            for (field in requiredFields) {
                val text = field.text.toString().trim()
                if (text.isEmpty() || text == "PH323-") {
                    field.error = "Required"
                    isFormValid = false
                }
            }

            if (!isFormValid) {
                Toast.makeText(this, "Please complete all required fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Password validation rules
            val password = etRegPassword.text.toString().trim()
            if (password.length < 6) {
                etRegPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }
            if (!password.any { it.isUpperCase() }) {
                etRegPassword.error = "Password must contain at least 1 uppercase letter"
                return@setOnClickListener
            }
            if (!password.any { it.isDigit() }) {
                etRegPassword.error = "Password must contain at least 1 number"
                return@setOnClickListener
            }

            if (password != etConfirm.text.toString()) {
                etConfirm.error = "Passwords do not match"
                return@setOnClickListener
            }

            val username = etRegUsername.text.toString().trim()
            val firstName = etBenFirstName.text.toString().trim()
            val lastName = etBenLastName.text.toString().trim()

            progressManager.show("Creating account...")
            btnSubmit.isEnabled = false

            // Check if student already exists in students collection
            db.collection("students").whereEqualTo("name", "$firstName $lastName").get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        progressManager.dismiss()
                        btnSubmit.isEnabled = true
                        Toast.makeText(this, "A student with this name already exists", Toast.LENGTH_LONG).show()
                    } else {
                        // Create student data for students collection
                        val studentData = hashMapOf(
                            "benId" to etBenId.text.toString().trim(),
                            "name" to "$firstName $lastName",
                            "firstName" to firstName,
                            "lastName" to lastName,
                            "birthday" to etBenBirthdate.text.toString().trim(),
                            "schoolName" to etBenSchoolName.text.toString().trim(),
                            "schoolAddress" to etBenSchoolAddress.text.toString().trim(),
                            "gradeLevel" to etBenGrade.text.toString().trim(),

                            "guardianFirstName" to etGuardFirstName.text.toString().trim(),
                            "guardianLastName" to etGuardLastName.text.toString().trim(),
                            "phoneNumber" to etGuardMobile.text.toString().trim(),
                            "address" to etGuardAddress.text.toString().trim(),
                            "guardianOccupation" to etGuardOccupation.text.toString().trim(),
                            "guardianBirthday" to etGuardBirthdate.text.toString().trim(),
                            "email" to etGuardEmail.text.toString().trim(),

                            "username" to username,
                            "password" to password,
                            "role" to "beneficiary",
                            "status" to "active",
                            "createdAt" to System.currentTimeMillis()
                        )

                        // Save directly to students collection
                        db.collection("students").document(username).set(studentData)
                            .addOnSuccessListener {
                                progressManager.dismiss()
                                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                val prefs = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("SHOW_APPROVAL_DIALOG", true).apply()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                progressManager.dismiss()
                                btnSubmit.isEnabled = true
                                Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    progressManager.dismiss()
                    btnSubmit.isEnabled = true
                    Toast.makeText(this, "Error checking: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupDatePicker(editText: TextInputEditText) {
        editText.setOnClickListener {
            showDatePickerDialog(editText)
        }
        // Also handle click on the parent TextInputLayout's end icon
        (editText.parent?.parent as? TextInputLayout)?.setEndIconOnClickListener {
            showDatePickerDialog(editText)
        }
    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
            editText.setText(sdf.format(cal.time))
        }, cal.get(Calendar.YEAR) - 15, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
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
}

