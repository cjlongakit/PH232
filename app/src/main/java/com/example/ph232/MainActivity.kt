package com.example.ph232

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var etPH: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var sharedPreferences: SharedPreferences
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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
            db.collection("users").document(username).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
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
                            // Login Success
                            val firstName = document.getString("FirstName") ?: document.getString("firstName") ?: ""
                            val lastName = document.getString("LastName") ?: document.getString("lastName") ?: ""

                            sharedPreferences.edit()
                                .putString("USER_PH", username)
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
                    } else {
                        Toast.makeText(this, "Account not found. Please register first.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Database error. Check connection.", Toast.LENGTH_SHORT).show()
                }
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            Toast.makeText(this, "Forgot Password clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
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
}