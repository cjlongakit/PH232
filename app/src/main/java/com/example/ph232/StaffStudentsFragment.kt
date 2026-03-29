package com.example.ph232

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StaffStudentsFragment : Fragment() {

    private lateinit var adapter: StudentAdapter
    private lateinit var rvStudents: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvStudentCount: TextView
    private lateinit var tvResultsSummary: TextView
    private lateinit var tvEmptySubtext: TextView
    private lateinit var etSearchStudents: EditText
    private lateinit var spinnerStatusFilter: Spinner
    private lateinit var spinnerYearFilter: Spinner
    private lateinit var spinnerSortStudents: Spinner
    private lateinit var btnAddStudent: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private val repository = FirebaseRepository.getInstance()
    private var studentsListener: ListenerRegistration? = null
    private var lettersListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true
    private var hasLoadedStudents = false
    private var hasLoadedLetters = false

    private var allStudents = emptyList<Student>()
    private var lateStudentIds = emptySet<String>()
    private var overdueStudentIds = emptySet<String>()
    private var staffUsername: String = ""

    private var currentSearch = ""
    private var currentStudentFilter = "All My Students"
    private var currentYearFilter = "All Years"
    private var currentSort = "Name: A-Z"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_staff_students, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        staffUsername = prefs.getString("USER_PH", "unknown") ?: "unknown"

        rvStudents = view.findViewById(R.id.rvStudents)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        tvStudentCount = view.findViewById(R.id.tvStudentCount)
        tvResultsSummary = view.findViewById(R.id.tvResultsSummary)
        tvEmptySubtext = view.findViewById(R.id.tvEmptySubtext)
        etSearchStudents = view.findViewById(R.id.etSearchStudents)
        spinnerStatusFilter = view.findViewById(R.id.spinnerStatusFilter)
        spinnerYearFilter = view.findViewById(R.id.spinnerYearFilter)
        spinnerSortStudents = view.findViewById(R.id.spinnerSortStudents)
        btnAddStudent = view.findViewById(R.id.btnAddStudent)

        adapter = StudentAdapter(
            students = emptyList(),
            onEditClick = { student -> showEditStudentDialog(student) },
            onDeleteClick = { student -> showDeleteConfirmation(student) }
        )
        rvStudents.layoutManager = LinearLayoutManager(requireContext())
        rvStudents.adapter = adapter

        setupFilterControls()

        btnAddStudent.setOnClickListener {
            showAddStudentDialog()
        }

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading students...")
        setupStudentsListener()
        setupLettersListener()
    }

    private fun setupFilterControls() {
        val studentFilters = arrayOf("All My Students", "Late", "Overdue", "Me")
        spinnerStatusFilter.adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            studentFilters
        )
        spinnerStatusFilter.onItemSelectedListener = SimpleItemSelectedListener { position ->
            currentStudentFilter = studentFilters[position]
            applyFilters()
        }

        val yearOptions = arrayOf("All Years", "Year 1", "Year 2", "Year 3", "Year 4")
        spinnerYearFilter.adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            yearOptions
        )
        spinnerYearFilter.onItemSelectedListener = SimpleItemSelectedListener { position ->
            currentYearFilter = yearOptions[position]
            applyFilters()
        }

        val sortOptions = arrayOf("Name: A-Z", "Name: Z-A", "Recently Added")
        spinnerSortStudents.adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            sortOptions
        )
        spinnerSortStudents.onItemSelectedListener = SimpleItemSelectedListener { position ->
            currentSort = sortOptions[position]
            applyFilters()
        }

        etSearchStudents.doAfterTextChanged { text ->
            currentSearch = text?.toString()?.trim().orEmpty()
            applyFilters()
        }
    }

    private fun setupStudentsListener() {
        studentsListener = db.collection("users")
            .whereIn("role", listOf("student", "beneficiary"))
            .whereEqualTo("assignedCaseworkerId", staffUsername)
            .whereEqualTo("approvalStatus", "approved")
            .addSnapshotListener { snapshot, _ ->
                allStudents = snapshot?.documents?.map { doc ->
                    val firstName = valueOrBlank(doc.getString("FirstName"), doc.getString("firstName"))
                    val lastName = valueOrBlank(doc.getString("LastName"), doc.getString("lastName"))
                    Student(
                        id = doc.id,
                        benId = valueOrBlank(doc.getString("benId")),
                        name = valueOrBlank(doc.getString("name")).ifBlank {
                            listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").trim()
                        }.ifBlank { doc.id },
                        email = valueOrBlank(doc.getString("email"), doc.getString("guardEmail")),
                        section = valueOrBlank(doc.getString("section")),
                        birthday = valueOrBlank(doc.getString("Birthdate"), doc.getString("birthdate")),
                        year = valueOrBlank(doc.getString("year"), doc.getString("Grade"), doc.getString("grade")),
                        status = valueOrBlank(doc.getString("approvalStatus"), doc.getString("status")).ifBlank { "approved" },
                        approvalStatus = valueOrBlank(doc.getString("approvalStatus"), doc.getString("status")).ifBlank { "approved" },
                        assignedCaseworkerId = doc.getString("assignedCaseworkerId"),
                        assignedCaseworkerName = valueOrBlank(
                            doc.getString("assignedCaseworkerName"),
                            doc.getString("Assigned Caseworker")
                        ),
                        phoneNumber = valueOrBlank(doc.getString("phone"), doc.getString("guardMobile"), doc.getString("phoneNumber")),
                        address = valueOrBlank(doc.getString("address"), doc.getString("guardAddress"))
                    )
                }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
                hasLoadedStudents = true
                applyFilters()
            }
    }

    private fun setupLettersListener() {
        lettersListener = repository.listenToStaffLetters(staffUsername) { letters ->
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            lateStudentIds = letters
                .filter { it.status.equals("LATE", ignoreCase = true) }
                .mapTo(mutableSetOf()) { it.phNumber }

            overdueStudentIds = letters
                .filter { !it.status.equals("TURN IN", ignoreCase = true) && it.deadline.isNotBlank() && it.deadline < today }
                .mapTo(mutableSetOf()) { it.phNumber }

            hasLoadedLetters = true
            applyFilters()
        }
    }

    private fun applyFilters() {
        if (!hasLoadedStudents || !hasLoadedLetters) {
            adapter.updateData(emptyList())
            updateStudentCount(0, 0)
            updateEmptyState(false)
            return
        }

        val filteredStudents = allStudents
            .asSequence()
            .filter { student ->
                when (currentStudentFilter) {
                    "Late" -> lateStudentIds.contains(student.id) || lateStudentIds.contains(student.benId)
                    "Overdue" -> overdueStudentIds.contains(student.id) || overdueStudentIds.contains(student.benId)
                    else -> true
                }
            }
            .filter { student ->
                currentYearFilter == "All Years" ||
                    normalizeYear(student.year) == currentYearFilter
            }
            .filter { student ->
                if (currentSearch.isBlank()) return@filter true
                student.name.contains(currentSearch, ignoreCase = true) ||
                    student.email.contains(currentSearch, ignoreCase = true) ||
                    student.section.contains(currentSearch, ignoreCase = true) ||
                    student.id.contains(currentSearch, ignoreCase = true) ||
                    student.benId.contains(currentSearch, ignoreCase = true) ||
                    student.year.contains(currentSearch, ignoreCase = true)
            }
            .sortedWith(buildSortComparator())
            .toList()

        adapter.updateData(filteredStudents)
        updateStudentCount(filteredStudents.size, allStudents.size)
        updateEmptyState(allStudents.isNotEmpty())

        if (isFirstLoad) {
            isFirstLoad = false
            progressManager?.dismiss()
        }
    }

    private fun buildSortComparator(): Comparator<Student> {
        return when (currentSort) {
            "Name: Z-A" -> compareByDescending<Student> { it.name.lowercase(Locale.getDefault()) }
                .thenBy { it.id.lowercase(Locale.getDefault()) }
            "Recently Added" -> compareByDescending<Student> { it.createdAt?.time ?: 0L }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
            else -> compareBy<Student> { it.name.lowercase(Locale.getDefault()) }
                .thenBy { it.id.lowercase(Locale.getDefault()) }
        }
    }

    private fun normalizeYear(year: String): String {
        val trimmed = year.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("Year ", ignoreCase = true)) trimmed else "Year $trimmed"
    }

    private fun updateStudentCount(filtered: Int, totalAssigned: Int) {
        tvStudentCount.text = "$totalAssigned assigned"
        tvResultsSummary.text = when {
            totalAssigned == 0 -> "No approved students are assigned to you."
            currentStudentFilter == "All My Students" || currentStudentFilter == "Me" ->
                if (filtered == totalAssigned) "Showing all assigned students" else "Showing $filtered of $totalAssigned assigned students"
            else -> "Showing $filtered ${currentStudentFilter.lowercase(Locale.getDefault())} students"
        }
    }

    private fun updateEmptyState(hasAssignedStudents: Boolean) {
        val hasVisibleStudents = adapter.itemCount > 0
        layoutEmpty.visibility = if (hasVisibleStudents) View.GONE else View.VISIBLE
        rvStudents.visibility = if (hasVisibleStudents) View.VISIBLE else View.GONE

        tvEmptySubtext.text = when {
            !hasLoadedStudents || !hasLoadedLetters -> "Loading assigned students..."
            !hasAssignedStudents -> "Students appear here after approval and assignment."
            else -> "Try adjusting the search or filters."
        }
    }

    private fun valueOrBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }

    private fun showAddStudentDialog() {
        val dialog = AddStudentDialog.newInstance()
        dialog.setOnStudentAddedListener { _, message ->
            if (isAdded) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show(parentFragmentManager, "AddStudentDialog")
    }

    private fun showEditStudentDialog(student: Student) {
        val dialog = EditStudentDialog.newInstance(student)
        dialog.setOnStudentUpdatedListener { _, message ->
            if (isAdded) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show(parentFragmentManager, "EditStudentDialog")
    }

    private fun showDeleteConfirmation(student: Student) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Student")
            .setMessage("Are you sure you want to delete ${student.name}? This action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteStudent(student)
            }
            .show()
    }

    private fun deleteStudent(student: Student) {
        val batch = db.batch()
        batch.delete(db.collection("students").document(student.id))
        batch.delete(db.collection("users").document(student.id))
        batch.commit()
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "${student.name} deleted successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        studentsListener?.remove()
        lettersListener?.let { repository.removeListener(it) }
    }
}
