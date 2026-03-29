package com.example.ph232

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Locale

class StaffLettersFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var etSearch: TextInputEditText
    private lateinit var spinnerTypeFilter: Spinner
    private lateinit var spinnerSort: Spinner
    private lateinit var tvResultsSummary: TextView
    private lateinit var rvLetters: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var btnPrevPage: MaterialButton
    private lateinit var btnNextPage: MaterialButton
    private lateinit var tvPageInfo: TextView
    private lateinit var fabAddLetter: ExtendedFloatingActionButton

    private lateinit var repository: FirebaseRepository
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: StaffLetterAdapter

    private var allLetters = mutableListOf<StaffLetter>()
    private var visibleLetters = emptyList<StaffLetter>()
    private var assignedStudentIds = mutableSetOf<String>()
    private var hasLoadedAssignedStudents = false
    private var staffUsername: String = ""
    private var staffDisplayName: String = ""
    private var lettersListener: ListenerRegistration? = null
    private var assignedStudentsListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

    private var currentSearch = ""
    private var currentTypeFilter = "All Types"
    private var currentSort = "Deadline: Soonest"
    private var currentPage = 0
    private val pageSize = 8

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_staff_letters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        staffUsername = prefs.getString("USER_PH", "unknown") ?: "unknown"
        staffDisplayName = prefs.getString("USER_NAME", staffUsername) ?: staffUsername

        tabLayout = view.findViewById(R.id.tabLayoutLetters)
        etSearch = view.findViewById(R.id.etSearchLetters)
        spinnerTypeFilter = view.findViewById(R.id.spinnerTypeFilter)
        spinnerSort = view.findViewById(R.id.spinnerSort)
        tvResultsSummary = view.findViewById(R.id.tvResultsSummary)
        rvLetters = view.findViewById(R.id.rvStaffLetters)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        btnPrevPage = view.findViewById(R.id.btnPrevPage)
        btnNextPage = view.findViewById(R.id.btnNextPage)
        tvPageInfo = view.findViewById(R.id.tvPageInfo)
        fabAddLetter = view.findViewById(R.id.fabAddLetter)

        setupTabs()
        setupFilterControls()

        rvLetters.layoutManager = LinearLayoutManager(requireContext())
        adapter = StaffLetterAdapter(emptyList()) { letter ->
            showUpdateStatusDialog(letter)
        }
        rvLetters.adapter = adapter

        btnPrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                applyFiltersAndPagination()
            }
        }

        btnNextPage.setOnClickListener {
            val totalPages = getTotalPages(visibleLetters.size)
            if (currentPage < totalPages - 1) {
                currentPage++
                applyFiltersAndPagination()
            }
        }

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading letters...")
        setupAssignedStudentsListener()
        setupLettersListener()

        fabAddLetter.setOnClickListener {
            showAddLetterDialog()
        }
    }

    private fun setupTabs() {
        val statuses = listOf("ALL", "PENDING", "ON HAND", "TURN IN", "LATE")
        statuses.forEach { status ->
            tabLayout.addTab(tabLayout.newTab().setText(status))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentPage = 0
                applyFiltersAndPagination()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun setupFilterControls() {
        val typeOptions = arrayOf("All Types", "Gift", "Reply", "General", "Final Letter", "First Letter")
        spinnerTypeFilter.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, typeOptions)
        spinnerTypeFilter.onItemSelectedListener = SimpleItemSelectedListener { position ->
            currentTypeFilter = typeOptions[position]
            currentPage = 0
            applyFiltersAndPagination()
        }

        val sortOptions = arrayOf("Deadline: Soonest", "Deadline: Latest", "Student: A-Z", "Student: Z-A", "Newest First")
        spinnerSort.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortOptions)
        spinnerSort.onItemSelectedListener = SimpleItemSelectedListener { position ->
            currentSort = sortOptions[position]
            currentPage = 0
            applyFiltersAndPagination()
        }

        etSearch.doAfterTextChanged { text ->
            currentSearch = text?.toString()?.trim().orEmpty()
            currentPage = 0
            applyFiltersAndPagination()
        }
    }

    private fun setupAssignedStudentsListener() {
        assignedStudentsListener = db.collection("users")
            .whereIn("role", listOf("student", "beneficiary"))
            .addSnapshotListener { snapshot, _ ->
                val assignedIds = snapshot?.documents
                    ?.filter { isStudentAssignedToCurrentCaseworker(it) }
                    ?.mapTo(mutableSetOf()) { it.id }
                    ?: mutableSetOf()
                assignedStudentIds = assignedIds
                hasLoadedAssignedStudents = true
                applyFiltersAndPagination()
            }
    }

    private fun setupLettersListener() {
        lettersListener = repository.listenToStaffLetters(staffUsername) { letters ->
            allLetters.clear()
            allLetters.addAll(letters)
            applyFiltersAndPagination()
            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
            }
        }
    }

    private fun applyFiltersAndPagination() {
        val selectedStatus = tabLayout.getTabAt(tabLayout.selectedTabPosition)?.text?.toString().orEmpty()
        visibleLetters = allLetters
            .asSequence()
            .filter { letter ->
                hasLoadedAssignedStudents && assignedStudentIds.contains(letter.phNumber)
            }
            .filter { letter ->
                selectedStatus == "ALL" || letter.status.equals(selectedStatus, ignoreCase = true)
            }
            .filter { letter ->
                currentTypeFilter == "All Types" || letter.type.equals(currentTypeFilter, ignoreCase = true)
            }
            .filter { letter ->
                if (currentSearch.isBlank()) return@filter true
                letter.studentName.contains(currentSearch, ignoreCase = true) ||
                    letter.phNumber.contains(currentSearch, ignoreCase = true) ||
                    letter.type.contains(currentSearch, ignoreCase = true) ||
                    letter.deadline.contains(currentSearch, ignoreCase = true)
            }
            .sortedWith(buildSortComparator())
            .toList()

        val totalPages = getTotalPages(visibleLetters.size)
        if (currentPage >= totalPages) {
            currentPage = if (totalPages == 0) 0 else totalPages - 1
        }

        val pageStart = currentPage * pageSize
        val pageItems = visibleLetters.drop(pageStart).take(pageSize)
        adapter.updateData(pageItems)

        tvEmptyState.visibility = if (visibleLetters.isEmpty()) View.VISIBLE else View.GONE
        rvLetters.visibility = if (visibleLetters.isEmpty()) View.GONE else View.VISIBLE

        val shownStart = if (visibleLetters.isEmpty()) 0 else pageStart + 1
        val shownEnd = if (visibleLetters.isEmpty()) 0 else pageStart + pageItems.size
        tvResultsSummary.text = "Showing $shownStart-$shownEnd of ${visibleLetters.size} letters"
        tvPageInfo.text = if (totalPages == 0) "Page 0 of 0" else "Page ${currentPage + 1} of $totalPages"
        btnPrevPage.isEnabled = currentPage > 0
        btnNextPage.isEnabled = currentPage < totalPages - 1
    }

    private fun buildSortComparator(): Comparator<StaffLetter> {
        return when (currentSort) {
            "Deadline: Latest" -> compareByDescending<StaffLetter> { it.deadline }.thenBy { it.studentName.lowercase(Locale.getDefault()) }
            "Student: A-Z" -> compareBy<StaffLetter> { it.studentName.lowercase(Locale.getDefault()) }.thenBy { it.deadline }
            "Student: Z-A" -> compareByDescending<StaffLetter> { it.studentName.lowercase(Locale.getDefault()) }.thenBy { it.deadline }
            "Newest First" -> compareByDescending<StaffLetter> { it.dateCreated }.thenBy { it.deadline }
            else -> compareBy<StaffLetter> { it.deadline }.thenBy { it.studentName.lowercase(Locale.getDefault()) }
        }
    }

    private fun getTotalPages(totalItems: Int): Int {
        return if (totalItems == 0) 0 else ((totalItems - 1) / pageSize) + 1
    }

    private fun isStudentAssignedToCurrentCaseworker(doc: DocumentSnapshot): Boolean {
        val approvalStatus = doc.getString("approvalStatus")?.trim().orEmpty()
        val legacyStatus = doc.getString("status")?.trim().orEmpty()
        val isApproved = approvalStatus.equals("approved", ignoreCase = true) ||
            legacyStatus.equals("approved", ignoreCase = true) ||
            legacyStatus.equals("active", ignoreCase = true)
        val assignedCaseworkerId = doc.getString("assignedCaseworkerId")?.trim().orEmpty()
        val assignedCaseworker = doc.getString("Assigned Caseworker")?.trim().orEmpty()
        return isApproved && (
            assignedCaseworkerId.equals(staffUsername, ignoreCase = true) ||
                assignedCaseworker.equals(staffUsername, ignoreCase = true) ||
                assignedCaseworker.equals(staffDisplayName, ignoreCase = true)
            )
    }

    private fun showAddLetterDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etPhNumber = EditText(requireContext()).apply {
            hint = "PH323 ID (e.g. PH323-000)"
        }
        layout.addView(etPhNumber)

        val btnVerify = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Verify Student"
        }
        layout.addView(btnVerify)

        val tvStudentName = TextView(requireContext()).apply {
            text = "Name will appear here..."
            setPadding(0, 10, 0, 20)
        }
        layout.addView(tvStudentName)

        val spinnerType = Spinner(requireContext())
        val types = arrayOf("Gift", "Reply", "General", "Final Letter", "First Letter")
        spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)
        layout.addView(spinnerType)

        val etDeadline = EditText(requireContext()).apply {
            hint = "Select Deadline Date"
            isFocusable = false
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(requireContext(), { _, year, month, day ->
                    setText(String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        layout.addView(etDeadline)

        var verifiedName = ""
        btnVerify.setOnClickListener {
            val searchId = etPhNumber.text.toString().trim()
            if (searchId.isEmpty()) return@setOnClickListener

            db.collection("users").document(searchId).get().addOnSuccessListener { doc ->
                val role = doc.getString("role")?.trim().orEmpty()
                if (!doc.exists() || !(role.equals("beneficiary", ignoreCase = true) || role.equals("student", ignoreCase = true))) {
                    tvStudentName.text = "Student not found"
                    tvStudentName.setTextColor(Color.RED)
                    verifiedName = ""
                    return@addOnSuccessListener
                }

                if (!isStudentAssignedToCurrentCaseworker(doc)) {
                    tvStudentName.text = "This student is not assigned to you"
                    tvStudentName.setTextColor(Color.RED)
                    verifiedName = ""
                    return@addOnSuccessListener
                }

                val fName = doc.getString("FirstName") ?: doc.getString("firstName") ?: ""
                val lName = doc.getString("LastName") ?: doc.getString("lastName") ?: ""
                verifiedName = "$fName $lName".trim()
                tvStudentName.text = "Found: $verifiedName"
                tvStudentName.setTextColor(Color.parseColor("#10B981"))
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create New Letter")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val type = spinnerType.selectedItem.toString()
                val deadline = etDeadline.text.toString()
                val phId = etPhNumber.text.toString().trim()

                if (verifiedName.isNotEmpty() && deadline.isNotEmpty()) {
                    val letter = StaffLetter(
                        phNumber = phId,
                        studentName = verifiedName,
                        type = type,
                        deadline = deadline,
                        status = "PENDING",
                        caseworker = staffUsername
                    )
                    repository.addStaffLetter(
                        letter,
                        onSuccess = { Toast.makeText(requireContext(), "Letter Added!", Toast.LENGTH_SHORT).show() },
                        onFailure = { Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                    )
                } else {
                    Toast.makeText(requireContext(), "Please verify an assigned student and set a deadline.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpdateStatusDialog(letter: StaffLetter) {
        val statuses = arrayOf("PENDING", "ON HAND", "TURN IN", "LATE")
        var selectedIndex = statuses.indexOf(letter.status.uppercase())
        if (selectedIndex == -1) selectedIndex = 0

        AlertDialog.Builder(requireContext())
            .setTitle("Update Letter Status")
            .setSingleChoiceItems(statuses, selectedIndex) { dialog, which ->
                val newStatus = statuses[which]
                repository.updateStaffLetterStatus(
                    letter.id,
                    newStatus,
                    onSuccess = { Toast.makeText(requireContext(), "Status Updated", Toast.LENGTH_SHORT).show() },
                    onFailure = { Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                )
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        lettersListener?.let { repository.removeListener(it) }
        assignedStudentsListener?.remove()
    }
}
