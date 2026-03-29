package com.example.ph232

import android.app.Dialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class QrGeneratorDialog : DialogFragment() {

    private lateinit var tvEventName: TextView
    private lateinit var tvQrCodeValue: TextView
    private lateinit var tvExpirationTime: TextView
    private lateinit var ivQrCode: ImageView
    private lateinit var btnNewCode: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var btnClose: MaterialButton
    private lateinit var repository: FirebaseRepository

    private var currentQrCode: String = ""
    private var currentSessionId: String = ""
    private var currentQrBitmap: Bitmap? = null
    private var eventName: String = "Attendance"
    private var eventId: String = ""
    private var adminId: String = ""
    private var adminName: String = ""

    companion object {
        fun newInstance(
            eventName: String = "Attendance",
            eventId: String = "",
            adminId: String = "",
            adminName: String = ""
        ): QrGeneratorDialog {
            return QrGeneratorDialog().apply {
                arguments = Bundle().apply {
                    putString("EVENT_NAME", eventName)
                    putString("EVENT_ID", eventId)
                    putString("ADMIN_ID", adminId)
                    putString("ADMIN_NAME", adminName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventName = arguments?.getString("EVENT_NAME") ?: "Attendance"
        eventId = arguments?.getString("EVENT_ID") ?: ""
        adminId = arguments?.getString("ADMIN_ID") ?: ""
        adminName = arguments?.getString("ADMIN_NAME") ?: "Admin"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_qr_generator, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvEventName = view.findViewById(R.id.tvEventName)
        tvQrCodeValue = view.findViewById(R.id.tvQrCodeValue)
        tvExpirationTime = view.findViewById(R.id.tvExpirationTime)
        ivQrCode = view.findViewById(R.id.ivQrCode)
        btnNewCode = view.findViewById(R.id.btnNewCode)
        btnDownload = view.findViewById(R.id.btnDownload)
        btnClose = view.findViewById(R.id.btnClose)

        repository = FirebaseRepository.getInstance()

        if (adminId.isEmpty()) {
            val prefs = requireActivity().getSharedPreferences("PH232_PREFS", android.content.Context.MODE_PRIVATE)
            adminId = prefs.getString("USER_PH", "admin") ?: "admin"
            adminName = prefs.getString("USER_NAME", "Admin") ?: "Admin"
        }

        tvEventName.text = eventName

        if (eventId.isNotEmpty()) {
            loadExistingQrCode()
        } else {
            loadActiveSessionOrGenerate()
        }

        btnNewCode.setOnClickListener { generateNewQrCode() }
        btnDownload.setOnClickListener { downloadQrCode() }
        btnClose.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun loadExistingQrCode() {
        FirebaseFirestore.getInstance()
            .collection("events")
            .document(eventId)
            .get()
            .addOnSuccessListener { document ->
                val existingQrCode = document.getString("qrCode") ?: ""
                if (existingQrCode.isNotEmpty()) {
                    ensureActiveQrSession(existingQrCode)
                } else {
                    generateNewQrCode()
                }
            }
            .addOnFailureListener {
                generateNewQrCode()
            }
    }

    private fun ensureActiveQrSession(qrCode: String) {
        repository.validateQrCode(
            scannedQrCode = qrCode,
            onSuccess = { existingSession ->
                if (existingSession != null && isSessionForToday(existingSession)) {
                    showQrSession(existingSession)
                } else {
                    createSessionForCode(qrCode)
                }
            },
            onFailure = {
                createSessionForCode(qrCode)
            }
        )
    }

    private fun loadActiveSessionOrGenerate() {
        repository.getActiveQrSession(
            onSuccess = { session ->
                if (session != null && isSessionForToday(session)) {
                    showQrSession(session)
                } else {
                    generateNewQrCode()
                }
            },
            onFailure = {
                generateNewQrCode()
            }
        )
    }

    private fun createSessionForCode(qrCode: String) {
        currentQrCode = qrCode
        renderQrCode(currentQrCode)
        val expiresAt = getEndOfDayMillis()
        repository.createQrSession(
            qrCode = qrCode,
            eventId = eventId,
            eventName = eventName,
            createdBy = adminId,
            createdByName = adminName,
            expiresAtMillis = expiresAt,
            onSuccess = { sessionId ->
                currentSessionId = sessionId
                updateExpirationText(expiresAt)
            },
            onFailure = {
                updateExpirationText(expiresAt)
            }
        )
    }

    private fun generateNewQrCode() {
        val dateKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val random = UUID.randomUUID().toString().take(8)
        val expiresAt = getEndOfDayMillis()
        currentQrCode = "ATTENDANCE_${dateKey}_$random"

        try {
            renderQrCode(currentQrCode)
            repository.createQrSession(
                qrCode = currentQrCode,
                eventId = eventId,
                eventName = eventName,
                createdBy = adminId,
                createdByName = adminName,
                expiresAtMillis = expiresAt,
                onSuccess = { sessionId ->
                    currentSessionId = sessionId
                    updateExpirationText(expiresAt)
                    Toast.makeText(requireContext(), "New QR code generated for today", Toast.LENGTH_SHORT).show()
                    if (eventId.isNotEmpty()) {
                        saveEventWithQrCode()
                    }
                },
                onFailure = {
                    updateExpirationText(expiresAt)
                    Toast.makeText(requireContext(), "Warning: QR session not saved to database", Toast.LENGTH_SHORT).show()
                    saveQrCodeToFirestore()
                }
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error generating QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderQrCode(qrCode: String) {
        currentQrBitmap = generateQrBitmap(qrCode, 512)
        ivQrCode.setImageBitmap(currentQrBitmap)
        tvQrCodeValue.text = "Token: $qrCode"
    }

    private fun showQrSession(session: QrSession) {
        currentQrCode = session.qrCode
        currentSessionId = session.id
        renderQrCode(currentQrCode)
        updateExpirationText(session.expiresAt)
    }

    private fun updateExpirationText(expiresAt: Long) {
        tvExpirationTime.text = "Valid until ${formatExpiry(expiresAt)}"
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    private fun isSessionForToday(session: QrSession): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return session.date == today && (session.expiresAt <= 0 || System.currentTimeMillis() <= session.expiresAt)
    }

    private fun getEndOfDayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    private fun formatExpiry(expiresAt: Long): String {
        if (expiresAt <= 0) return "No expiry"
        return SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(expiresAt))
    }

    private fun downloadQrCode() {
        val bitmap = currentQrBitmap
        if (bitmap == null) {
            Toast.makeText(requireContext(), "Generate the QR code first", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "attendance_qr_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.png"
        runCatching {
            saveBitmapToDownloads(bitmap, fileName)
        }.onSuccess {
            Toast.makeText(requireContext(), "QR code saved to Downloads/PH232", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(requireContext(), "Download failed: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToDownloads(bitmap: Bitmap, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = requireContext().contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PH232")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create download file")
            resolver.openOutputStream(uri).use { output ->
                writeBitmap(bitmap, output, uri)
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PH232"
            )
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, fileName)
            FileOutputStream(file).use { output ->
                writeBitmap(bitmap, output, null)
            }
            MediaScannerConnection.scanFile(
                requireContext(),
                arrayOf(file.absolutePath),
                arrayOf("image/png"),
                null
            )
        }
    }

    private fun writeBitmap(bitmap: Bitmap, output: OutputStream?, uri: Uri?) {
        if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            if (uri != null) {
                requireContext().contentResolver.delete(uri, null, null)
            }
            error("Unable to write file")
        }
    }

    private fun saveEventWithQrCode() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (eventId.isNotEmpty()) {
            repository.updateEvent(
                eventId = eventId,
                updates = mapOf("qrCode" to currentQrCode),
                onSuccess = { },
                onFailure = { }
            )
        } else {
            val event = Event(
                name = eventName,
                title = eventName,
                date = currentDate,
                time = currentTime,
                qrCode = currentQrCode,
                isActive = true,
                createdBy = "admin"
            )

            repository.addEvent(
                event = event,
                onSuccess = { newEventId ->
                    eventId = newEventId
                },
                onFailure = {
                    saveQrCodeToFirestore()
                }
            )
        }
    }

    private fun saveQrCodeToFirestore() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val qrCodeData = mapOf(
            "qrCode" to currentQrCode,
            "eventName" to eventName,
            "date" to currentDate,
            "time" to currentTime,
            "expiresAt" to getEndOfDayMillis(),
            "timestamp" to System.currentTimeMillis(),
            "isActive" to true
        )

        FirebaseFirestore.getInstance()
            .collection("generated_qr_codes")
            .add(qrCodeData)
    }
}
