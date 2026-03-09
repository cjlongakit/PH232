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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerDialog : DialogFragment() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var btnFlash: FloatingActionButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var scanLine: View
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var db: FirebaseFirestore

    private var camera: Camera? = null
    private var isFlashOn = false
    private var lastScannedQR = ""
    private var lastScanTime = 0L
    private var studentId: String = ""

    private var onAttendanceRecorded: ((Boolean, String) -> Unit)? = null

    companion object {
        fun newInstance(studentId: String): QrScannerDialog {
            return QrScannerDialog().apply {
                arguments = Bundle().apply {
                    putString("STUDENT_ID", studentId)
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
        db = FirebaseFirestore.getInstance()

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
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
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
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
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

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to start camera", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun handleQRCodeScanned(qrCode: String) {
        val currentTime = System.currentTimeMillis()
        // Prevent duplicate scans within 3 seconds
        if (qrCode != lastScannedQR || currentTime - lastScanTime > 3000) {
            lastScannedQR = qrCode
            lastScanTime = currentTime
            recordAttendance(qrCode)
        }
    }

    private fun recordAttendance(eventQR: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        // Check if attendance already exists for this student, event, and date
        db.collection("attendance")
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("eventQR", eventQR)
            .whereEqualTo("date", currentDate)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    // No duplicate found, record attendance
                    val attendanceData = mapOf(
                        "studentId" to studentId,
                        "eventQR" to eventQR,
                        "date" to currentDate,
                        "time" to currentTime,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("attendance")
                        .add(attendanceData)
                        .addOnSuccessListener {
                            onAttendanceRecorded?.invoke(true, "Attendance recorded successfully!")
                            dismiss()
                        }
                        .addOnFailureListener { e ->
                            onAttendanceRecorded?.invoke(false, "Error: ${e.message}")
                        }
                } else {
                    onAttendanceRecorded?.invoke(false, "Attendance already recorded for today!")
                }
            }
            .addOnFailureListener { e ->
                onAttendanceRecorded?.invoke(false, "Error checking attendance: ${e.message}")
            }
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
        private val scanner = BarcodeScanning.getClient()
        private var lastAnalyzedTimestamp = 0L

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp >= 500) {
                imageProxy.image?.let { image ->
                    val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.rawValue?.let { value ->
                                onQRCodeDetected(value)
                                lastAnalyzedTimestamp = currentTimestamp
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } ?: imageProxy.close()
            } else {
                imageProxy.close()
            }
        }
    }
}

