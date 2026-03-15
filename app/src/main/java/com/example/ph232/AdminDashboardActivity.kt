package com.example.ph232

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var tvHeaderTitle: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var fabQrGenerator: FloatingActionButton
    private lateinit var profileCard: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin_dashboard)

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
        fabQrGenerator = findViewById(R.id.fabQrGenerator)
        profileCard = findViewById(R.id.profileCard)

        // Setup Profile dropdown menu
        profileCard.setOnClickListener { view ->
            showProfileMenu(view)
        }

        // Setup QR Generator FAB
        fabQrGenerator.setOnClickListener {
            showQrGeneratorDialog()
        }

        // Setup bottom navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    tvHeaderTitle.text = "Dashboard"
                    loadFragment(AdminDashboardFragment())
                    true
                }
                R.id.nav_attendance -> {
                    tvHeaderTitle.text = "Attendance Management"
                    loadFragment(AdminAttendanceFragment())
                    true
                }
                R.id.nav_letters -> {
                    tvHeaderTitle.text = "Letters"
                    loadFragment(AdminLettersFragment())
                    true
                }
                R.id.nav_events -> {
                    tvHeaderTitle.text = "Events"
                    loadFragment(AdminEventsFragment())
                    true
                }
                R.id.nav_students -> {
                    tvHeaderTitle.text = "All Students"
                    loadFragment(AdminStudentsFragment())
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

    private fun showQrGeneratorDialog() {
        val dialog = QrGeneratorDialog.newInstance("Attendance")
        dialog.show(supportFragmentManager, "QrGeneratorDialog")
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
