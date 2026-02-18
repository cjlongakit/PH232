package com.example.ph232

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var etPH: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize SharedPreferences for local storage
        sharedPreferences = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)

        // Check if user is already logged in
        if (isUserLoggedIn()) {
            navigateToDashboard()
            return
        }

        // Initialize views
        etPH = findViewById(R.id.etPH)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        // Set click listeners
        btnLogin.setOnClickListener {
            val ph = etPH.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (ph.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else if (!isValidPHFormat(ph)) {
                Toast.makeText(this, "Invalid PH format. Use 3 digits (e.g., 001, 002)", Toast.LENGTH_SHORT).show()
            } else {
                // Save credentials locally
                saveCredentials(ph, password)
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                navigateToDashboard()
            }
        }

        findViewById<android.widget.TextView>(R.id.tvForgotPassword).setOnClickListener {
            Toast.makeText(this, "Forgot Password clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.widget.TextView>(R.id.tvRegister).setOnClickListener {
            Toast.makeText(this, "Register clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isValidPHFormat(ph: String): Boolean {
        // Only accept 3-digit numbers like 001, 002, 010, 100, etc.
        val regex = Regex("^\\d{3}$")
        return regex.matches(ph)
    }

    private fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getString("USER_PH", null) != null
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun saveCredentials(ph: String, password: String) {
        val editor = sharedPreferences.edit()
        editor.putString("USER_PH", ph)
        editor.putString("USER_PASSWORD", password)
        editor.apply()
    }
}