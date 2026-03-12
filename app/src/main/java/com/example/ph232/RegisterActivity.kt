package com.example.ph232

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitRegister)

        val etBenId = findViewById<EditText>(R.id.etBenId)
        val etBenFirstName = findViewById<EditText>(R.id.etBenFirstName)
        val etBenLastName = findViewById<EditText>(R.id.etBenLastName)
        val etBenBirthdate = findViewById<EditText>(R.id.etBenBirthdate)
        val etBenSchoolName = findViewById<EditText>(R.id.etBenSchoolName)
        val etBenSchoolAddress = findViewById<EditText>(R.id.etBenSchoolAddress)
        val etBenGrade = findViewById<EditText>(R.id.etBenGrade)

        val etGuardFirstName = findViewById<EditText>(R.id.etGuardFirstName)
        val etGuardLastName = findViewById<EditText>(R.id.etGuardLastName)
        val etGuardMobile = findViewById<EditText>(R.id.etGuardMobile)
        val etGuardAddress = findViewById<EditText>(R.id.etGuardAddress)
        val etGuardOccupation = findViewById<EditText>(R.id.etGuardOccupation)
        val etGuardBirthdate = findViewById<EditText>(R.id.etGuardBirthdate)
        val etGuardEmail = findViewById<EditText>(R.id.etGuardEmail)

        val etRegUsername = findViewById<EditText>(R.id.etRegUsername)
        val etRegPassword = findViewById<EditText>(R.id.etRegPassword)
        val etConfirm = findViewById<EditText>(R.id.etRegConfirmPass)

        setupPrefix(etBenId)
        setupPrefix(etRegUsername)

        val requiredFields = listOf(
            etBenId, etBenFirstName, etBenLastName, etBenBirthdate, etBenSchoolName, etBenSchoolAddress, etBenGrade,
            etGuardFirstName, etGuardLastName, etGuardMobile, etGuardAddress, etGuardOccupation, etGuardBirthdate, etGuardEmail,
            etRegUsername, etRegPassword, etConfirm
        )

        for (field in requiredFields) {
            addRedAsteriskToHint(field)
        }

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

            if (etRegPassword.text.toString() != etConfirm.text.toString()) {
                etConfirm.error = "Passwords do not match"
                return@setOnClickListener
            }

            val username = etRegUsername.text.toString().trim()
            val password = etRegPassword.text.toString().trim()

            db.collection("users").document(username).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        Toast.makeText(this, "Username is already in use", Toast.LENGTH_LONG).show()
                        etRegUsername.error = "Already in use"
                    } else {
                        val userMap = hashMapOf(
                            "benId" to etBenId.text.toString().trim(),
                            "FirstName" to etBenFirstName.text.toString().trim(),
                            "LastName" to etBenLastName.text.toString().trim(),
                            "Birthdate" to etBenBirthdate.text.toString().trim(),
                            "SchoolName" to etBenSchoolName.text.toString().trim(),
                            "SchoolAddress" to etBenSchoolAddress.text.toString().trim(),
                            "Grade" to etBenGrade.text.toString().trim(),

                            "guardFirstName" to etGuardFirstName.text.toString().trim(),
                            "guardLastName" to etGuardLastName.text.toString().trim(),
                            "guardMobile" to etGuardMobile.text.toString().trim(),
                            "guardAddress" to etGuardAddress.text.toString().trim(),
                            "guardOccupation" to etGuardOccupation.text.toString().trim(),
                            "guardBirthdate" to etGuardBirthdate.text.toString().trim(),
                            "guardEmail" to etGuardEmail.text.toString().trim(),

                            "password" to password,
                            "role" to "beneficiary",
                            "status" to "pending"
                        )

                        db.collection("users").document(username).set(userMap)
                            .addOnSuccessListener {
                                val prefs = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("SHOW_APPROVAL_DIALOG", true).apply()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
        }
    }

    private fun setupPrefix(editText: EditText) {
        editText.setText("PH323-")
        Selection.setSelection(editText.text, editText.text.length)
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.toString().startsWith("PH323-")) {
                    editText.setText("PH323-")
                    Selection.setSelection(editText.text, editText.text.length)
                }
            }
        })
    }

    private fun addRedAsteriskToHint(editText: EditText) {
        val currentHint = editText.hint?.toString() ?: ""
        val builder = SpannableStringBuilder(currentHint)
        val asterisk = SpannableString(" *")
        asterisk.setSpan(ForegroundColorSpan(Color.RED), 0, asterisk.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append(asterisk)
        editText.hint = builder
    }
}

