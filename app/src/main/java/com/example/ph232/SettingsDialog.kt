package com.example.ph232

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class SettingsDialog : DialogFragment() {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        fun newInstance(): SettingsDialog = SettingsDialog()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_settings, null)

        val switchDarkMode = view.findViewById<MaterialSwitch>(R.id.switchDarkMode)
        val etCurrentPassword = view.findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNewPassword = view.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPassword = view.findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnChangePassword = view.findViewById<MaterialButton>(R.id.btnChangePassword)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseSettings)

        val prefs = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val userId = prefs.getString("USER_PH", "") ?: ""

        // Load current dark mode state
        val isDarkMode = prefs.getBoolean("DARK_MODE", false)
        switchDarkMode.isChecked = isDarkMode

        // Dark mode toggle
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DARK_MODE", isChecked).apply()
            dismiss()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Change password
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

        btnClose.setOnClickListener { dismiss() }

        val dlg = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dlg
    }
}
