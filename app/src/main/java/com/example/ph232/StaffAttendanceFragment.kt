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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StaffAttendanceFragment : Fragment() {

    private lateinit var tvSelectedDate: TextView
    private lateinit var btnPrevDate: MaterialButton
    private lateinit var btnNextDate: MaterialButton
    private lateinit var btnPickDate: MaterialButton
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPresentCount: TextView
    private lateinit var tvLateCount: TextView
    private lateinit var tvRemovedCount: TextView
    private lateinit var cardActiveSession: MaterialCardView
    private lateinit var tvActiveSessionTitle: TextView
    private lateinit var tvActiveSessionMeta: TextView
    private lateinit var btnDeactivateSession: MaterialButton
    private lateinit var rvAttendanceLogs: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var fabQrGenerator: FloatingActionButton

    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: AttendanceLogAdapter
    private var attendanceListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null

    private var selectedDate: Calendar = Calendar.getInstance()
    private var staffUsername: String = ""
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_staff_attendance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        tvSelectedDate = view.findViewById(R.id.tvSelectedDate)
        btnPrevDate = view.findViewById(R.id.btnPrevDate)
        btnNextDate = view.findViewById(R.id.btnNextDate)
        btnPickDate = view.findViewById(R.id.btnPickDate)
        tvTotalCount = view.findViewById(R.id.tvTotalCount)
        tvPresentCount = view.findViewById(R.id.tvPresentCount)
        tvLateCount = view.findViewById(R.id.tvLateCount)
        tvRemovedCount = view.findViewById(R.id.tvRemovedCount)
        cardActiveSession = view.findViewById(R.id.cardActiveSession)
        tvActiveSessionTitle = view.findViewById(R.id.tvActiveSessionTitle)
        tvActiveSessionMeta = view.findViewById(R.id.tvActiveSessionMeta)
        btnDeactivateSession = view.findViewById(R.id.btnDeactivateSession)
        rvAttendanceLogs = view.findViewById(R.id.rvAttendanceLogs)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        fabQrGenerator = view.findViewById(R.id.fabQrGenerator)

        val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        staffUsername = prefs.getString("USER_PH", "staff") ?: "staff"
        val staffName = prefs.getString("USER_NAME", "Staff") ?: "Staff"

        fabQrGenerator.setOnClickListener {
            showQrGeneratorDialog(staffUsername, staffName)
        }

        adapter = AttendanceLogAdapter(
            logs = emptyList(),
            onEditClick = { log -> showEditDialog(log) },
            onDeleteClick = { log -> showDeleteConfirmation(log) }
        )
        rvAttendanceLogs.layoutManager = LinearLayoutManager(requireContext())
        rvAttendanceLogs.adapter = adapter

        updateDateDisplay()
        setupDateNavigation()
        setupActiveSessionListener()

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading attendance...")
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
        attendanceListener?.let { repository.removeListener(it) }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)
        attendanceListener = repository.listenToAllAttendance { allRecords ->
            val records = allRecords.filter { it.date == dateStr }
            val logs = records.map { att ->
                AttendanceLog(
                    id = att.id,
                    attendanceId = att.id,
                    studentId = att.studentId,
                    studentName = att.studentName,
                    eventId = att.eventId,
                    eventName = att.eventName,
                    qrSessionId = "",
                    qrCode = att.eventQR,
                    scanDate = att.date,
                    scanTime = att.time.ifEmpty { att.scanTime },
                    timestamp = att.timestamp,
                    status = att.status,
                    modifiedBy = "",
                    modifiedAt = 0,
                    notes = att.notes
                )
            }
            updateUI(logs)
        }
    }

    private fun updateUI(logs: List<AttendanceLog>) {
        adapter.updateData(logs)

        if (isFirstLoad) {
            isFirstLoad = false
            progressManager?.dismiss()
        }

        val total = logs.size
        val present = logs.count { it.status.lowercase() == "present" }
        val late = logs.count { it.status.lowercase() == "late" }
        val removed = logs.count { it.status.lowercase() == "removed" || it.status.lowercase() == "absent" }

        tvTotalCount.text = total.toString()
        tvPresentCount.text = present.toString()
        tvLateCount.text = late.toString()
        tvRemovedCount.text = removed.toString()

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
                val expiresText = if (session.expiresAt > 0) {
                    SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(session.expiresAt))
                } else {
                    "No expiry"
                }
                tvActiveSessionTitle.text = session.eventName.ifBlank { "Attendance QR is active" }
                tvActiveSessionMeta.text = "Expires $expiresText"

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

                repository.updateAttendanceRecord(
                    attendanceId = log.id,
                    status = newStatus,
                    notes = newNotes,
                    modifiedBy = staffUsername,
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
                repository.deleteAttendance(
                    attendanceId = log.id,
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

    private fun showQrGeneratorDialog(staffId: String, staffName: String) {
        val dialog = QrGeneratorDialog.newInstance(
            eventName = "Attendance",
            eventId = "",
            adminId = staffId,
            adminName = staffName
        )
        dialog.show(parentFragmentManager, "QrGeneratorDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        attendanceListener?.let { repository.removeListener(it) }
        sessionListener?.let { repository.removeListener(it) }
    }
}
