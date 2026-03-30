package com.example.ph232

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminDashboardFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private val db = FirebaseFirestore.getInstance()
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalEmployees: TextView
    private lateinit var tvAttendanceCount: TextView
    private lateinit var btnAddEmployee: MaterialButton
    private lateinit var btnCreateEvent: MaterialButton
    private lateinit var btnAddStudent: MaterialButton
    private lateinit var monitoringContainer: LinearLayout

    private var studentsListener: ListenerRegistration? = null
    private var employeesListener: ListenerRegistration? = null
    private var attendanceListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var dataLoadedCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        tvTotalStudents = view.findViewById(R.id.tvTotalStudents)
        tvTotalEmployees = view.findViewById(R.id.tvTotalEmployees)
        tvAttendanceCount = view.findViewById(R.id.tvAttendanceCount)
        btnAddEmployee = view.findViewById(R.id.btnAddEmployee)
        btnCreateEvent = view.findViewById(R.id.btnCreateEvent)
        btnAddStudent = view.findViewById(R.id.btnAddStudent)
        monitoringContainer = view.findViewById(R.id.monitoringContainer)

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading dashboard...")
        dataLoadedCount = 0
        setupDashboardListeners()

        btnAddEmployee.setOnClickListener {
            startActivity(Intent(requireContext(), RegisterStaffActivity::class.java))
        }

        btnCreateEvent.setOnClickListener {
            showCreateEventDialog()
        }

        btnAddStudent.setOnClickListener {
            showAddStudentDialog()
        }
    }

    private fun showAddStudentDialog() {
        val dialog = AddStudentDialog.newInstance()
        dialog.setOnStudentAddedListener { _, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "AddStudentDialog")
    }

    private fun showCreateEventDialog() {
        val calendar = Calendar.getInstance()
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val dialog = AddEventCalendarDialog.newInstance(date = date, day = day)
        dialog.setOnEventAddedListener { _, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "AddEventCalendarDialog")
    }

    private fun onDataLoaded() {
        dataLoadedCount++
        if (dataLoadedCount >= 3) {
            progressManager?.dismiss()
        }
    }

    private fun setupDashboardListeners() {
        studentsListener = db.collection("users")
            .whereIn("role", listOf("student", "beneficiary"))
            .addSnapshotListener { snapshot, _ ->
                val total = snapshot?.documents?.count { doc ->
                    val approvalStatus = doc.getString("approvalStatus")?.trim()?.lowercase().orEmpty()
                    val legacyStatus = doc.getString("status")?.trim()?.lowercase().orEmpty()
                    approvalStatus.isNotBlank() || legacyStatus.isNotBlank()
                } ?: 0
                tvTotalStudents.text = total.toString()
                onDataLoaded()
            }

        employeesListener = db.collection("staff")
            .addSnapshotListener { snapshot, _ ->
                tvTotalEmployees.text = (snapshot?.size() ?: 0).toString()
                onDataLoaded()
            }

        attendanceListener = repository.listenToAttendance { attendanceList ->
            tvAttendanceCount.text = attendanceList.size.toString()

            monitoringContainer.removeAllViews()
            if (attendanceList.isEmpty()) {
                addMonitoringItem("No recent activity", "Attendance records will appear here once students start scanning.")
            } else {
                attendanceList.take(5).forEach { attendance ->
                    val studentName = attendance.studentName.ifBlank { attendance.studentId.ifBlank { "Unknown student" } }
                    val detail = attendance.eventName.ifBlank { "Attendance recorded" }
                    val time = listOf(attendance.date, attendance.scanTime.ifBlank { attendance.time })
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                    addMonitoringItem(studentName, if (time.isBlank()) detail else "$detail - $time")
                }
            }
            onDataLoaded()
        }
    }

    private fun addMonitoringItem(title: String, subtitle: String) {
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_recent_attendance, monitoringContainer, false)
        row.findViewById<TextView>(R.id.tvStudentName).text = title
        row.findViewById<TextView>(R.id.tvAttendanceDetail).text = subtitle
        row.findViewById<TextView>(R.id.tvTime).visibility = View.GONE
        monitoringContainer.addView(row)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        studentsListener?.remove()
        employeesListener?.remove()
        attendanceListener?.remove()
    }
}
