package com.example.ph232
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
class StudentLetterAdapter(private var letters: List<LetterModel>) : RecyclerView.Adapter<StudentLetterAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvStudentLetterType)
        val tvAssignedBy: TextView = view.findViewById(R.id.tvAssignedBy)
        val tvDeadline: TextView = view.findViewById(R.id.tvStudentDeadline)
        val tvStatus: TextView = view.findViewById(R.id.tvStudentLetterStatus)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_letter, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val letter = letters[position]
        holder.tvType.text = letter.type
        holder.tvDeadline.text = letter.deadline
        holder.tvAssignedBy.text = "Assigned by: ${letter.phNumber}"
        holder.tvStatus.text = letter.status.uppercase()
        when (letter.status.uppercase()) {
            "TURN IN" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#10B981"))
                holder.tvStatus.setBackgroundColor(Color.parseColor("#D1FAE5"))
            }
            "LATE" -> {
                holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                holder.tvStatus.setBackgroundColor(Color.parseColor("#FEE2E2"))
            }
            else -> {
                holder.tvStatus.setTextColor(Color.parseColor("#F59E0B"))
                holder.tvStatus.setBackgroundColor(Color.parseColor("#FEF3C7"))
            }
        }
    }
    override fun getItemCount() = letters.size
    fun updateData(newLetters: List<LetterModel>) {
        letters = newLetters
        notifyDataSetChanged()
    }
}
