package com.example.ph232

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var etPH: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var progressManager: ProgressManager
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dark mode preference BEFORE super.onCreate to avoid recreation loop
        val darkPrefs = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val isDarkMode = darkPrefs.getBoolean("DARK_MODE", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize loading dialog
        progressManager = ProgressManager(this)

        // Initialize Cloudinary
        CloudinaryHelper.init(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)

        // Check if user is already logged in
        val activeUser = sharedPreferences.getString("USER_PH", null)
        val activeRole = sharedPreferences.getString("USER_ROLE", null)

        if (activeUser != null) {
            when (activeRole) {
                "admin" -> startActivity(Intent(this, AdminDashboardActivity::class.java))
                "staff" -> startActivity(Intent(this, StaffDashboardActivity::class.java))
                else -> startActivity(Intent(this, DashboardActivity::class.java))
            }
            finish()
            return
        }

        // Initialize views
        etPH = findViewById(R.id.etPH)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        // Role toggle setup
        val toggleRole = findViewById<MaterialButtonToggleGroup>(R.id.toggleRole)
        val tilPH = findViewById<TextInputLayout>(R.id.tilPH)
        var selectedRole = "student"
        var prefixWatcher: TextWatcher? = null

        // Default to student
        toggleRole.check(R.id.btnRoleStudent)
        prefixWatcher = createPrefixWatcher(etPH)
        etPH.setText("PH323-")
        Selection.setSelection(etPH.text, etPH.text?.length ?: 0)
        etPH.addTextChangedListener(prefixWatcher)

        toggleRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            // Remove prefix watcher before clearing
            if (prefixWatcher != null) {
                etPH.removeTextChangedListener(prefixWatcher)
                prefixWatcher = null
            }
            etPH.text?.clear()
            when (checkedId) {
                R.id.btnRoleStudent -> {
                    selectedRole = "student"
                    tilPH.hint = "Student ID"
                    etPH.hint = "PH323-XXXX"
                    etPH.inputType = android.text.InputType.TYPE_CLASS_TEXT
                    // Attach prefix watcher
                    prefixWatcher = createPrefixWatcher(etPH)
                    etPH.setText("PH323-")
                    Selection.setSelection(etPH.text, etPH.text?.length ?: 0)
                    etPH.addTextChangedListener(prefixWatcher)
                }
                R.id.btnRoleStaff -> {
                    selectedRole = "staff"
                    tilPH.hint = "Caseworker Email"
                    etPH.hint = "example@email.com"
                    etPH.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                }
                R.id.btnRoleAdmin -> {
                    selectedRole = "admin"
                    tilPH.hint = "Admin Username"
                    etPH.hint = "Enter username"
                    etPH.inputType = android.text.InputType.TYPE_CLASS_TEXT
                }
            }
        }

        btnLogin.setOnClickListener {
            val username = etPH.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Master Admin bypass
            if (username == "admin" && password == "admin123") {
                Toast.makeText(this, "Welcome, Master Admin!", Toast.LENGTH_SHORT).show()
                sharedPreferences.edit()
                    .putString("USER_PH", username)
                    .putString("USER_ROLE", "admin")
                    .apply()
                startActivity(Intent(this, AdminDashboardActivity::class.java))
                finish()
                return@setOnClickListener
            }

            // Firebase login
            progressManager.show("Signing in...")
            btnLogin.isEnabled = false

            if (selectedRole == "staff") {
                // Staff login by email - search for user by guardEmail or email
                db.collection("users")
                    .whereEqualTo("role", "staff")
                    .get()
                    .addOnSuccessListener { documents ->
                        val matchDoc = documents.documents.firstOrNull { doc ->
                            val email = doc.getString("guardEmail") ?: doc.getString("email") ?: ""
                            email.equals(username, ignoreCase = true) || doc.id.equals(username, ignoreCase = true)
                        }
                        if (matchDoc != null) {
                            handleLoginDocument(matchDoc, password, matchDoc.id)
                        } else {
                            // Fallback: try as document ID
                            db.collection("users").document(username).get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        handleLoginDocument(document, password, username)
                                    } else {
                                        progressManager.dismiss()
                                        btnLogin.isEnabled = true
                                        Toast.makeText(this, "Staff account not found.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .addOnFailureListener {
                                    progressManager.dismiss()
                                    btnLogin.isEnabled = true
                                    Toast.makeText(this, "Database error. Check connection.", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener {
                        progressManager.dismiss()
                        btnLogin.isEnabled = true
                        Toast.makeText(this, "Database error. Check connection.", Toast.LENGTH_SHORT).show()
                    }
            } else if (selectedRole == "student") {
                // Student login - check students collection first, then users
                db.collection("students").document(username).get()
                    .addOnSuccessListener { studentDoc ->
                        if (studentDoc.exists()) {
                            handleLoginDocument(studentDoc, password, username)
                        } else {
                            // Fallback to users collection for backward compatibility
                            db.collection("users").document(username).get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        handleLoginDocument(document, password, username)
                                    } else {
                                        progressManager.dismiss()
                                        btnLogin.isEnabled = true
                                        Toast.makeText(this, "Account not found. Please register first.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .addOnFailureListener {
                                    progressManager.dismiss()
                                    btnLogin.isEnabled = true
                                    Toast.makeText(this, "Database error. Check connection.", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener {
                        progressManager.dismiss()
                        btnLogin.isEnabled = true
                        Toast.makeText(this, "Database error. Check connection.", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Admin login - check users collection
                db.collection("users").document(username).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            handleLoginDocument(document, password, username)
                        } else {
                            progressManager.dismiss()
                            btnLogin.isEnabled = true
                            Toast.makeText(this, "Account not found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        progressManager.dismiss()
                        btnLogin.isEnabled = true
                        Toast.makeText(this, "Database error. Check connection.", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            showForgotPasswordDialog()
        }

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            // Only beneficiary/student registration is allowed
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (sharedPreferences.getBoolean("SHOW_APPROVAL_DIALOG", false)) {
            sharedPreferences.edit().putBoolean("SHOW_APPROVAL_DIALOG", false).apply()
            AlertDialog.Builder(this)
                .setTitle("Registration Submitted")
                .setMessage("Waiting for approval. Contact your caseworker for any clarification.")
                .setPositiveButton("Understood", null)
                .setCancelable(false)
                .show()
        }
    }

    private fun handleLoginDocument(document: com.google.firebase.firestore.DocumentSnapshot, password: String, docId: String) {
        progressManager.dismiss()
        btnLogin.isEnabled = true

        val dbPassword = document.getString("password")
        val status = document.getString("status")
        val role = document.getString("role")

        if (dbPassword != password) {
            Toast.makeText(this, "Incorrect password!", Toast.LENGTH_SHORT).show()
        } else if (status == "pending") {
            AlertDialog.Builder(this)
                .setTitle("Account Pending")
                .setMessage("Waiting for approval. Contact your caseworker for any clarification.")
                .setPositiveButton("Understood", null)
                .show()
        } else {
            val firstName = document.getString("FirstName") ?: document.getString("firstName") ?: ""
            val lastName = document.getString("LastName") ?: document.getString("lastName") ?: ""

            sharedPreferences.edit()
                .putString("USER_PH", docId)
                .putString("USER_ROLE", role)
                .putString("USER_NAME", "$firstName $lastName".trim())
                .apply()

            when (role) {
                "admin" -> startActivity(Intent(this, AdminDashboardActivity::class.java))
                "staff" -> startActivity(Intent(this, StaffDashboardActivity::class.java))
                else -> startActivity(Intent(this, DashboardActivity::class.java))
            }
            finish()
        }
    }

    private fun createPrefixWatcher(editText: TextInputEditText): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.toString().startsWith("PH323-")) {
                    editText.setText("PH323-")
                    Selection.setSelection(editText.text, editText.text?.length ?: 0)
                }
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etEmail)
        val layoutEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEmail)
        val layoutNewPassword = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutNewPassword)
        val tvFoundUser = dialogView.findViewById<TextView>(R.id.tvFoundUser)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDescription)
        val etNewPassword = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmNewPassword = dialogView.findViewById<TextInputEditText>(R.id.etConfirmNewPassword)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSendReset = dialogView.findViewById<MaterialButton>(R.id.btnSendReset)

        var foundDocumentId: String? = null  // The username/document ID of the found user

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSendReset.setOnClickListener {
            if (foundDocumentId == null) {
                // Step 1: Look up user by guardian email
                val email = etEmail.text.toString().trim()

                if (email.isEmpty()) {
                    Toast.makeText(this, "Please enter your guardian email address", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                btnSendReset.isEnabled = false
                btnSendReset.text = "Verifying..."

                // Search for user by guardEmail field (matching RegisterActivity schema)
                db.collection("users")
                    .whereEqualTo("guardEmail", email)
                    .get()
                    .addOnSuccessListener { documents ->
                        btnSendReset.isEnabled = true
                        if (documents.isEmpty) {
                            Toast.makeText(this, "No account found with this guardian email address", Toast.LENGTH_SHORT).show()
                            btnSendReset.text = "Verify Email"
                        } else {
                            // Found user - show password reset fields
                            val doc = documents.documents[0]
                            foundDocumentId = doc.id
                            val firstName = doc.getString("FirstName") ?: ""
                            val lastName = doc.getString("LastName") ?: ""
                            val fullName = "$firstName $lastName".trim()

                            // Transition to step 2
                            tvDescription.text = "Account verified! Enter a new password below."
                            tvFoundUser.text = "Account: ${if (fullName.isNotEmpty()) fullName else doc.id}"
                            layoutEmail.visibility = android.view.View.GONE
                            layoutNewPassword.visibility = android.view.View.VISIBLE
                            btnSendReset.text = "Reset Password"
                        }
                    }
                    .addOnFailureListener { e ->
                        btnSendReset.isEnabled = true
                        btnSendReset.text = "Verify Email"
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Step 2: Reset the password
                val newPassword = etNewPassword.text.toString().trim()
                val confirmPassword = etConfirmNewPassword.text.toString().trim()

                if (newPassword.isEmpty()) {
                    etNewPassword.error = "Password is required"
                    return@setOnClickListener
                }

                if (newPassword.length < 6) {
                    etNewPassword.error = "Password must be at least 6 characters"
                    return@setOnClickListener
                }

                if (newPassword != confirmPassword) {
                    etConfirmNewPassword.error = "Passwords do not match"
                    return@setOnClickListener
                }

                btnSendReset.isEnabled = false
                btnSendReset.text = "Resetting..."

                // Update password in Firestore
                db.collection("users").document(foundDocumentId!!)
                    .update("password", newPassword)
                    .addOnSuccessListener {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Password Reset Successful")
                            .setMessage("Your password has been updated. You can now log in with your new password.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    .addOnFailureListener { e ->
                        btnSendReset.isEnabled = true
                        btnSendReset.text = "Reset Password"
                        Toast.makeText(this, "Failed to reset password: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        dialog.show()
    }
}