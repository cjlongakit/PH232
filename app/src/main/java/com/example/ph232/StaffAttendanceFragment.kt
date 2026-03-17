package com.example.ph232

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.text.SimpleDateFormat
import java.util.*

class StaffAttendanceFragment : Fragment() {

    private lateinit var btnSetTime: MaterialButton
    private lateinit var ivGeneratedQR: ImageView
    private lateinit var tvQrStatus: TextView
    private lateinit var tvAttendanceCount: TextView
    private lateinit var rvAttendanceList: RecyclerView

    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: AttendanceAdapter
    private lateinit var staffUsername: String
    private var attendanceListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_staff_attendance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        btnSetTime = view.findViewById(R.id.btnSetTime)
        ivGeneratedQR = view.findViewById(R.id.ivGeneratedQR)
        tvQrStatus = view.findViewById(R.id.tvQrStatus)
        tvAttendanceCount = view.findViewById(R.id.tvAttendanceCount)
        rvAttendanceList = view.findViewById(R.id.rvAttendanceList)

        rvAttendanceList.layoutManager = LinearLayoutManager(requireContext())
        adapter = AttendanceAdapter(emptyList()) { attendance ->
            showDeleteConfirmation(attendance)
        }
        rvAttendanceList.adapter = adapter

        val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        staffUsername = prefs.getString("USER_PH", "unknown_staff") ?: "unknown_staff"

        checkAndLoadTodayQR()
        setupAttendanceListener()

        btnSetTime.setOnClickListener {
            showTimePicker()
        }
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val timeStr24Hour = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
            prefs.edit().putString("qr_date", todayStr).putString("qr_time", timeStr24Hour).apply()

            generateQRCodeImage(todayStr, timeStr24Hour)

            // Refresh attendance listener to pick up new QR code scans
            setupAttendanceListener()
        }

        TimePickerDialog(requireContext(), timeSetListener, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    private fun checkAndLoadTodayQR() {
        val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val savedDate = prefs.getString("qr_date", "")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (savedDate == todayStr) {
            val savedTime = prefs.getString("qr_time", "17:00")!!
            generateQRCodeImage(todayStr, savedTime)
        } else {
            tvQrStatus.text = "Please set a closing time to generate today's QR."
            tvQrStatus.setTextColor(Color.parseColor("#F59E0B"))
            ivGeneratedQR.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    private fun generateQRCodeImage(date: String, deadline24h: String) {
        val qrDataString = "PH323ATTENDANCE|${date}|${staffUsername}|${deadline24h}"

        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(qrDataString, BarcodeFormat.QR_CODE, 600, 600)
            ivGeneratedQR.setImageBitmap(bitmap)

            val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val friendlyTime = sdf12.format(sdf24.parse(deadline24h)!!)

            tvQrStatus.text = "Active until $friendlyTime"
            tvQrStatus.setTextColor(Color.parseColor("#10B981"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAttendanceListener() {
        attendanceListener?.remove()
        attendanceListener = null

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val db = FirebaseFirestore.getInstance()

        // Direct Firestore read - no filters at all
        db.collection("attendance")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                val docCount = snapshot.documents.size

                val allRecords = mutableListOf<Attendance>()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val att = Attendance(
                        id = doc.id,
                        studentId = data["studentId"] as? String ?: "",
                        studentName = data["studentName"] as? String ?: "",
                        eventId = data["eventId"] as? String ?: "",
                        eventName = data["eventName"] as? String ?: "",
                        eventQR = data["eventQR"] as? String ?: "",
                        date = data["date"] as? String ?: "",
                        time = data["time"] as? String ?: "",
                        scanTime = data["scanTime"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        status = data["status"] as? String ?: "present",
                        notes = data["notes"] as? String ?: ""
                    )
                    allRecords.add(att)
                }

                // Show ALL records (no date filter) so we can confirm data is loading
                val sortedRecords = allRecords.sortedByDescending { it.timestamp }

                tvAttendanceCount.text = "Total: ${sortedRecords.size}"
                adapter.updateData(sortedRecords)

                // Debug toast
                Toast.makeText(
                    requireContext(),
                    "Docs: $docCount | Parsed: ${allRecords.size} | Today($todayStr): ${allRecords.count { it.date == todayStr }}",
                    Toast.LENGTH_LONG
                ).show()

                // Set up real-time listener for future updates
                setupRealtimeListener(todayStr)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Read error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupRealtimeListener(todayStr: String) {
        attendanceListener?.remove()
        attendanceListener = null

        val db = FirebaseFirestore.getInstance()
        attendanceListener = db.collection("attendance")
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                if (error != null) return@addSnapshotListener
                if (snapshot == null) return@addSnapshotListener

                val allRecords = mutableListOf<Attendance>()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val att = Attendance(
                        id = doc.id,
                        studentId = data["studentId"] as? String ?: "",
                        studentName = data["studentName"] as? String ?: "",
                        eventId = data["eventId"] as? String ?: "",
                        eventName = data["eventName"] as? String ?: "",
                        eventQR = data["eventQR"] as? String ?: "",
                        date = data["date"] as? String ?: "",
                        time = data["time"] as? String ?: "",
                        scanTime = data["scanTime"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        status = data["status"] as? String ?: "present",
                        notes = data["notes"] as? String ?: ""
                    )
                    allRecords.add(att)
                }

                val sortedRecords = allRecords.sortedByDescending { it.timestamp }

                tvAttendanceCount.text = "Total: ${sortedRecords.size}"
                adapter.updateData(sortedRecords)
            }
    }

    private fun showDeleteConfirmation(attendance: Attendance) {
        val studentName = attendance.studentName.ifEmpty { attendance.studentId }
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Attendance")
            .setMessage("Remove ${studentName}'s attendance record?")
            .setPositiveButton("Remove") { _, _ ->
                repository.deleteAttendance(
                    attendanceId = attendance.id,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Attendance removed", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        attendanceListener?.remove()
        attendanceListener = null
    }
}
