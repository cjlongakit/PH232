package com.example.ph232

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.ListenerRegistration

class AdminLettersFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private lateinit var lettersContainer: LinearLayout
    private var lettersListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

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

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading letters...")
        setupLettersListener()
    }

    private fun setupLettersListener() {
        lettersListener = repository.listenToLetters { letters ->
            lettersContainer.removeAllViews()

            if (letters.isEmpty()) {
                addEmptyStateView()
            } else {
                for (letter in letters) {
                    addLetterCard(letter)
                }
            }

            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
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
        cardView.addView(contentLayout)
        lettersContainer.addView(cardView)
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
