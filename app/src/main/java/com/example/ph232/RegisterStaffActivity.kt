package com.example.ph232

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class RegisterStaffActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var progressManager: ProgressManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_staff)

        progressManager = ProgressManager(this)

        val etFirstName = findViewById<EditText>(R.id.etStaffFirstName)
        val etLastName = findViewById<EditText>(R.id.etStaffLastName)
        val etEmail = findViewById<EditText>(R.id.etStaffEmail)
        val etMobile = findViewById<EditText>(R.id.etStaffMobile)
        val etPosition = findViewById<EditText>(R.id.etStaffPosition)
        val etUsername = findViewById<EditText>(R.id.etStaffUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etStaffPassword)
        val etConfirm = findViewById<TextInputEditText>(R.id.etStaffConfirmPass)
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitStaffRegister)

        val requiredFields = listOf<EditText>(
            etFirstName, etLastName, etEmail, etMobile, etPosition,
            etUsername, etPassword, etConfirm
        )

        for (field in requiredFields) {
            addRedAsteriskToHint(field)
        }

        btnSubmit.setOnClickListener {
            var isFormValid = true
            for (field in requiredFields) {
                if (field.text.toString().trim().isEmpty()) {
                    field.error = "Required"
                    isFormValid = false
                }
            }
            if (!isFormValid) {
                Toast.makeText(this, "Please complete all required fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val password = etPassword.text.toString().trim()
            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }
            if (!password.any { it.isUpperCase() }) {
                etPassword.error = "Must contain at least 1 uppercase letter"
                return@setOnClickListener
            }
            if (!password.any { it.isDigit() }) {
                etPassword.error = "Must contain at least 1 number"
                return@setOnClickListener
            }
            if (password != etConfirm.text.toString()) {
                etConfirm.error = "Passwords do not match"
                return@setOnClickListener
            }

            val username = etUsername.text.toString().trim()

            progressManager.show("Creating staff account...")
            btnSubmit.isEnabled = false

            db.collection("users").document(username).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        progressManager.dismiss()
                        btnSubmit.isEnabled = true
                        Toast.makeText(this, "Username already in use", Toast.LENGTH_LONG).show()
                        etUsername.error = "Already in use"
                    } else {
                        val userMap = hashMapOf(
                            "FirstName" to etFirstName.text.toString().trim(),
                            "LastName" to etLastName.text.toString().trim(),
                            "guardEmail" to etEmail.text.toString().trim(),
                            "guardMobile" to etMobile.text.toString().trim(),
                            "position" to etPosition.text.toString().trim(),
                            "password" to password,
                            "role" to "staff",
                            "status" to "pending"
                        )

                        db.collection("users").document(username).set(userMap)
                            .addOnSuccessListener {
                                progressManager.dismiss()
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
        }
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

