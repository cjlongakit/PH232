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

class AdminStudentsFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var studentsContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_students, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        studentsContainer = view.findViewById(R.id.studentsContainer)

        loadStudents()
    }

    private fun loadStudents() {
        db.collection("students")
            .get()
            .addOnSuccessListener { result ->
                studentsContainer.removeAllViews()

                if (result.isEmpty) {
                    addEmptyStateView()
                    return@addOnSuccessListener
                }

                for (document in result) {
                    val student = document.toObject(Student::class.java).copy(id = document.id)
                    addStudentCard(student)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
                addEmptyStateView()
            }
    }

    private fun addStudentCard(student: Student) {
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
            text = student.name.ifEmpty { "Unknown Student" }
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val idView = TextView(requireContext()).apply {
            text = "PH ${student.id}"
            textSize = 14f
            setTextColor(resources.getColor(R.color.gray_text, null))
        }

        val sectionView = TextView(requireContext()).apply {
            text = "Section: ${student.section.ifEmpty { "N/A" }} | Year: ${student.year.ifEmpty { "N/A" }}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.gray_text, null))
        }

        val statusView = TextView(requireContext()).apply {
            text = "Status: ${student.status.ifEmpty { "Active" }}"
            textSize = 12f
            setTextColor(
                when (student.status.lowercase()) {
                    "active" -> resources.getColor(R.color.status_turned_in, null)
                    "inactive" -> resources.getColor(R.color.status_on_hand, null)
                    else -> resources.getColor(R.color.gray_text, null)
                }
            )
        }

        contentLayout.addView(nameView)
        contentLayout.addView(idView)
        contentLayout.addView(sectionView)
        contentLayout.addView(statusView)
        cardView.addView(contentLayout)
        studentsContainer.addView(cardView)
    }

    private fun addEmptyStateView() {
        val textView = TextView(requireContext()).apply {
            text = "No students found"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        studentsContainer.addView(textView)
    }
}
