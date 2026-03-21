package com.example.ph232

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class ProfileDialog : DialogFragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var ivProfileAvatar: ImageView
    private var cameraImageUri: Uri? = null

    // Camera launcher → sends result to UCrop
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            launchCrop(cameraImageUri!!)
        }
    }

    // Gallery launcher → sends result to UCrop
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            launchCrop(uri)
        }
    }

    // UCrop result launcher
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

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera()
        else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(): ProfileDialog = ProfileDialog()

        fun getProfileImageFile(context: Context): File {
            val dir = File(context.filesDir, "profile_images")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "profile_photo.jpg")
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
        val tvId = view.findViewById<TextView>(R.id.tvProfileId)
        val tvFullName = view.findViewById<TextView>(R.id.tvProfileFullName)
        val tvEmail = view.findViewById<TextView>(R.id.tvProfileEmail)
        val tvStatus = view.findViewById<TextView>(R.id.tvProfileStatus)
        val tvPhone = view.findViewById<TextView>(R.id.tvProfilePhone)
        val layoutPhone = view.findViewById<LinearLayout>(R.id.layoutPhone)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseProfile)
        val btnChangePhoto = view.findViewById<MaterialCardView>(R.id.btnChangePhoto)

        val prefs = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val userId = prefs.getString("USER_PH", "") ?: ""
        val userRole = prefs.getString("USER_ROLE", "user") ?: "user"
        val userName = prefs.getString("USER_NAME", "User") ?: "User"

        // Initialize Cloudinary
        CloudinaryHelper.init(requireContext())

        // Load existing profile photo (local first, then Cloudinary)
        val localBitmap = loadProfileBitmap(requireContext())
        if (localBitmap != null) {
            ivProfileAvatar.setImageBitmap(localBitmap)
        } else {
            // Try loading from Cloudinary URL saved in prefs or Firestore
            val savedUrl = CloudinaryHelper.getSavedProfileUrl(requireContext())
            if (!savedUrl.isNullOrEmpty()) {
                CloudinaryHelper.downloadProfileImage(requireContext(), savedUrl,
                    onSuccess = { bitmap ->
                        if (isAdded) ivProfileAvatar.setImageBitmap(bitmap)
                    },
                    onError = { /* ignore, use placeholder */ }
                )
            }
        }

        tvName.text = userName.ifEmpty { userId }
        tvId.text = userId
        tvRole.text = userRole.replaceFirstChar { it.uppercase() }

        // Photo change button
        btnChangePhoto.setOnClickListener {
            showPhotoPickerDialog()
        }

        // Fetch profile from Firestore
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                if (doc.exists()) {
                    val first = doc.getString("FirstName") ?: doc.getString("firstName") ?: ""
                    val last = doc.getString("LastName") ?: doc.getString("lastName") ?: ""
                    val full = "$first $last".trim()
                    val email = doc.getString("email") ?: doc.getString("guardianEmail") ?: "—"
                    val status = doc.getString("status") ?: "active"
                    val phone = doc.getString("phone") ?: doc.getString("guardianMobile") ?: doc.getString("phoneNumber") ?: ""

                    tvName.text = full.ifEmpty { userId }
                    tvFullName.text = full.ifEmpty { "—" }
                    tvEmail.text = email
                    tvStatus.text = status.replaceFirstChar { it.uppercase() }
                    if (status.lowercase() in listOf("active", "approved")) {
                        tvStatus.setTextColor(resources.getColor(R.color.green_500, null))
                    } else {
                        tvStatus.setTextColor(resources.getColor(R.color.orange_500, null))
                    }
                    if (phone.isNotEmpty()) {
                        tvPhone.text = phone
                        layoutPhone.visibility = View.VISIBLE
                    } else {
                        layoutPhone.visibility = View.GONE
                    }
                    prefs.edit().putString("USER_NAME", full.ifEmpty { userName }).apply()

                    // Load profile image from Cloudinary if available and no local photo
                    val profileUrl = doc.getString("profileImageUrl") ?: ""
                    if (profileUrl.isNotEmpty()) {
                        prefs.edit().putString("PROFILE_IMAGE_URL", profileUrl).apply()
                        // If no local image, download from Cloudinary
                        val localFile = getProfileImageFile(requireContext())
                        if (!localFile.exists()) {
                            CloudinaryHelper.downloadProfileImage(requireContext(), profileUrl,
                                onSuccess = { bitmap ->
                                    if (isAdded) ivProfileAvatar.setImageBitmap(bitmap)
                                },
                                onError = { /* ignore */ }
                            )
                        }
                    }
                }
            }

        btnClose.setOnClickListener {
            // Notify the activity to refresh the header profile image
            (activity as? DashboardActivity)?.loadHeaderProfileImage()
            (activity as? StaffDashboardActivity)?.loadHeaderProfileImage()
            (activity as? AdminDashboardActivity)?.loadHeaderProfileImage()
            dismiss()
        }

        val dlg = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dlg
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
                // Delete local file
                val localFile = getProfileImageFile(requireContext())
                if (localFile.exists()) localFile.delete()

                // Clear saved URL
                val prefs = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
                prefs.edit().remove("PROFILE_IMAGE_URL").apply()

                // Reset avatar to placeholder
                ivProfileAvatar.setImageResource(R.drawable.ic_profile_placeholder)

                // Remove URL from Firestore
                val userId = prefs.getString("USER_PH", "") ?: ""
                if (userId.isNotEmpty()) {
                    db.collection("users").document(userId)
                        .update("profileImageUrl", "")
                }

                // Refresh header in parent activity
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
                // Save to internal storage
                val destFile = getProfileImageFile(requireContext())
                val fos = FileOutputStream(destFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
                fos.close()

                // Update avatar
                ivProfileAvatar.setImageBitmap(bitmap)

                val prefs = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
                val userId = prefs.getString("USER_PH", "unknown") ?: "unknown"

                Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()

                // Upload to Cloudinary
                CloudinaryHelper.uploadProfileImage(
                    context = requireContext(),
                    bitmap = bitmap,
                    userId = userId,
                    onSuccess = { secureUrl ->
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Profile photo saved to cloud!", Toast.LENGTH_SHORT).show()
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
