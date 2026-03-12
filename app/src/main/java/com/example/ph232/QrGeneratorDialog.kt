package com.example.ph232

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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
import java.text.SimpleDateFormat
import java.util.*

class QrGeneratorDialog : DialogFragment() {

    private lateinit var tvEventName: TextView
    private lateinit var tvQrCodeValue: TextView
    private lateinit var ivQrCode: ImageView
    private lateinit var btnNewCode: MaterialButton
    private lateinit var btnClose: MaterialButton
    private lateinit var repository: FirebaseRepository

    private var currentQrCode: String = ""
    private var eventName: String = "Attendance"
    private var eventId: String = ""

    companion object {
        fun newInstance(eventName: String = "Attendance", eventId: String = ""): QrGeneratorDialog {
            return QrGeneratorDialog().apply {
                arguments = Bundle().apply {
                    putString("EVENT_NAME", eventName)
                    putString("EVENT_ID", eventId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventName = arguments?.getString("EVENT_NAME") ?: "Attendance"
        eventId = arguments?.getString("EVENT_ID") ?: ""
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
        ivQrCode = view.findViewById(R.id.ivQrCode)
        btnNewCode = view.findViewById(R.id.btnNewCode)
        btnClose = view.findViewById(R.id.btnClose)

        repository = FirebaseRepository.getInstance()

        // Set event name
        tvEventName.text = eventName

        // If we have an eventId, try to load existing QR code first
        if (eventId.isNotEmpty()) {
            loadExistingQrCode()
        } else {
            // Generate new QR code
            generateNewQrCode()
        }

        // New code button
        btnNewCode.setOnClickListener {
            generateNewQrCode()
        }

        // Close button
        btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun loadExistingQrCode() {
        val db = FirebaseFirestore.getInstance()
        db.collection("events").document(eventId)
            .get()
            .addOnSuccessListener { document ->
                val existingQrCode = document.getString("qrCode") ?: ""
                if (existingQrCode.isNotEmpty()) {
                    currentQrCode = existingQrCode
                    tvQrCodeValue.text = "Code: $currentQrCode"
                    try {
                        val qrBitmap = generateQrBitmap(currentQrCode, 512)
                        ivQrCode.setImageBitmap(qrBitmap)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error displaying QR code", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // No existing QR code, generate new one
                    generateNewQrCode()
                }
            }
            .addOnFailureListener {
                // Error loading, generate new one
                generateNewQrCode()
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun generateNewQrCode() {
        // Generate unique QR code with timestamp and random component
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val random = UUID.randomUUID().toString().take(8)
        currentQrCode = "EVENT_${timestamp}_$random"

        // Update UI
        tvQrCodeValue.text = "Code: $currentQrCode"

        // Generate QR code bitmap
        try {
            val qrBitmap = generateQrBitmap(currentQrCode, 512)
            ivQrCode.setImageBitmap(qrBitmap)

            // Save QR code with event to Firestore
            saveEventWithQrCode()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error generating QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

    private fun saveEventWithQrCode() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (eventId.isNotEmpty()) {
            // Update existing event with new QR code
            repository.updateEvent(
                eventId = eventId,
                updates = mapOf("qrCode" to currentQrCode),
                onSuccess = {
                    Toast.makeText(requireContext(), "QR code updated for event", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    // Silent fail - QR code will still work locally
                }
            )
        } else {
            // Create a new temporary event for attendance tracking
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
                    Toast.makeText(requireContext(), "QR code generated successfully", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    // Fallback: Save QR code to a separate collection
                    saveQrCodeToFirestore()
                }
            )
        }
    }

    private fun saveQrCodeToFirestore() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val db = FirebaseFirestore.getInstance()

        val qrCodeData = mapOf(
            "qrCode" to currentQrCode,
            "eventName" to eventName,
            "date" to currentDate,
            "time" to currentTime,
            "timestamp" to System.currentTimeMillis(),
            "isActive" to true
        )

        db.collection("generated_qr_codes")
            .add(qrCodeData)
            .addOnSuccessListener {
                // QR code saved successfully
            }
            .addOnFailureListener { e ->
                // Silent fail - QR code will still work
            }
    }
}

