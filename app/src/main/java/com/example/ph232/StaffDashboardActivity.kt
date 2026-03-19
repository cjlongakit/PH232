package com.example.ph232

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class StaffDashboardActivity : AppCompatActivity() {

    private lateinit var tvHeaderTitle: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var profileCard: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_dashboard)

        tvHeaderTitle = findViewById(R.id.tvStaffHeaderTitle)
        bottomNavigation = findViewById(R.id.staffBottomNavigation)
        profileCard = findViewById(R.id.staffProfileCard)

        // Set the default tab when the app opens
        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_staff_attendance
            loadFragment(StaffAttendanceFragment())
        }

        // Intercept back button to prevent accidental exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLogoutWarning()
            }
        })

        // Logout when profile picture is clicked
        profileCard.setOnClickListener { view ->
            showProfileMenu(view)
        }

        // Setup bottom navigation routing
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_staff_attendance -> {
                    tvHeaderTitle.text = "Today's Attendance"
                    loadFragment(StaffAttendanceFragment())
                    true
                }
                R.id.nav_staff_letters -> {
                    tvHeaderTitle.text = "Letter Management"
                    loadFragment(StaffLettersFragment())
                    true
                }
                R.id.nav_staff_events -> {
                    tvHeaderTitle.text = "Event Manager"
                    loadFragment(AdminEventsFragment()) // Reuse admin events fragment
                    true
                }
                else -> false
            }
        }
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
                    showLogoutWarning()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.staffFragmentContainer, fragment)
            .commit()
    }


    private fun showLogoutWarning() {
        AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out of the Caseworker Portal?")
            .setPositiveButton("Yes") { _, _ -> logout() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun logout() {
        val sharedPreferences = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

