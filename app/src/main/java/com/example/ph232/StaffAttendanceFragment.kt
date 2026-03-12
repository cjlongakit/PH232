package com.example.ph232

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: AttendanceAdapter
    private lateinit var staffUsername: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_staff_attendance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSetTime = view.findViewById(R.id.btnSetTime)
        ivGeneratedQR = view.findViewById(R.id.ivGeneratedQR)
        tvQrStatus = view.findViewById(R.id.tvQrStatus)
        tvAttendanceCount = view.findViewById(R.id.tvAttendanceCount)
        rvAttendanceList = view.findViewById(R.id.rvAttendanceList)

        rvAttendanceList.layoutManager = LinearLayoutManager(requireContext())
        adapter = AttendanceAdapter(emptyList())
        rvAttendanceList.adapter = adapter

        val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        staffUsername = prefs.getString("USER_PH", "unknown_staff") ?: "unknown_staff"

        checkAndLoadTodayQR()
        fetchTodaysAttendance()

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

    private fun fetchTodaysAttendance() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        db.collection("attendance")
            .whereEqualTo("date", todayStr)
            .whereEqualTo("staffId", staffUsername)
            .orderBy("scanTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val records = mutableListOf<AttendanceRecord>()
                for (doc in snapshots) {
                    val name = doc.getString("studentName") ?: "Unknown Student"
                    val time = doc.getString("scanTime") ?: "--:--"
                    records.add(AttendanceRecord(name, time))
                }

                tvAttendanceCount.text = "Total: ${records.size}"
                adapter.updateData(records)
            }
    }
}

