package com.example.ph232

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileDialog : DialogFragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var ivProfileAvatar: ImageView
    private var cameraImageUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            launchCrop(cameraImageUri!!)
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            launchCrop(uri)
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val croppedUri = UCrop.getOutput(result.data!!)
            if (croppedUri != null) {
                saveProfileImage(croppedUri)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR && result.data != null) {
            val error = UCrop.getError(result.data!!)
            Toast.makeText(requireContext(), "Crop failed: ${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera()
        else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(): ProfileDialog = ProfileDialog()

        private fun getCurrentUserId(context: Context): String {
            val prefs = context.getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
            return prefs.getString("USER_PH", "unknown") ?: "unknown"
        }

        fun getProfileImageFile(context: Context): File {
            val userId = getCurrentUserId(context)
            val dir = File(context.filesDir, "profile_images")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "profile_photo_$userId.jpg")
        }

        fun loadProfileBitmap(context: Context): Bitmap? {
            val file = getProfileImageFile(context)
            return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile, null)
        ivProfileAvatar = view.findViewById(R.id.ivProfileAvatar)

        val tvName = view.findViewById<TextView>(R.id.tvProfileName)
        val tvRole = view.findViewById<TextView>(R.id.tvProfileRole)
        val tvIdLabel = view.findViewById<TextView>(R.id.tvProfileIdLabel)
        val tvId = view.findViewById<TextView>(R.id.tvProfileId)
        val tvStatus = view.findViewById<TextView>(R.id.tvProfileStatus)
        val tvPosition = view.findViewById<TextView>(R.id.tvProfilePosition)
        val tvFullName = view.findViewById<TextView>(R.id.tvProfileFullName)
        val tvBirthdate = view.findViewById<TextView>(R.id.tvProfileBirthdate)
        val tvSchoolName = view.findViewById<TextView>(R.id.tvProfileSchoolName)
        val tvSchoolAddress = view.findViewById<TextView>(R.id.tvProfileSchoolAddress)
        val tvGrade = view.findViewById<TextView>(R.id.tvProfileGrade)
        val tvGuardianName = view.findViewById<TextView>(R.id.tvProfileGuardianName)
        val tvEmail = view.findViewById<TextView>(R.id.tvProfileEmail)
        val tvPhone = view.findViewById<TextView>(R.id.tvProfilePhone)
        val tvGuardianAddress = view.findViewById<TextView>(R.id.tvProfileGuardianAddress)
        val tvGuardianOccupation = view.findViewById<TextView>(R.id.tvProfileGuardianOccupation)
        val tvGuardianBirthdate = view.findViewById<TextView>(R.id.tvProfileGuardianBirthdate)
        val layoutPosition = view.findViewById<LinearLayout>(R.id.layoutPosition)
        val layoutPhone = view.findViewById<LinearLayout>(R.id.layoutPhone)
        val cardBeneficiaryDetails = view.findViewById<MaterialCardView>(R.id.cardBeneficiaryDetails)
        val cardGuardianDetails = view.findViewById<MaterialCardView>(R.id.cardGuardianDetails)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseProfile)
        val btnChangePhoto = view.findViewById<MaterialCardView>(R.id.btnChangePhoto)
        val btnEditProfile = view.findViewById<MaterialButton>(R.id.btnEditProfile)

        val prefs = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val userId = prefs.getString("USER_PH", "") ?: ""
        val userRole = prefs.getString("USER_ROLE", "user") ?: "user"
        val userName = prefs.getString("USER_NAME", "User") ?: "User"
        val isStudent = userRole.equals("beneficiary", ignoreCase = true) || userRole.equals("student", ignoreCase = true)

        tvIdLabel.text = when {
            userRole.equals("staff", ignoreCase = true) -> "Caseworker Email"
            userRole.equals("admin", ignoreCase = true) -> "Admin Username"
            else -> "PH323 ID"
        }

        CloudinaryHelper.init(requireContext())
        loadAvatar()

        tvName.text = userName.ifEmpty { userId }
        tvRole.text = userRole.replaceFirstChar { it.uppercase() }
        tvId.text = userId
        tvStatus.text = "Not set"
        tvFullName.text = userName.ifEmpty { "Not set" }
        tvBirthdate.text = "Not set"
        tvSchoolName.text = "Not set"
        tvSchoolAddress.text = "Not set"
        tvGrade.text = "Not set"
        tvGuardianName.text = "Not set"
        tvEmail.text = "Not set"
        tvPhone.text = "Not set"
        tvGuardianAddress.text = "Not set"
        tvGuardianOccupation.text = "Not set"
        tvGuardianBirthdate.text = "Not set"
        tvPosition.text = "Not set"

        cardBeneficiaryDetails.visibility = if (isStudent) View.VISIBLE else View.GONE
        cardGuardianDetails.visibility = if (isStudent) View.VISIBLE else View.GONE
        layoutPosition.visibility = if (isStudent) View.GONE else View.VISIBLE
        layoutPhone.visibility = View.VISIBLE

        btnChangePhoto.setOnClickListener {
            showPhotoPickerDialog()
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (!isAdded || !doc.exists()) return@addOnSuccessListener

                val first = valueOrBlank(doc.getString("FirstName"), doc.getString("firstName"))
                val last = valueOrBlank(doc.getString("LastName"), doc.getString("lastName"))
                val fullName = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { userName.ifEmpty { userId } }
                val resolvedRole = valueOrBlank(doc.getString("role"), userRole).ifBlank { userRole }
                val resolvedStatus = valueOrBlank(doc.getString("status"), "active").ifBlank { "active" }
                val email = valueOrBlank(doc.getString("email"), doc.getString("guardianEmail"), doc.getString("guardEmail"))
                val phone = valueOrBlank(doc.getString("phone"), doc.getString("guardianMobile"), doc.getString("guardMobile"), doc.getString("phoneNumber"))
                val birthdate = valueOrBlank(doc.getString("birthdate"), doc.getString("Birthdate"))
                val schoolName = valueOrBlank(doc.getString("schoolName"), doc.getString("SchoolName"))
                val schoolAddress = valueOrBlank(doc.getString("schoolAddress"), doc.getString("SchoolAddress"))
                val grade = valueOrBlank(doc.getString("grade"), doc.getString("Grade"))
                val guardFirst = valueOrBlank(doc.getString("guardFirstName"), doc.getString("GuardianFirstName"))
                val guardLast = valueOrBlank(doc.getString("guardLastName"), doc.getString("GuardianLastName"))
                val guardianName = listOf(guardFirst, guardLast).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Not set" }
                val guardianAddress = valueOrBlank(doc.getString("guardAddress"), doc.getString("guardianAddress"))
                val guardianOccupation = valueOrBlank(doc.getString("guardOccupation"), doc.getString("guardianOccupation"))
                val guardianBirthdate = valueOrBlank(doc.getString("guardBirthdate"), doc.getString("guardianBirthdate"))
                val position = valueOrBlank(doc.getString("position"), doc.getString("Position"))
                val isStaffUser = resolvedRole.equals("staff", ignoreCase = true) || resolvedRole.equals("caseworker", ignoreCase = true)

                val displayRole = when {
                    resolvedRole.equals("staff", ignoreCase = true) || resolvedRole.equals("caseworker", ignoreCase = true) -> "Caseworker"
                    resolvedRole.equals("admin", ignoreCase = true) -> "Admin"
                    else -> "Student"
                }

                tvName.text = fullName
                tvRole.text = displayRole
                tvId.text = when {
                    isStaffUser -> email.ifBlank { userId.ifBlank { "Not set" } }
                    resolvedRole.equals("admin", ignoreCase = true) -> userId.ifBlank { "Not set" }
                    else -> userId.ifBlank { "Not set" }
                }
                tvStatus.text = resolvedStatus.replaceFirstChar { it.uppercase() }
                tvFullName.text = fullName
                tvBirthdate.text = birthdate.ifBlank { "Not set" }
                tvSchoolName.text = schoolName.ifBlank { "Not set" }
                tvSchoolAddress.text = schoolAddress.ifBlank { "Not set" }
                tvGrade.text = grade.ifBlank { "Not set" }
                tvGuardianName.text = guardianName
                tvEmail.text = email.ifBlank { "Not set" }
                tvPhone.text = phone.ifBlank { "Not set" }
                tvGuardianAddress.text = guardianAddress.ifBlank { "Not set" }
                tvGuardianOccupation.text = guardianOccupation.ifBlank { "Not set" }
                tvGuardianBirthdate.text = guardianBirthdate.ifBlank { "Not set" }
                tvPosition.text = position.ifBlank { "Not set" }

                layoutPhone.visibility = View.VISIBLE
                layoutPosition.visibility = if (isStudent) View.GONE else View.VISIBLE

                if (resolvedStatus.lowercase() in listOf("active", "approved")) {
                    tvStatus.setTextColor(resources.getColor(R.color.green_500, null))
                } else {
                    tvStatus.setTextColor(resources.getColor(R.color.orange_500, null))
                }

                prefs.edit().putString("USER_NAME", fullName).apply()

                if (isStaffUser) {
                    db.collection("staff").document(userId).get()
                        .addOnSuccessListener { staffDoc ->
                            if (!isAdded || !staffDoc.exists()) return@addOnSuccessListener
                            val staffName = valueOrBlank(staffDoc.getString("name")).ifBlank { fullName }
                            val staffEmail = valueOrBlank(staffDoc.getString("email"), email)
                            val staffPhone = valueOrBlank(staffDoc.getString("phone"), phone)
                            val staffPosition = valueOrBlank(staffDoc.getString("position"), position)
                            tvName.text = staffName
                            tvFullName.text = staffName
                            tvEmail.text = staffEmail.ifBlank { "Not set" }
                            tvPhone.text = staffPhone.ifBlank { "Not set" }
                            tvId.text = staffEmail.ifBlank { userId.ifBlank { "Not set" } }
                            tvPosition.text = staffPosition.ifBlank { "Not set" }
                            prefs.edit().putString("USER_NAME", staffName).apply()
                        }
                }

                val profileUrl = doc.getString("profileImageUrl") ?: ""
                if (profileUrl.isNotEmpty()) {
                    prefs.edit().putString("PROFILE_IMAGE_URL_$userId", profileUrl).apply()
                    val localFile = getProfileImageFile(requireContext())
                    if (!localFile.exists()) {
                        CloudinaryHelper.downloadProfileImage(
                            requireContext(),
                            profileUrl,
                            onSuccess = { bitmap -> if (isAdded) ivProfileAvatar.setImageBitmap(bitmap) },
                            onError = { }
                        )
                    }
                }
            }

        if (userRole.lowercase() in listOf("admin", "staff")) {
            btnEditProfile.visibility = View.VISIBLE
        }

        btnEditProfile.setOnClickListener {
            showEditProfileDialog(userId, tvName, tvFullName, tvEmail, tvPhone, layoutPhone)
        }

        btnClose.setOnClickListener {
            (activity as? DashboardActivity)?.loadHeaderProfileImage()
            (activity as? StaffDashboardActivity)?.loadHeaderProfileImage()
            (activity as? AdminDashboardActivity)?.loadHeaderProfileImage()
            dismiss()
        }

        val dlg = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dlg
    }

    private fun loadAvatar() {
        val localBitmap = loadProfileBitmap(requireContext())
        if (localBitmap != null) {
            ivProfileAvatar.setImageBitmap(localBitmap)
            return
        }

        val savedUrl = CloudinaryHelper.getSavedProfileUrl(requireContext())
        if (!savedUrl.isNullOrEmpty()) {
            CloudinaryHelper.downloadProfileImage(
                requireContext(),
                savedUrl,
                onSuccess = { bitmap -> if (isAdded) ivProfileAvatar.setImageBitmap(bitmap) },
                onError = { }
            )
        }
    }

    private fun valueOrBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }

    private fun showEditProfileDialog(
        userId: String,
        tvName: TextView,
        tvFullName: TextView,
        tvEmail: TextView,
        tvPhone: TextView,
        layoutPhone: LinearLayout
    ) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)
        val etFirstName = dialogView.findViewById<TextInputEditText>(R.id.etEditProfileFirstName)
        val etLastName = dialogView.findViewById<TextInputEditText>(R.id.etEditProfileLastName)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etEditProfileEmail)
        val etPhoneEdit = dialogView.findViewById<TextInputEditText>(R.id.etEditProfilePhone)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelEditProfile)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEditProfile)

        val fullNameParts = tvFullName.text.toString().takeIf { it != "Not set" }.orEmpty().trim().split(" ")
        etFirstName.setText(fullNameParts.firstOrNull().orEmpty())
        etLastName.setText(fullNameParts.drop(1).joinToString(" "))
        etEmail.setText(tvEmail.text.toString().takeIf { it != "Not set" }.orEmpty())
        etPhoneEdit.setText(tvPhone.text.toString().takeIf { it != "Not set" }.orEmpty())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhoneEdit.text.toString().trim()

            if (firstName.isEmpty()) {
                etFirstName.error = "First Name is required"
                etFirstName.requestFocus()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            btnSave.text = "Saving..."

            val updates = mutableMapOf<String, Any>(
                "FirstName" to firstName,
                "LastName" to lastName
            )
            if (email.isNotEmpty()) updates["email"] = email else updates["email"] = ""
            if (phone.isNotEmpty()) updates["phone"] = phone else updates["phone"] = ""

            db.collection("users").document(userId).update(updates)
                .addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    val fullName = "$firstName $lastName".trim()
                    tvName.text = fullName
                    tvFullName.text = fullName
                    tvEmail.text = email.ifEmpty { "Not set" }
                    tvPhone.text = phone.ifEmpty { "Not set" }
                    layoutPhone.visibility = View.VISIBLE
                    requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putString("USER_NAME", fullName)
                        .apply()
                    Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    btnSave.isEnabled = true
                    btnSave.text = "Save Changes"
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showPhotoPickerDialog() {
        val hasPhoto = getProfileImageFile(requireContext()).exists()
        val options = if (hasPhoto) {
            arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        } else {
            arrayOf("Take Photo", "Choose from Gallery")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                            openCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> pickImageLauncher.launch("image/*")
                    2 -> removeProfilePhoto()
                }
            }
            .show()
    }

    private fun removeProfilePhoto() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Profile Photo")
            .setMessage("Are you sure you want to remove your profile photo?")
            .setPositiveButton("Remove") { _, _ ->
                val localFile = getProfileImageFile(requireContext())
                if (localFile.exists()) localFile.delete()

                val prefs = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
                val userId = prefs.getString("USER_PH", "unknown") ?: "unknown"
                prefs.edit().remove("PROFILE_IMAGE_URL_$userId").apply()

                ivProfileAvatar.setImageResource(R.drawable.ic_profile_placeholder)

                if (userId.isNotEmpty() && userId != "unknown") {
                    db.collection("users").document(userId)
                        .update("profileImageUrl", "")
                }

                (activity as? DashboardActivity)?.loadHeaderProfileImage()
                (activity as? StaffDashboardActivity)?.loadHeaderProfileImage()
                (activity as? AdminDashboardActivity)?.loadHeaderProfileImage()

                Toast.makeText(requireContext(), "Profile photo removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openCamera() {
        val imageFile = createTempImageFile("CAMERA")
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )
        takePictureLauncher.launch(cameraImageUri!!)
    }

    private fun launchCrop(sourceUri: Uri) {
        val destFile = createTempImageFile("CROPPED")
        val destUri = Uri.fromFile(destFile)

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setCircleDimmedLayer(true)
            setShowCropGrid(false)
            setShowCropFrame(false)
            setToolbarTitle("Crop Profile Photo")
            setToolbarColor(Color.parseColor("#6D28D9"))
            setStatusBarColor(Color.parseColor("#4C1D95"))
            setActiveControlsWidgetColor(Color.parseColor("#6D28D9"))
        }

        val cropIntent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)
            .withOptions(options)
            .getIntent(requireContext())

        cropLauncher.launch(cropIntent)
    }

    private fun createTempImageFile(prefix: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(requireContext().cacheDir, "profile_images")
        if (!dir.exists()) dir.mkdirs()
        return File.createTempFile("${prefix}_${timeStamp}_", ".jpg", dir)
    }

    private fun saveProfileImage(sourceUri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(sourceUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val destFile = getProfileImageFile(requireContext())
                val fos = FileOutputStream(destFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
                fos.close()

                ivProfileAvatar.setImageBitmap(bitmap)

                val prefs = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
                val userId = prefs.getString("USER_PH", "unknown") ?: "unknown"

                Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()

                CloudinaryHelper.uploadProfileImage(
                    context = requireContext(),
                    bitmap = bitmap,
                    userId = userId,
                    onSuccess = { secureUrl ->
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Already Saved!", Toast.LENGTH_SHORT).show()
                        }
                        db.collection("users").document(userId)
                            .update("profileImageUrl", secureUrl)
                            .addOnFailureListener {
                                db.collection("users").document(userId)
                                    .set(mapOf("profileImageUrl" to secureUrl), com.google.firebase.firestore.SetOptions.merge())
                            }
                    },
                    onError = { errorMsg ->
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Cloud upload failed: $errorMsg\nPhoto saved locally.", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to save photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
