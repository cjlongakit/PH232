package com.example.ph232

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class SettingsDialog : DialogFragment() {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        fun newInstance(): SettingsDialog = SettingsDialog()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_settings, null)

        val prefs = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val userId = prefs.getString("USER_PH", "") ?: ""
        val userRole = prefs.getString("USER_ROLE", "") ?: ""

        // ==================== ACCOUNT SECTION ====================
        val tvUserId = view.findViewById<TextView>(R.id.tvUserId)
        val tvUserRole = view.findViewById<TextView>(R.id.tvUserRole)

        tvUserId.text = userId.ifEmpty { "—" }
        val displayRole = when (userRole.lowercase()) {
            "staff" -> "Caseworker"
            else -> userRole.replaceFirstChar { it.uppercase() }
        }
        tvUserRole.text = displayRole.ifEmpty { "—" }


        // ==================== APPEARANCE SECTION ====================
        val switchDarkMode = view.findViewById<MaterialSwitch>(R.id.switchDarkMode)
        val isDarkMode = prefs.getBoolean("DARK_MODE", false)
        switchDarkMode.isChecked = isDarkMode

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DARK_MODE", isChecked).apply()
            // Save current tab so activity recreates on the same screen
            val activity = requireActivity()
            if (activity is AdminDashboardActivity) {
                val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                prefs.edit().putInt("SAVED_TAB", bottomNav.selectedItemId).apply()
            } else if (activity is StaffDashboardActivity) {
                val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                prefs.edit().putInt("SAVED_TAB", bottomNav.selectedItemId).apply()
            } else if (activity is DashboardActivity) {
                val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                prefs.edit().putInt("SAVED_TAB", bottomNav.selectedItemId).apply()
            }
            dismiss()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // ==================== NOTIFICATIONS SECTION ====================
        val switchNotifications = view.findViewById<MaterialSwitch>(R.id.switchNotifications)
        val switchAttendanceAlerts = view.findViewById<MaterialSwitch>(R.id.switchAttendanceAlerts)
        val switchLetterReminders = view.findViewById<MaterialSwitch>(R.id.switchLetterReminders)

        switchNotifications.isChecked = prefs.getBoolean("NOTIFICATIONS_ENABLED", true)
        switchAttendanceAlerts.isChecked = prefs.getBoolean("ATTENDANCE_ALERTS", true)
        switchLetterReminders.isChecked = prefs.getBoolean("LETTER_REMINDERS", true)

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("NOTIFICATIONS_ENABLED", isChecked).apply()
            // When master notifications is off, disable sub-toggles
            switchAttendanceAlerts.isEnabled = isChecked
            switchLetterReminders.isEnabled = isChecked
            if (!isChecked) {
                switchAttendanceAlerts.isChecked = false
                switchLetterReminders.isChecked = false
            }
            val msg = if (isChecked) "Notifications enabled" else "Notifications disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Sync enabled state on open
        switchAttendanceAlerts.isEnabled = switchNotifications.isChecked
        switchLetterReminders.isEnabled = switchNotifications.isChecked

        switchAttendanceAlerts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("ATTENDANCE_ALERTS", isChecked).apply()
        }

        switchLetterReminders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("LETTER_REMINDERS", isChecked).apply()
        }

        // ==================== SECURITY SECTION ====================
        val etCurrentPassword = view.findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNewPassword = view.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPassword = view.findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnChangePassword = view.findViewById<MaterialButton>(R.id.btnChangePassword)

        btnChangePassword.setOnClickListener {
            val currentPass = etCurrentPassword.text.toString().trim()
            val newPass = etNewPassword.text.toString().trim()
            val confirmPass = etConfirmPassword.text.toString().trim()

            if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all password fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPass.length < 6) {
                Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                Toast.makeText(requireContext(), "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("users").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (!isAdded) return@addOnSuccessListener
                    if (doc.exists()) {
                        val dbPassword = doc.getString("password") ?: ""
                        if (dbPassword != currentPass) {
                            Toast.makeText(requireContext(), "Current password is incorrect", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        db.collection("users").document(userId)
                            .update("password", newPass)
                            .addOnSuccessListener {
                                if (!isAdded) return@addOnSuccessListener
                                Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                etCurrentPassword.text?.clear()
                                etNewPassword.text?.clear()
                                etConfirmPassword.text?.clear()
                            }
                            .addOnFailureListener {
                                if (!isAdded) return@addOnFailureListener
                                Toast.makeText(requireContext(), "Failed to update password", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(requireContext(), "User not found in database", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    if (!isAdded) return@addOnFailureListener
                    Toast.makeText(requireContext(), "Error verifying password", Toast.LENGTH_SHORT).show()
                }
        }

        // ==================== DATA & STORAGE SECTION ====================
        val btnClearCache = view.findViewById<LinearLayout>(R.id.btnClearCache)
        val tvCacheSize = view.findViewById<TextView>(R.id.tvCacheSize)
        val btnExportData = view.findViewById<LinearLayout>(R.id.btnExportData)

        // Calculate and display cache size
        val cacheSize = getCacheSize()
        tvCacheSize.text = "Cache size: $cacheSize"

        btnClearCache.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Cache")
                .setMessage("This will clear all cached data. Are you sure?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    clearCache()
                    tvCacheSize.text = "Cache size: 0 KB"
                    Toast.makeText(requireContext(), "Cache cleared successfully!", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        btnExportData.setOnClickListener {
            Toast.makeText(requireContext(), "Export feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        // ==================== ABOUT SECTION ====================
        val tvAppVersion = view.findViewById<TextView>(R.id.tvAppVersion)
        val tvBuildNumber = view.findViewById<TextView>(R.id.tvBuildNumber)

        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            tvAppVersion.text = pInfo.versionName ?: "1.0.0"
            tvBuildNumber.text = "Build ${pInfo.longVersionCode}"
        } catch (e: Exception) {
            tvAppVersion.text = "1.0.0"
            tvBuildNumber.text = "Debug"
        }

        // ==================== CLOSE BUTTON ====================
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseSettings)
        btnClose.setOnClickListener { dismiss() }

        val dlg = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dlg
    }

    private fun getCacheSize(): String {
        val cacheDir = requireContext().cacheDir
        val size = getDirSize(cacheDir)
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
        }
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isFile) file.length() else getDirSize(file)
            }
        }
        return size
    }

    private fun clearCache() {
        val cacheDir = requireContext().cacheDir
        deleteDir(cacheDir)
    }

    private fun deleteDir(dir: File): Boolean {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { child ->
                deleteDir(child)
            }
        }
        return dir.delete()
    }
}
