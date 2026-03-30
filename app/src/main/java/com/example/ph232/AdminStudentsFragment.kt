package com.example.ph232

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Locale

class AdminStudentsFragment : Fragment() {

    private data class CaseworkerOption(
        val id: String?,
        val label: String
    )

    private data class StudentAccount(
        val id: String,
        val benId: String,
        val fullName: String,
        val email: String,
        val phone: String,
        val birthdate: String,
        val address: String,
        val schoolName: String,
        val schoolAddress: String,
        val grade: String,
        val section: String,
        val year: String,
        val guardianName: String,
        val guardianEmail: String,
        val guardianMobile: String,
        val guardianAddress: String,
        val guardianOccupation: String,
        val guardianBirthdate: String,
        val approvalStatus: String,
        val assignedCaseworkerId: String?,
        val assignedCaseworkerName: String
    )

    private lateinit var repository: FirebaseRepository
    private lateinit var studentsContainer: LinearLayout
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvStudentCount: TextView
    private lateinit var tvResultsSummary: TextView
    private lateinit var tvEmptySubtext: TextView
    private lateinit var etSearchStudents: EditText
    private lateinit var dropdownCaseworker: AutoCompleteTextView
    private lateinit var btnAddStudent: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private var studentsListener: ListenerRegistration? = null
    private var caseworkersListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true
    private var hasLoadedStudents = false

    private var allStudents = emptyList<StudentAccount>()
    private var caseworkerOptions = listOf(
        CaseworkerOption(id = null, label = "All Students"),
        CaseworkerOption(id = "__unassigned__", label = "Unassigned")
    )
    private var currentSearch = ""
    private var selectedCaseworker = caseworkerOptions.first()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_students, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        studentsContainer = view.findViewById(R.id.studentsContainer)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        tvStudentCount = view.findViewById(R.id.tvStudentCount)
        tvResultsSummary = view.findViewById(R.id.tvResultsSummary)
        tvEmptySubtext = view.findViewById(R.id.tvEmptySubtext)
        etSearchStudents = view.findViewById(R.id.etSearchStudents)
        dropdownCaseworker = view.findViewById(R.id.dropdownCaseworker)
        btnAddStudent = view.findViewById(R.id.btnAddStudent)

        setupCaseworkerFilter()

        btnAddStudent.setOnClickListener {
            showAddStudentDialog()
        }

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading students...")
        listenToCaseworkers()
        listenToStudents()
    }

    private fun setupCaseworkerFilter() {
        etSearchStudents.doAfterTextChanged { text ->
            currentSearch = text?.toString()?.trim().orEmpty()
            renderFilteredStudents()
        }

        updateCaseworkerDropdown()
        dropdownCaseworker.setOnItemClickListener { _, _, position, _ ->
            selectedCaseworker = caseworkerOptions[position]
            listenToStudents()
        }
    }

    private fun listenToCaseworkers() {
        caseworkersListener = db.collection("staff")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val dynamicOptions = snapshot?.documents
                    ?.map {
                        CaseworkerOption(
                            id = it.id,
                            label = buildCaseworkerLabel(it)
                        )
                    }
                    ?.sortedBy { it.label.lowercase(Locale.getDefault()) }
                    ?: emptyList()

                caseworkerOptions = buildList {
                    add(CaseworkerOption(id = null, label = "All Students"))
                    add(CaseworkerOption(id = "__unassigned__", label = "Unassigned"))
                    addAll(dynamicOptions)
                }

                if (caseworkerOptions.none { it.id == selectedCaseworker.id && it.label == selectedCaseworker.label }) {
                    selectedCaseworker = caseworkerOptions.first()
                    listenToStudents()
                }
                updateCaseworkerDropdown()
            }
    }

    private fun updateCaseworkerDropdown() {
        if (!isAdded) return
        dropdownCaseworker.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                caseworkerOptions.map { it.label }
            )
        )
        dropdownCaseworker.setText(selectedCaseworker.label, false)
    }

    private fun listenToStudents() {
        studentsListener?.remove()
        hasLoadedStudents = false

        var query = db.collection("users")
            .whereIn("role", listOf("student", "beneficiary"))

        query = when (selectedCaseworker.id) {
            "__unassigned__" -> query.whereEqualTo("assignedCaseworkerId", null)
            null -> query
            else -> query.whereEqualTo("assignedCaseworkerId", selectedCaseworker.id)
        }

        studentsListener = query.addSnapshotListener { snapshot, _ ->
            allStudents = snapshot?.documents
                ?.map { toStudentAccount(it) }
                ?.sortedBy { it.fullName.lowercase(Locale.getDefault()) }
                ?: emptyList()
            hasLoadedStudents = true
            renderFilteredStudents()
            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
            }
        }
    }

    private fun renderFilteredStudents() {
        val filteredStudents = allStudents.filter { account ->
            if (currentSearch.isBlank()) return@filter true
            listOf(
                account.fullName,
                account.id,
                account.benId,
                account.email,
                account.guardianName,
                account.assignedCaseworkerName,
                account.schoolName,
                account.section,
                account.year
            ).any { it.contains(currentSearch, ignoreCase = true) }
        }

        studentsContainer.removeAllViews()
        filteredStudents.forEach { account ->
            studentsContainer.addView(createStudentCard(account))
        }

        updateSummary(filteredStudents.size)
        updateEmptyState(filteredStudents.isEmpty())
    }

    private fun createStudentCard(account: StudentAccount): View {
        return MaterialCardView(requireContext()).apply {
            radius = 18f
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(requireContext(), R.color.gray_200)
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            isClickable = true
            isFocusable = true
            rippleColor = ContextCompat.getColorStateList(requireContext(), R.color.purple_soft)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }

            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(18))
            }

            val headerRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleColumn = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(requireContext()).apply {
                text = account.fullName.ifBlank { "Unknown Student" }
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val idView = TextView(requireContext()).apply {
                text = account.benId.ifBlank { account.id }
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            titleColumn.addView(nameView)
            titleColumn.addView(idView)

            val statusView = TextView(requireContext()).apply {
                text = displayApprovalStatus(account.approvalStatus)
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_badge)
                setTextColor(resolveApprovalColor(account.approvalStatus))
            }

            headerRow.addView(titleColumn)
            headerRow.addView(statusView)

            val metaView = TextView(requireContext()).apply {
                text = buildString {
                    append(account.schoolName.ifBlank { "No school" })
                    if (account.grade.isNotBlank()) {
                        append(" • ")
                        append(account.grade)
                    }
                }
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
                setPadding(0, dp(10), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val caseworkerView = TextView(requireContext()).apply {
                text = "Caseworker: ${account.assignedCaseworkerName.ifBlank { "Unassigned" }}"
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))
                setPadding(0, dp(6), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val footerView = TextView(requireContext()).apply {
                text = account.email.ifBlank { account.guardianEmail.ifBlank { "No email available" } }
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_400))
                setPadding(0, dp(6), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            content.addView(headerRow)
            content.addView(metaView)
            content.addView(caseworkerView)
            content.addView(footerView)
            addView(content)

            setOnClickListener {
                showStudentReviewDialog(account)
            }
        }
    }

    private fun showStudentReviewDialog(account: StudentAccount) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_admin_student_review, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvStudentReviewTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvStudentReviewSubtitle)
        val dropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.dropdownAssignedCaseworker)
        val detailsContainer = dialogView.findViewById<LinearLayout>(R.id.layoutStudentReviewDetails)
        val btnSaveAssignment = dialogView.findViewById<MaterialButton>(R.id.btnSaveStudentAssignment)
        val btnApprove = dialogView.findViewById<MaterialButton>(R.id.btnApproveStudent)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseStudentReview)

        val assignableCaseworkers = caseworkerOptions.filter { !it.id.isNullOrBlank() && it.id != "__unassigned__" }
        dropdown.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                assignableCaseworkers.map { it.label }
            )
        )

        val selectedCaseworker = assignableCaseworkers.firstOrNull {
            it.id == account.assignedCaseworkerId
        }
        dropdown.setText(selectedCaseworker?.label.orEmpty(), false)

        tvTitle.text = account.fullName.ifBlank { "Student Review" }
        tvSubtitle.text = "Status: ${displayApprovalStatus(account.approvalStatus)}"

        addDetailRow(detailsContainer, "Student ID", account.benId.ifBlank { account.id })
        addDetailRow(detailsContainer, "Email", account.email.ifBlank { account.guardianEmail })
        addDetailRow(detailsContainer, "Phone", account.phone.ifBlank { account.guardianMobile })
        addDetailRow(detailsContainer, "School", account.schoolName)
        addDetailRow(detailsContainer, "School Address", account.schoolAddress)
        addDetailRow(detailsContainer, "Grade", account.grade)
        addDetailRow(detailsContainer, "Guardian", account.guardianName)
        addDetailRow(detailsContainer, "Guardian Email", account.guardianEmail)
        addDetailRow(detailsContainer, "Guardian Mobile", account.guardianMobile)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSaveAssignment.setOnClickListener {
            val chosen = assignableCaseworkers.firstOrNull { it.label == dropdown.text.toString().trim() }
            if (chosen == null) {
                Toast.makeText(requireContext(), "Select a caseworker first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            persistAssignment(account, chosen, approveStudent = false, dialog = null)
        }

        btnApprove.isEnabled = account.approvalStatus != "approved"
        btnApprove.setOnClickListener {
            val chosen = assignableCaseworkers.firstOrNull { it.label == dropdown.text.toString().trim() }
            if (chosen == null) {
                Toast.makeText(requireContext(), "Assign a caseworker before approval.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            persistAssignment(account, chosen, approveStudent = true, dialog = dialog)
        }

        dialog.show()
    }

    private fun persistAssignment(
        account: StudentAccount,
        caseworker: CaseworkerOption,
        approveStudent: Boolean,
        dialog: androidx.appcompat.app.AlertDialog?
    ) {
        val targetStatus = if (approveStudent) "approved" else account.approvalStatus.ifBlank { "pending" }
        repository.updateStudentVerification(
            userId = account.id,
            studentProfileId = account.id,
            assignedCaseworkerId = caseworker.id.orEmpty(),
            assignedCaseworkerName = caseworker.label,
            approvalStatus = targetStatus,
            onSuccess = {
                if (!isAdded) return@updateStudentVerification
                Toast.makeText(
                    requireContext(),
                    if (approveStudent) "Student approved." else "Caseworker assigned.",
                    Toast.LENGTH_SHORT
                ).show()
                dialog?.dismiss()
            },
            onFailure = { exception ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun updateSummary(filteredCount: Int) {
        tvStudentCount.text = if (filteredCount == 1) "1 student" else "$filteredCount students"
        tvResultsSummary.text = when {
            !hasLoadedStudents -> "Loading students..."
            selectedCaseworker.id == null -> "Showing all student accounts"
            selectedCaseworker.id == "__unassigned__" -> "Showing unassigned student accounts"
            else -> "Showing students for ${selectedCaseworker.label}"
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        studentsContainer.visibility = if (isEmpty) View.GONE else View.VISIBLE
        tvEmptySubtext.text = when {
            !hasLoadedStudents -> "Loading student records..."
            currentSearch.isNotBlank() -> "Try a different search term."
            selectedCaseworker.id == "__unassigned__" -> "No unassigned students found."
            selectedCaseworker.id != null -> "No students match this caseworker."
            else -> "Students will appear here once records are available."
        }
    }

    private fun buildCaseworkerLabel(doc: DocumentSnapshot): String {
        return valueOrBlank(doc.getString("name"), doc.getString("email")).ifBlank { doc.id }
    }

    private fun toStudentAccount(doc: DocumentSnapshot): StudentAccount {
        val firstName = valueOrBlank(doc.getString("FirstName"), doc.getString("firstName"))
        val lastName = valueOrBlank(doc.getString("LastName"), doc.getString("lastName"))
        val fullName = valueOrBlank(doc.getString("name")).ifBlank {
            listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        }
        val approvalStatus = valueOrBlank(doc.getString("approvalStatus"), doc.getString("status")).ifBlank { "pending" }

        return StudentAccount(
            id = doc.id,
            benId = valueOrBlank(doc.getString("benId")),
            fullName = fullName.ifBlank { doc.id },
            email = valueOrBlank(doc.getString("email"), doc.getString("guardEmail")),
            phone = valueOrBlank(doc.getString("phone"), doc.getString("phoneNumber"), doc.getString("guardMobile")),
            birthdate = valueOrBlank(doc.getString("Birthdate"), doc.getString("birthdate")),
            address = valueOrBlank(doc.getString("address"), doc.getString("guardAddress")),
            schoolName = valueOrBlank(doc.getString("SchoolName"), doc.getString("schoolName")),
            schoolAddress = valueOrBlank(doc.getString("SchoolAddress"), doc.getString("schoolAddress")),
            grade = valueOrBlank(doc.getString("Grade"), doc.getString("grade")),
            section = valueOrBlank(doc.getString("section")),
            year = valueOrBlank(doc.getString("year")),
            guardianName = listOf(
                valueOrBlank(doc.getString("guardFirstName"), doc.getString("GuardianFirstName")),
                valueOrBlank(doc.getString("guardLastName"), doc.getString("GuardianLastName"))
            ).filter { it.isNotBlank() }.joinToString(" ").trim(),
            guardianEmail = valueOrBlank(doc.getString("guardEmail"), doc.getString("guardianEmail")),
            guardianMobile = valueOrBlank(doc.getString("guardMobile"), doc.getString("guardianMobile")),
            guardianAddress = valueOrBlank(doc.getString("guardAddress"), doc.getString("guardianAddress")),
            guardianOccupation = valueOrBlank(doc.getString("guardOccupation"), doc.getString("guardianOccupation")),
            guardianBirthdate = valueOrBlank(doc.getString("guardBirthdate"), doc.getString("guardianBirthdate")),
            approvalStatus = approvalStatus.lowercase(Locale.getDefault()),
            assignedCaseworkerId = doc.getString("assignedCaseworkerId"),
            assignedCaseworkerName = valueOrBlank(
                doc.getString("assignedCaseworkerName"),
                doc.getString("Assigned Caseworker")
            )
        )
    }

    private fun displayApprovalStatus(status: String): String {
        return when (status.lowercase(Locale.getDefault())) {
            "approved", "active" -> "Approved"
            else -> "Pending"
        }
    }

    private fun resolveApprovalColor(status: String): Int {
        return when (status.lowercase(Locale.getDefault())) {
            "approved", "active" -> ContextCompat.getColor(requireContext(), R.color.green_500)
            else -> ContextCompat.getColor(requireContext(), R.color.orange_500)
        }
    }

    private fun addDetailRow(container: LinearLayout, label: String, value: String?) {
        val safeValue = value?.trim().orEmpty().ifBlank { "Not set" }
        container.addView(TextView(requireContext()).apply {
            text = "$label: $safeValue"
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))
            setPadding(0, dp(8), 0, 0)
        })
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        studentsListener?.remove()
        caseworkersListener?.remove()
    }
}
