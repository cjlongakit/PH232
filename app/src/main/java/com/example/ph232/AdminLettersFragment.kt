package com.example.ph232

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class AdminLettersFragment : Fragment() {

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
    private lateinit var adapter: AdminLetterMonitorAdapter

    private val db = FirebaseFirestore.getInstance()
    private var lettersListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

    private var allLetters = mutableListOf<StaffLetter>()
    private var visibleLetters = emptyList<StaffLetter>()
    private var currentSearch = ""
    private var currentTypeFilter = "All Types"
    private var currentSort = "Deadline: Soonest"
    private var currentPage = 0
    private val pageSize = 8

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_admin_letters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tabLayoutLetters)
        etSearch = view.findViewById(R.id.etSearchLetters)
        spinnerTypeFilter = view.findViewById(R.id.spinnerTypeFilter)
        spinnerSort = view.findViewById(R.id.spinnerSort)
        tvResultsSummary = view.findViewById(R.id.tvResultsSummary)
        rvLetters = view.findViewById(R.id.rvAdminLetters)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        btnPrevPage = view.findViewById(R.id.btnPrevPage)
        btnNextPage = view.findViewById(R.id.btnNextPage)
        tvPageInfo = view.findViewById(R.id.tvPageInfo)

        setupTabs()
        setupFilterControls()

        rvLetters.layoutManager = LinearLayoutManager(requireContext())
        adapter = AdminLetterMonitorAdapter(emptyList())
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
        setupLettersListener()
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

    private fun setupLettersListener() {
        lettersListener = db.collection("staff_letters")
            .addSnapshotListener { snapshot, _ ->
                allLetters = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toObject(StaffLetter::class.java)?.copy(id = doc.id) }
                    ?.toMutableList()
                    ?: mutableListOf()

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
                    letter.deadline.contains(currentSearch, ignoreCase = true) ||
                    letter.caseworker.contains(currentSearch, ignoreCase = true)
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

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        lettersListener?.remove()
    }
}

class AdminLetterMonitorAdapter(
    private var letters: List<StaffLetter>
) : RecyclerView.Adapter<AdminLetterMonitorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvLetterType)
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvCaseworker: TextView = view.findViewById(R.id.tvCaseworker)
        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
        val tvCreated: TextView = view.findViewById(R.id.tvCreated)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_letter_monitor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val letter = letters[position]
        holder.tvType.text = letter.type.ifBlank { "General" }
        holder.tvStudentName.text = "${letter.studentName.ifBlank { "Unknown Student" }} (${letter.phNumber.ifBlank { "No PH ID" }})"
        holder.tvCaseworker.text = "Caseworker: ${letter.caseworker.ifBlank { "Unassigned" }}"
        holder.tvDeadline.text = "Deadline: ${letter.deadline.ifBlank { "No deadline set" }}"
        holder.tvCreated.text = "Created: ${letter.dateCreated.ifBlank { "Unknown" }}"
        holder.tvStatus.text = letter.status.ifBlank { "PENDING" }.uppercase(Locale.getDefault())
    }

    override fun getItemCount() = letters.size

    fun updateData(newLetters: List<StaffLetter>) {
        letters = newLetters
        notifyDataSetChanged()
    }
}
