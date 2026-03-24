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

class AdminStudentsFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private lateinit var studentsContainer: LinearLayout
    private var studentsListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

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

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading students...")
        setupStudentsListener()
    }

    private fun setupStudentsListener() {
        studentsListener = repository.listenToStudents { students ->
            studentsContainer.removeAllViews()

            if (students.isEmpty()) {
                addEmptyStateView()
            } else {
                for (student in students) {
                    addStudentCard(student)
                }
            }

            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
            }
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

        val emailView = TextView(requireContext()).apply {
            text = "Email: ${student.email.ifEmpty { "N/A" }}"
            textSize = 12f
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
        contentLayout.addView(emailView)
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

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        studentsListener?.remove()
    }
}
