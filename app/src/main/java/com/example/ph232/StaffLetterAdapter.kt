package com.example.ph232
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
data class LetterModel(
    val letterId: String = "",
    val phNumber: String = "",
    val studentName: String = "",
    val type: String = "",
    val deadline: String = "",
    val status: String = ""
)
class StaffLetterAdapter(
    private var letters: List<LetterModel>,
    private val onStatusClick: (LetterModel) -> Unit
) : RecyclerView.Adapter<StaffLetterAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvLetterType)
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
        val btnStatus: MaterialButton = view.findViewById(R.id.btnUpdateStatus)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_staff_letter, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val letter = letters[position]
        holder.tvType.text = letter.type
        holder.tvName.text = "${letter.studentName} (${letter.phNumber})"
        holder.tvDeadline.text = "Deadline: ${letter.deadline}"
        holder.btnStatus.text = letter.status.uppercase()
        when (letter.status.uppercase()) {
            "PENDING" -> {
                holder.btnStatus.setTextColor(Color.parseColor("#F59E0B"))
                holder.btnStatus.strokeColor = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            }
            "ON HAND" -> {
                holder.btnStatus.setTextColor(Color.parseColor("#3B82F6"))
                holder.btnStatus.strokeColor = ColorStateList.valueOf(Color.parseColor("#3B82F6"))
            }
            "TURN IN" -> {
                holder.btnStatus.setTextColor(Color.parseColor("#10B981"))
                holder.btnStatus.strokeColor = ColorStateList.valueOf(Color.parseColor("#10B981"))
            }
            "LATE" -> {
                holder.btnStatus.setTextColor(Color.parseColor("#EF4444"))
                holder.btnStatus.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            }
            else -> {
                holder.btnStatus.setTextColor(Color.GRAY)
                holder.btnStatus.strokeColor = ColorStateList.valueOf(Color.GRAY)
            }
        }
        holder.btnStatus.setOnClickListener { onStatusClick(letter) }
    }
    override fun getItemCount() = letters.size
    fun updateData(newLetters: List<LetterModel>) {
        letters = newLetters
        notifyDataSetChanged()
    }
}
