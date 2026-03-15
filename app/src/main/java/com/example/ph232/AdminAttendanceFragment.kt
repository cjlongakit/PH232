package com.example.ph232

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class AdminAttendanceFragment : Fragment() {

    private lateinit var tvSelectedDate: TextView
    private lateinit var btnPrevDate: MaterialButton
    private lateinit var btnNextDate: MaterialButton
    private lateinit var btnPickDate: MaterialButton
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPresentCount: TextView
    private lateinit var tvLateCount: TextView
    private lateinit var tvRemovedCount: TextView
    private lateinit var cardActiveSession: MaterialCardView
    private lateinit var tvActiveSessionInfo: TextView
    private lateinit var btnDeactivateSession: MaterialButton
    private lateinit var rvAttendanceLogs: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: AttendanceLogAdapter
    private var attendanceListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null

    private var selectedDate: Calendar = Calendar.getInstance()
    private var adminUsername: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_admin_attendance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        // Initialize views
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate)
        btnPrevDate = view.findViewById(R.id.btnPrevDate)
        btnNextDate = view.findViewById(R.id.btnNextDate)
        btnPickDate = view.findViewById(R.id.btnPickDate)
        tvTotalCount = view.findViewById(R.id.tvTotalCount)
        tvPresentCount = view.findViewById(R.id.tvPresentCount)
        tvLateCount = view.findViewById(R.id.tvLateCount)
        tvRemovedCount = view.findViewById(R.id.tvRemovedCount)
        cardActiveSession = view.findViewById(R.id.cardActiveSession)
        tvActiveSessionInfo = view.findViewById(R.id.tvActiveSessionInfo)
        btnDeactivateSession = view.findViewById(R.id.btnDeactivateSession)
        rvAttendanceLogs = view.findViewById(R.id.rvAttendanceLogs)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)

        // Get admin username
        val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        adminUsername = prefs.getString("USER_PH", "admin") ?: "admin"

        // Setup RecyclerView
        adapter = AttendanceLogAdapter(
            logs = emptyList(),
            onEditClick = { log -> showEditDialog(log) },
            onDeleteClick = { log -> showDeleteConfirmation(log) }
        )
        rvAttendanceLogs.layoutManager = LinearLayoutManager(requireContext())
        rvAttendanceLogs.adapter = adapter

        // Setup date navigation
        updateDateDisplay()
        setupDateNavigation()

        // Setup active session listener
        setupActiveSessionListener()

        // Load attendance for selected date
        loadAttendanceForDate()
    }

    private fun setupDateNavigation() {
        btnPrevDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1)
            updateDateDisplay()
            loadAttendanceForDate()
        }

        btnNextDate.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1)
            updateDateDisplay()
            loadAttendanceForDate()
        }

        btnPickDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate.set(year, month, day)
                updateDateDisplay()
                loadAttendanceForDate()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun updateDateDisplay() {
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        val selectedDateStr = dateFormat.format(selectedDate.time)
        val todayStr = dateFormat.format(today.time)
        val yesterdayStr = dateFormat.format(yesterday.time)

        tvSelectedDate.text = when (selectedDateStr) {
            todayStr -> "Today"
            yesterdayStr -> "Yesterday"
            else -> displayFormat.format(selectedDate.time)
        }
    }

    private fun loadAttendanceForDate() {
        // Remove previous listener
        attendanceListener?.let { repository.removeListener(it) }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)

        attendanceListener = repository.listenToAttendanceLogsByDate(dateStr) { logs ->
            updateUI(logs)
        }
    }

    private fun updateUI(logs: List<AttendanceLog>) {
        adapter.updateData(logs)

        // Update stats
        val total = logs.size
        val present = logs.count { it.status.lowercase() == "present" }
        val late = logs.count { it.status.lowercase() == "late" }
        val removed = logs.count { it.status.lowercase() == "removed" || it.status.lowercase() == "absent" }

        tvTotalCount.text = total.toString()
        tvPresentCount.text = present.toString()
        tvLateCount.text = late.toString()
        tvRemovedCount.text = removed.toString()

        // Show/hide empty state
        if (logs.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvAttendanceLogs.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvAttendanceLogs.visibility = View.VISIBLE
        }
    }

    private fun setupActiveSessionListener() {
        sessionListener = repository.listenToActiveQrSession { session ->
            if (session != null) {
                cardActiveSession.visibility = View.VISIBLE
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val expiresTime = if (session.expiresAt > 0) {
                    timeFormat.format(Date(session.expiresAt))
                } else {
                    "No expiry"
                }
                tvActiveSessionInfo.text = "Active: ${session.eventName} • Expires: $expiresTime"

                btnDeactivateSession.setOnClickListener {
                    showDeactivateConfirmation(session)
                }
            } else {
                cardActiveSession.visibility = View.GONE
            }
        }
    }

    private fun showDeactivateConfirmation(session: QrSession) {
        AlertDialog.Builder(requireContext())
            .setTitle("Deactivate QR Session")
            .setMessage("Are you sure you want to deactivate the current QR code? Students will not be able to scan until a new code is generated.")
            .setPositiveButton("Deactivate") { _, _ ->
                repository.deactivateQrSession(
                    sessionId = session.id,
                    onSuccess = {
                        Toast.makeText(requireContext(), "QR session deactivated", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(log: AttendanceLog) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_attendance, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.rgStatus)
        val rbPresent = dialogView.findViewById<RadioButton>(R.id.rbPresent)
        val rbLate = dialogView.findViewById<RadioButton>(R.id.rbLate)
        val rbAbsent = dialogView.findViewById<RadioButton>(R.id.rbAbsent)
        val etNotes = dialogView.findViewById<EditText>(R.id.etNotes)

        // Set current values
        when (log.status.lowercase()) {
            "present" -> rbPresent.isChecked = true
            "late" -> rbLate.isChecked = true
            "absent", "removed" -> rbAbsent.isChecked = true
        }
        etNotes.setText(log.notes)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Attendance")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newStatus = when (radioGroup.checkedRadioButtonId) {
                    R.id.rbPresent -> "present"
                    R.id.rbLate -> "late"
                    R.id.rbAbsent -> "removed"
                    else -> log.status
                }
                val newNotes = etNotes.text.toString()

                repository.updateAttendanceLog(
                    logId = log.id,
                    status = newStatus,
                    notes = newNotes,
                    modifiedBy = adminUsername,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Attendance updated", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(log: AttendanceLog) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Attendance")
            .setMessage("Are you sure you want to remove ${log.studentName}'s attendance record? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteAttendanceLog(
                    logId = log.id,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Attendance record deleted", Toast.LENGTH_SHORT).show()
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
        attendanceListener?.let { repository.removeListener(it) }
        sessionListener?.let { repository.removeListener(it) }
    }
}

