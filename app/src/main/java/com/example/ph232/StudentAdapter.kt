package com.example.ph232

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class StudentAdapter(
    private var students: List<Student>,
    private val onEditClick: (Student) -> Unit,
    private val onDeleteClick: (Student) -> Unit
) : RecyclerView.Adapter<StudentAdapter.ViewHolder>() {

    private var filteredStudents: List<Student> = students

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitials: TextView = view.findViewById(R.id.tvInitials)
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStudentId: TextView = view.findViewById(R.id.tvStudentId)
        val tvStudentSection: TextView = view.findViewById(R.id.tvStudentSection)
        val tvStudentYear: TextView = view.findViewById(R.id.tvStudentYear)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val btnMore: ImageButton = view.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = filteredStudents[position]
        val context = holder.itemView.context

        // Set initials
        val initials = student.name.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
        holder.tvInitials.text = initials.ifEmpty { "?" }

        // Set student info
        holder.tvStudentName.text = student.name.ifEmpty { "Unknown Student" }
        holder.tvStudentId.text = "PH ${student.id}"
        holder.tvStudentSection.text = "Section ${student.section.ifEmpty { "N/A" }}"
        holder.tvStudentYear.text = "Year ${student.year.ifEmpty { "N/A" }}"

        // Set status
        when (student.status.lowercase()) {
            "active" -> {
                holder.tvStatus.text = "Active"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.green_500))
            }
            "inactive" -> {
                holder.tvStatus.text = "Inactive"
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.orange_500))
            }
            else -> {
                holder.tvStatus.text = student.status.replaceFirstChar { it.uppercase() }
                holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.gray_text))
            }
        }

        // Overflow menu
        holder.btnMore.setOnClickListener { view ->
            val popup = PopupMenu(context, view)
            popup.menu.add(0, 1, 0, "Edit Student")
            popup.menu.add(0, 2, 1, "Delete Student")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        onEditClick(student)
                        true
                    }
                    2 -> {
                        onDeleteClick(student)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount() = filteredStudents.size

    fun updateData(newStudents: List<Student>) {
        students = newStudents
        filteredStudents = newStudents
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredStudents = if (query.isEmpty()) {
            students
        } else {
            students.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.email.contains(query, ignoreCase = true) ||
                it.section.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true) ||
                it.year.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun getFilteredCount() = filteredStudents.size
    fun getTotalCount() = students.size
}

