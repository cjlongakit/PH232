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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration

class StaffStudentsFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private lateinit var adapter: StudentAdapter
    private lateinit var rvStudents: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvStudentCount: TextView
    private lateinit var etSearchStudents: EditText
    private lateinit var btnAddStudent: MaterialButton

    private var studentsListener: ListenerRegistration? = null
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_staff_students, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        rvStudents = view.findViewById(R.id.rvStudents)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        tvStudentCount = view.findViewById(R.id.tvStudentCount)
        etSearchStudents = view.findViewById(R.id.etSearchStudents)
        btnAddStudent = view.findViewById(R.id.btnAddStudent)

        adapter = StudentAdapter(
            students = emptyList(),
            onEditClick = { student -> showEditStudentDialog(student) },
            onDeleteClick = { student -> showDeleteConfirmation(student) }
        )
        rvStudents.layoutManager = LinearLayoutManager(requireContext())
        rvStudents.adapter = adapter

        btnAddStudent.setOnClickListener {
            showAddStudentDialog()
        }

        etSearchStudents.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                adapter.filter(query)
                updateStudentCount()
                updateEmptyState()
            }
        })

        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading students...")
        setupStudentsListener()
    }

    private fun setupStudentsListener() {
        studentsListener = repository.listenToStudents { students ->
            adapter.updateData(students)
            updateStudentCount()
            updateEmptyState()
            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
            }
        }
    }

    private fun updateStudentCount() {
        val filtered = adapter.getFilteredCount()
        val total = adapter.getTotalCount()
        tvStudentCount.text = if (filtered == total) {
            "$total students"
        } else {
            "$filtered of $total students"
        }
    }

    private fun updateEmptyState() {
        if (adapter.getFilteredCount() == 0) {
            layoutEmpty.visibility = View.VISIBLE
            rvStudents.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvStudents.visibility = View.VISIBLE
        }
    }

    private fun showAddStudentDialog() {
        val dialog = AddStudentDialog.newInstance()
        dialog.setOnStudentAddedListener { success, message ->
            if (isAdded) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show(parentFragmentManager, "AddStudentDialog")
    }

    private fun showEditStudentDialog(student: Student) {
        val dialog = EditStudentDialog.newInstance(student)
        dialog.setOnStudentUpdatedListener { success, message ->
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
        repository.deleteStudent(
            studentId = student.id,
            onSuccess = {
                if (isAdded) {
                    Toast.makeText(requireContext(), "${student.name} deleted successfully", Toast.LENGTH_SHORT).show()
                }
            },
            onFailure = { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        studentsListener?.remove()
    }
}
