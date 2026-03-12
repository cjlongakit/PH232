package com.example.ph232

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DashboardActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var tvHeaderTitle: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var fabQrScanner: FloatingActionButton
    private lateinit var profileCard: CardView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showQrScannerDialog()
        } else {
            Toast.makeText(this, "Camera permission required for QR scanning", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragmentContainer)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)

        // Initialize views
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        fabQrScanner = findViewById(R.id.fabQrScanner)
        profileCard = findViewById(R.id.profileCard)

        // Setup Profile dropdown menu
        profileCard.setOnClickListener { view ->
            showProfileMenu(view)
        }

        // Setup QR Scanner FAB
        fabQrScanner.setOnClickListener {
            openQrScanner()
        }

        // Setup bottom navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    tvHeaderTitle.text = "Dashboard"
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.nav_letters -> {
                    tvHeaderTitle.text = "Letters"
                    loadFragment(LettersFragment())
                    true
                }
                R.id.nav_events -> {
                    tvHeaderTitle.text = "Events"
                    loadFragment(EventsFragment())
                    true
                }
                else -> false
            }
        }

        // Set default selection
        bottomNavigation.selectedItemId = R.id.nav_dashboard
    }

    private fun showProfileMenu(anchor: View) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menuInflater.inflate(R.menu.profile_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_profile -> {
                    Toast.makeText(this, "Profile clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_settings -> {
                    Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_terms -> {
                    Toast.makeText(this, "Terms & Agreements clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun openQrScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            showQrScannerDialog()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showQrScannerDialog() {
        val studentId = sharedPreferences.getString("USER_PH", "Unknown") ?: "Unknown"
        val studentName = sharedPreferences.getString("USER_NAME", "") ?: ""
        val dialog = QrScannerDialog.newInstance(studentId, studentName)
        dialog.setOnAttendanceRecordedListener { success, message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(supportFragmentManager, "QrScannerDialog")
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun logout() {
        // Clear stored credentials
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()

        // Navigate back to login
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
