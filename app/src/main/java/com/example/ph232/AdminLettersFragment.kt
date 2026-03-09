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
import com.google.firebase.firestore.FirebaseFirestore

class AdminLettersFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var lettersContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_letters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        lettersContainer = view.findViewById(R.id.lettersContainer)

        loadLetters()
    }

    private fun loadLetters() {
        db.collection("letters")
            .get()
            .addOnSuccessListener { result ->
                lettersContainer.removeAllViews()

                if (result.isEmpty) {
                    addEmptyStateView()
                    return@addOnSuccessListener
                }

                for (document in result) {
                    val letter = document.toObject(Letter::class.java).copy(id = document.id)
                    addLetterCard(letter)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading letters: ${e.message}", Toast.LENGTH_SHORT).show()
                addEmptyStateView()
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
                    "turned in", "completed" -> resources.getColor(R.color.status_turned_in, null)
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
}
