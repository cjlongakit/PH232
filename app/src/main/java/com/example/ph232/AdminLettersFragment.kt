package com.example.ph232

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.ListenerRegistration

class AdminLettersFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private lateinit var lettersContainer: LinearLayout
    private var lettersListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true
    private var allLetters = mutableListOf<Letter>()
    private var currentFilter = "All"
    private var currentSearch = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_letters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()
        lettersContainer = view.findViewById(R.id.lettersContainer)

        // Wire up search
        val etSearch = view.findViewById<EditText>(R.id.etSearchLetters)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearch = s.toString().trim()
                filterAndDisplayLetters()
            }
        })

        // Wire up chip group filters
        val chipGroupView = findChipGroup(view)
        chipGroupView?.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                currentFilter = chip?.text?.toString() ?: "All"
            } else {
                currentFilter = "All"
            }
            filterAndDisplayLetters()
        }

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading letters...")
        setupLettersListener()
    }

    private fun findChipGroup(view: View): ChipGroup? {
        if (view is ChipGroup) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findChipGroup(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun setupLettersListener() {
        lettersListener = repository.listenToLetters { letters ->
            allLetters.clear()
            allLetters.addAll(letters)
            filterAndDisplayLetters()

            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
            }
        }
    }

    private fun filterAndDisplayLetters() {
        lettersContainer.removeAllViews()

        var filtered = allLetters.toList()

        // Apply status filter
        when (currentFilter.lowercase()) {
            "pending" -> filtered = filtered.filter { it.status.lowercase() in listOf("pending") }
            "on hand" -> filtered = filtered.filter { it.status.lowercase() in listOf("on hand", "on_hand") }
            "outdated" -> filtered = filtered.filter { it.status.lowercase() in listOf("outdated", "late") }
            "turned in" -> filtered = filtered.filter { it.status.lowercase() in listOf("turned in", "turned_in", "completed") }
        }

        // Apply search filter
        if (currentSearch.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.contains(currentSearch, ignoreCase = true) ||
                it.title.contains(currentSearch, ignoreCase = true) ||
                it.studentName.contains(currentSearch, ignoreCase = true) ||
                it.studentId.contains(currentSearch, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            addEmptyStateView()
        } else {
            for (letter in filtered) {
                addLetterCard(letter)
            }
        }
    }

    private fun addLetterCard(letter: Letter) {
        val cardView = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 12f
            cardElevation = 4f
            setContentPadding(24, 16, 24, 16)
        }

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val nameView = TextView(requireContext()).apply {
            text = letter.name.ifEmpty { letter.title.ifEmpty { "Untitled Letter" } }
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val studentView = TextView(requireContext()).apply {
            text = "Student: ${letter.studentName.ifEmpty { letter.studentId.ifEmpty { "All Students" } }}"
            textSize = 14f
            setTextColor(resources.getColor(R.color.purple_primary, null))
        }

        val deadlineView = TextView(requireContext()).apply {
            text = "Deadline: ${letter.deadline.ifEmpty { "No deadline set" }}"
            textSize = 14f
            setTextColor(resources.getColor(R.color.gray_text, null))
        }

        val statusView = TextView(requireContext()).apply {
            text = "Status: ${letter.status.ifEmpty { "Pending" }}"
            textSize = 12f
            setTextColor(
                when (letter.status.lowercase()) {
                    "turned in", "turned_in", "completed" -> resources.getColor(R.color.status_turned_in, null)
                    "on hand", "pending" -> resources.getColor(R.color.status_on_hand, null)
                    else -> resources.getColor(R.color.gray_text, null)
                }
            )
        }

        val dateCreatedView = TextView(requireContext()).apply {
            text = "Created: ${letter.dateCreated.ifEmpty { "Unknown" }}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.gray_text, null))
        }

        contentLayout.addView(nameView)
        contentLayout.addView(studentView)
        contentLayout.addView(deadlineView)
        contentLayout.addView(statusView)
        contentLayout.addView(dateCreatedView)

        // Show assigned caseworker if set
        val assignedBy = letter.assignedBy
        if (assignedBy.isNotEmpty() && assignedBy != "admin") {
            val caseworkerView = TextView(requireContext()).apply {
                text = "Caseworker: $assignedBy"
                textSize = 12f
                setTextColor(resources.getColor(R.color.purple_light, null))
            }
            contentLayout.addView(caseworkerView)
        }

        cardView.addView(contentLayout)

        // Click on card to assign caseworker
        cardView.setOnClickListener {
            showAssignCaseworkerDialog(letter)
        }

        lettersContainer.addView(cardView)
    }

    private fun showAssignCaseworkerDialog(letter: Letter) {
        // Fetch all staff users to populate the dropdown
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "staff")
            .get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener
                val caseworkerNames = mutableListOf<String>()
                val caseworkerIds = mutableListOf<String>()

                for (doc in documents) {
                    val first = doc.getString("FirstName") ?: ""
                    val last = doc.getString("LastName") ?: ""
                    val name = "$first $last".trim().ifEmpty { doc.id }
                    caseworkerNames.add(name)
                    caseworkerIds.add(doc.id)
                }

                if (caseworkerNames.isEmpty()) {
                    Toast.makeText(requireContext(), "No caseworkers found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val currentIndex = caseworkerNames.indexOfFirst {
                    it.equals(letter.assignedBy, ignoreCase = true)
                }.coerceAtLeast(0)

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Assign Caseworker")
                    .setSingleChoiceItems(caseworkerNames.toTypedArray(), currentIndex) { dialog, which ->
                        val selectedName = caseworkerNames[which]
                        repository.updateLetterStatus(
                            letterId = letter.id,
                            status = letter.status,
                            isCompleted = letter.isCompleted,
                            onSuccess = {
                                // Update assignedBy field separately
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("letters").document(letter.id)
                                    .update("assignedBy", selectedName)
                                    .addOnSuccessListener {
                                        if (isAdded) Toast.makeText(requireContext(), "Caseworker assigned: $selectedName", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            onFailure = { e ->
                                if (isAdded) Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
    }

    private fun addEmptyStateView() {
        val textView = TextView(requireContext()).apply {
            text = "No letters found"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        lettersContainer.addView(textView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        lettersListener?.remove()
    }
}
