package com.example.ph232

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class LettersFragment : Fragment() {

    private lateinit var tvPendingCount: TextView
    private lateinit var tvLettersSummary: TextView
    private lateinit var tvLettersPageInfo: TextView
    private lateinit var tvAttendanceSummary: TextView
    private lateinit var tvAttendancePageInfo: TextView
    private lateinit var tvAttendanceEmptyState: TextView
    private lateinit var tabToDo: TextView
    private lateinit var tabCompleted: TextView
    private lateinit var rvLetters: RecyclerView
    private lateinit var rvAttendanceRecords: RecyclerView
    private lateinit var btnLettersPrevPage: MaterialButton
    private lateinit var btnLettersNextPage: MaterialButton
    private lateinit var btnAttendancePrevPage: MaterialButton
    private lateinit var btnAttendanceNextPage: MaterialButton
    private lateinit var lettersAdapter: LettersAdapter
    private lateinit var attendanceAdapter: StudentAttendanceAdapter
    private lateinit var repository: FirebaseRepository

    private var isToDoSelected = true
    private var pendingLetters = mutableListOf<Letter>()
    private var completedLetters = mutableListOf<Letter>()
    private var attendanceRecords = mutableListOf<Attendance>()
    private var lettersListener: ListenerRegistration? = null
    private var attendanceListener: ListenerRegistration? = null
    private var studentId: String = ""
    private var progressManager: ProgressManager? = null
    private var isLettersLoaded = false
    private var isAttendanceLoaded = false

    private var currentLettersPage = 0
    private var currentAttendancePage = 0
    private val lettersPageSize = 5
    private val attendancePageSize = 5

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_letters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        val sharedPreferences = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        studentId = sharedPreferences.getString("USER_PH", "") ?: ""

        tvPendingCount = view.findViewById(R.id.tvPendingCount)
        tvLettersSummary = view.findViewById(R.id.tvLettersSummary)
        tvLettersPageInfo = view.findViewById(R.id.tvLettersPageInfo)
        tvAttendanceSummary = view.findViewById(R.id.tvAttendanceSummary)
        tvAttendancePageInfo = view.findViewById(R.id.tvAttendancePageInfo)
        tvAttendanceEmptyState = view.findViewById(R.id.tvAttendanceEmptyState)
        tabToDo = view.findViewById(R.id.tabToDo)
        tabCompleted = view.findViewById(R.id.tabCompleted)
        rvLetters = view.findViewById(R.id.rvLetters)
        rvAttendanceRecords = view.findViewById(R.id.rvAttendanceRecords)
        btnLettersPrevPage = view.findViewById(R.id.btnLettersPrevPage)
        btnLettersNextPage = view.findViewById(R.id.btnLettersNextPage)
        btnAttendancePrevPage = view.findViewById(R.id.btnAttendancePrevPage)
        btnAttendanceNextPage = view.findViewById(R.id.btnAttendanceNextPage)

        lettersAdapter = LettersAdapter(emptyList()) { letter ->
            markLetterAsTurnedIn(letter)
        }
        rvLetters.layoutManager = LinearLayoutManager(requireContext())
        rvLetters.adapter = lettersAdapter

        attendanceAdapter = StudentAttendanceAdapter(emptyList())
        rvAttendanceRecords.layoutManager = LinearLayoutManager(requireContext())
        rvAttendanceRecords.adapter = attendanceAdapter

        btnLettersPrevPage.setOnClickListener {
            if (currentLettersPage > 0) {
                currentLettersPage--
                updateLettersList()
            }
        }

        btnLettersNextPage.setOnClickListener {
            val totalPages = getTotalPages(getActiveLetters().size, lettersPageSize)
            if (currentLettersPage < totalPages - 1) {
                currentLettersPage++
                updateLettersList()
            }
        }

        btnAttendancePrevPage.setOnClickListener {
            if (currentAttendancePage > 0) {
                currentAttendancePage--
                updateAttendanceList()
            }
        }

        btnAttendanceNextPage.setOnClickListener {
            val totalPages = getTotalPages(attendanceRecords.size, attendancePageSize)
            if (currentAttendancePage < totalPages - 1) {
                currentAttendancePage++
                updateAttendanceList()
            }
        }

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading letters...")
        setupLettersListener()
        setupAttendanceListener()

        tabToDo.setOnClickListener {
            if (!isToDoSelected) {
                isToDoSelected = true
                currentLettersPage = 0
                updateTabUI()
                updateLettersList()
            }
        }

        tabCompleted.setOnClickListener {
            if (isToDoSelected) {
                isToDoSelected = false
                currentLettersPage = 0
                updateTabUI()
                updateLettersList()
            }
        }
    }

    private fun setupLettersListener() {
        lettersListener = repository.listenToLetters { letters ->
            pendingLetters.clear()
            completedLetters.clear()

            for (letter in letters) {
                if (studentId.isEmpty() || letter.studentId == studentId || letter.studentId.isEmpty()) {
                    val status = letter.status.lowercase(Locale.getDefault())
                    if (status == "completed" || status == "turned in" || status == "turned_in" || letter.isCompleted) {
                        completedLetters.add(letter.copy(isCompleted = true))
                    } else {
                        pendingLetters.add(letter.copy(isCompleted = false))
                    }
                }
            }

            updatePendingCount(pendingLetters.size)
            val totalPages = getTotalPages(getActiveLetters().size, lettersPageSize)
            if (currentLettersPage >= totalPages) {
                currentLettersPage = if (totalPages == 0) 0 else totalPages - 1
            }
            updateLettersList()
            isLettersLoaded = true
            dismissLoaderIfReady()
        }
    }

    private fun setupAttendanceListener() {
        if (studentId.isBlank()) {
            attendanceRecords.clear()
            updateAttendanceList()
            isAttendanceLoaded = true
            dismissLoaderIfReady()
            return
        }

        attendanceListener = repository.listenToAttendanceByStudent(studentId) { records ->
            attendanceRecords.clear()
            attendanceRecords.addAll(records)
            val totalPages = getTotalPages(attendanceRecords.size, attendancePageSize)
            if (currentAttendancePage >= totalPages) {
                currentAttendancePage = if (totalPages == 0) 0 else totalPages - 1
            }
            updateAttendanceList()
            isAttendanceLoaded = true
            dismissLoaderIfReady()
        }
    }

    private fun dismissLoaderIfReady() {
        if (isLettersLoaded && isAttendanceLoaded) {
            progressManager?.dismiss()
        }
    }

    private fun getActiveLetters(): List<Letter> {
        return if (isToDoSelected) pendingLetters else completedLetters
    }

    private fun updateLettersList() {
        val activeLetters = getActiveLetters()
        val totalPages = getTotalPages(activeLetters.size, lettersPageSize)
        if (currentLettersPage >= totalPages) {
            currentLettersPage = if (totalPages == 0) 0 else totalPages - 1
        }

        val pageStart = currentLettersPage * lettersPageSize
        val pageItems = activeLetters.drop(pageStart).take(lettersPageSize)
        lettersAdapter.updateData(pageItems)

        val shownStart = if (activeLetters.isEmpty()) 0 else pageStart + 1
        val shownEnd = if (activeLetters.isEmpty()) 0 else pageStart + pageItems.size
        val label = if (isToDoSelected) "pending" else "completed"
        tvLettersSummary.text = "Showing $shownStart-$shownEnd of ${activeLetters.size} $label letters"
        tvLettersPageInfo.text = if (totalPages == 0) "Page 0 of 0" else "Page ${currentLettersPage + 1} of $totalPages"
        btnLettersPrevPage.isEnabled = currentLettersPage > 0
        btnLettersNextPage.isEnabled = currentLettersPage < totalPages - 1
    }

    private fun updateAttendanceList() {
        val totalPages = getTotalPages(attendanceRecords.size, attendancePageSize)
        if (currentAttendancePage >= totalPages) {
            currentAttendancePage = if (totalPages == 0) 0 else totalPages - 1
        }

        val pageStart = currentAttendancePage * attendancePageSize
        val pageItems = attendanceRecords.drop(pageStart).take(attendancePageSize)
        attendanceAdapter.updateData(pageItems)

        val shownStart = if (attendanceRecords.isEmpty()) 0 else pageStart + 1
        val shownEnd = if (attendanceRecords.isEmpty()) 0 else pageStart + pageItems.size
        tvAttendanceSummary.text = "Showing $shownStart-$shownEnd of ${attendanceRecords.size} attendance records"
        tvAttendancePageInfo.text = if (totalPages == 0) "Page 0 of 0" else "Page ${currentAttendancePage + 1} of $totalPages"
        tvAttendanceEmptyState.visibility = if (attendanceRecords.isEmpty()) View.VISIBLE else View.GONE
        rvAttendanceRecords.visibility = if (attendanceRecords.isEmpty()) View.GONE else View.VISIBLE
        btnAttendancePrevPage.isEnabled = currentAttendancePage > 0
        btnAttendanceNextPage.isEnabled = currentAttendancePage < totalPages - 1
    }

    private fun getTotalPages(totalItems: Int, pageSize: Int): Int {
        return if (totalItems == 0) 0 else ((totalItems - 1) / pageSize) + 1
    }

    private fun markLetterAsTurnedIn(letter: Letter) {
        if (letter.id.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot update letter without ID", Toast.LENGTH_SHORT).show()
            return
        }

        repository.updateLetterStatus(
            letterId = letter.id,
            status = "Turned In",
            isCompleted = true,
            onSuccess = {
                Toast.makeText(requireContext(), "Marked as turned in", Toast.LENGTH_SHORT).show()
            },
            onFailure = { e ->
                Toast.makeText(requireContext(), "Error updating letter: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updatePendingCount(count: Int) {
        val text = if (count == 1) {
            "You have 1 Pending Letter"
        } else {
            "You have $count Pending Letters"
        }
        val spannable = SpannableString(text)
        val countText = count.toString()
        val start = text.indexOf(countText)
        val end = start + countText.length
        spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val pendingStart = text.indexOf("Pending")
        val pendingEnd = pendingStart + "Pending".length
        spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), pendingStart, pendingEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvPendingCount.text = spannable
    }

    private fun updateTabUI() {
        if (isToDoSelected) {
            tabToDo.setBackgroundResource(R.drawable.tab_selected)
            tabToDo.setTextColor(resources.getColor(R.color.white, null))
            tabCompleted.setBackgroundResource(android.R.color.transparent)
            tabCompleted.setTextColor(resources.getColor(R.color.black, null))
        } else {
            tabCompleted.setBackgroundResource(R.drawable.tab_selected)
            tabCompleted.setTextColor(resources.getColor(R.color.white, null))
            tabToDo.setBackgroundResource(android.R.color.transparent)
            tabToDo.setTextColor(resources.getColor(R.color.black, null))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        lettersListener?.remove()
        attendanceListener?.remove()
    }
}

class LettersAdapter(
    private var letters: List<Letter>,
    private val onMarkTurnedIn: (Letter) -> Unit
) : RecyclerView.Adapter<LettersAdapter.LetterViewHolder>() {

    class LetterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLetterTitle: TextView = view.findViewById(R.id.tvLetterTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
        val btnMarkTurnedIn: View = view.findViewById(R.id.btnMarkTurnedIn)
        val badgePending: View = view.findViewById(R.id.badgePending)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LetterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_letter, parent, false)
        return LetterViewHolder(view)
    }

    override fun onBindViewHolder(holder: LetterViewHolder, position: Int) {
        val letter = letters[position]
        holder.tvLetterTitle.text = letter.title.ifBlank { "Letter" }
        holder.tvStatus.text = if (letter.isCompleted) "Completed" else letter.status.ifBlank { "Pending" }
        holder.tvDeadline.text = letter.deadline.ifBlank { "No deadline" }

        if (letter.isCompleted) {
            holder.badgePending.visibility = View.GONE
            holder.btnMarkTurnedIn.visibility = View.GONE
        } else {
            holder.badgePending.visibility = View.VISIBLE
            holder.btnMarkTurnedIn.visibility = View.VISIBLE
            holder.btnMarkTurnedIn.setOnClickListener {
                onMarkTurnedIn(letter)
            }
        }
    }

    override fun getItemCount() = letters.size

    fun updateData(newLetters: List<Letter>) {
        letters = newLetters
        notifyDataSetChanged()
    }
}

class StudentAttendanceAdapter(
    private var records: List<Attendance>
) : RecyclerView.Adapter<StudentAttendanceAdapter.AttendanceViewHolder>() {

    class AttendanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvAttName)
        val tvTime: TextView = view.findViewById(R.id.tvAttTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val record = records[position]
        val eventLabel = record.eventName.ifBlank { "Attendance" }
        val dateLabel = record.date.ifBlank { "No date" }
        holder.tvName.text = eventLabel
        holder.tvTime.text = "$dateLabel - ${record.scanTime.ifBlank { record.time.ifBlank { "--:--" } }}"
    }

    override fun getItemCount() = records.size

    fun updateData(newRecords: List<Attendance>) {
        records = newRecords
        notifyDataSetChanged()
    }
}
