package com.example.ph232

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerDialog : DialogFragment() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var btnFlash: FloatingActionButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var scanLine: View
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var repository: FirebaseRepository

    private var camera: Camera? = null
    private var isFlashOn = false
    private var lastScannedQR = ""
    private var lastScanTime = 0L
    private var studentId: String = ""
    private var studentName: String = ""

    private var onAttendanceRecorded: ((Boolean, String) -> Unit)? = null

    companion object {
        fun newInstance(studentId: String, studentName: String = ""): QrScannerDialog {
            return QrScannerDialog().apply {
                arguments = Bundle().apply {
                    putString("STUDENT_ID", studentId)
                    putString("STUDENT_NAME", studentName)
                }
            }
        }
    }

    fun setOnAttendanceRecordedListener(listener: (Boolean, String) -> Unit) {
        onAttendanceRecorded = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        studentId = arguments?.getString("STUDENT_ID") ?: "Unknown"
        studentName = arguments?.getString("STUDENT_NAME") ?: ""
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
        return inflater.inflate(R.layout.dialog_qr_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraPreview = view.findViewById(R.id.cameraPreview)
        btnFlash = view.findViewById(R.id.btnFlash)
        btnCancel = view.findViewById(R.id.btnCancel)
        scanLine = view.findViewById(R.id.scanLine)

        cameraExecutor = Executors.newSingleThreadExecutor()
        repository = FirebaseRepository.getInstance()

        // Start scan line animation
        startScanLineAnimation()

        // Check camera permission and start camera
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        // Flash toggle
        btnFlash.setOnClickListener {
            toggleFlash()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // Set dialog to be wider and tall enough for camera
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun startScanLineAnimation() {
        val animator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, 180f)
        animator.duration = 2000
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.start()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(cameraPreview.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrCode ->
                            activity?.runOnUiThread {
                                handleQRCodeScanned(qrCode)
                            }
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Bind camera to lifecycle
                camera = cameraProvider.bindToLifecycle(
                    this, // Use DialogFragment as lifecycle owner
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun handleQRCodeScanned(qrCode: String) {
        val currentTime = System.currentTimeMillis()
        // Prevent duplicate scans within 3 seconds
        if (qrCode != lastScannedQR || currentTime - lastScanTime > 3000) {
            lastScannedQR = qrCode
            lastScanTime = currentTime

            // Show toast that QR was detected
            Toast.makeText(requireContext(), "QR Detected, validating...", Toast.LENGTH_SHORT).show()

            // Validate against centralized QR session first
            validateAndRecordAttendance(qrCode)
        }
    }

    private fun validateAndRecordAttendance(scannedQrCode: String) {
        // First validate if this QR code belongs to an active session
        repository.validateQrCode(
            scannedQrCode = scannedQrCode,
            onSuccess = { qrSession ->
                if (qrSession != null) {
                    // Valid active QR session found - record attendance with log
                    repository.recordAttendanceWithLog(
                        studentId = studentId,
                        studentName = studentName,
                        qrSession = qrSession,
                        onSuccess = { attendanceId ->
                            onAttendanceRecorded?.invoke(true, "Attendance recorded successfully for ${qrSession.eventName}!")
                            dismiss()
                        },
                        onFailure = { e ->
                            onAttendanceRecorded?.invoke(false, e.message ?: "Error recording attendance")
                        }
                    )
                } else {
                    // No active session for this QR code - check if it's expired or invalid
                    onAttendanceRecorded?.invoke(false, "This QR code is invalid or has expired. Please ask admin for a new code.")
                }
            },
            onFailure = { e ->
                // Validation failed, fall back to old method
                recordAttendanceLegacy(scannedQrCode)
            }
        )
    }

    private fun recordAttendanceLegacy(eventQR: String) {
        // Legacy method - fallback for old QR codes
        repository.getEventByQRCode(eventQR,
            onSuccess = { event ->
                if (event != null) {
                    // Event found, record attendance with full details
                    repository.recordAttendance(
                        studentId = studentId,
                        studentName = studentName,
                        eventId = event.id,
                        eventName = event.name.ifEmpty { event.title },
                        eventQR = eventQR,
                        onSuccess = { attendanceId ->
                            onAttendanceRecorded?.invoke(true, "Attendance recorded successfully for ${event.name.ifEmpty { event.title }}!")
                            dismiss()
                        },
                        onFailure = { e ->
                            onAttendanceRecorded?.invoke(false, e.message ?: "Error recording attendance")
                        }
                    )
                } else {
                    // Event not found but still record with QR code
                    repository.recordAttendance(
                        studentId = studentId,
                        studentName = studentName,
                        eventId = "",
                        eventName = "Unknown Event",
                        eventQR = eventQR,
                        onSuccess = { attendanceId ->
                            onAttendanceRecorded?.invoke(true, "Attendance recorded successfully!")
                            dismiss()
                        },
                        onFailure = { e ->
                            onAttendanceRecorded?.invoke(false, e.message ?: "Error recording attendance")
                        }
                    )
                }
            },
            onFailure = { e ->
                // Error finding event, try to record anyway
                repository.recordAttendance(
                    studentId = studentId,
                    studentName = studentName,
                    eventId = "",
                    eventName = "Event",
                    eventQR = eventQR,
                    onSuccess = { attendanceId ->
                        onAttendanceRecorded?.invoke(true, "Attendance recorded successfully!")
                        dismiss()
                    },
                    onFailure = { ex ->
                        onAttendanceRecorded?.invoke(false, ex.message ?: "Error recording attendance")
                    }
                )
            }
        )
    }

    private fun toggleFlash() {
        camera?.let {
            isFlashOn = !isFlashOn
            it.cameraControl.enableTorch(isFlashOn)
            btnFlash.setImageResource(
                if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    // QR Code Analyzer
    private class QRCodeAnalyzer(private val onQRCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        // Configure scanner to only look for QR codes
        private val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        private val scanner = BarcodeScanning.getClient(options)
        private var isScanning = false

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            if (isScanning) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                isScanning = true
                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            for (barcode in barcodes) {
                                barcode.rawValue?.let { value ->
                                    if (value.isNotEmpty()) {
                                        onQRCodeDetected(value)
                                        return@addOnSuccessListener
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                    }
                    .addOnCompleteListener {
                        isScanning = false
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}

