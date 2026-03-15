package com.example.ph232

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class AttendanceLogAdapter(
    private var logs: List<AttendanceLog>,
    private val onEditClick: (AttendanceLog) -> Unit,
    private val onDeleteClick: (AttendanceLog) -> Unit
) : RecyclerView.Adapter<AttendanceLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewStatusIndicator: View = view.findViewById(R.id.viewStatusIndicator)
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvScanTime: TextView = view.findViewById(R.id.tvScanTime)
        val tvEventName: TextView = view.findViewById(R.id.tvEventName)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvNotes: TextView = view.findViewById(R.id.tvNotes)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]

        holder.tvStudentName.text = log.studentName.ifEmpty { "Unknown Student" }
        holder.tvEventName.text = log.eventName.ifEmpty { "Attendance" }

        // Format time for display
        val displayTime = try {
            val sdf24 = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf12.format(sdf24.parse(log.scanTime) ?: Date())
        } catch (e: Exception) {
            log.scanTime
        }
        holder.tvScanTime.text = displayTime

        // Set status and colors
        when (log.status.lowercase()) {
            "present" -> {
                holder.tvStatus.text = "Present"
                holder.tvStatus.setTextColor(Color.parseColor("#10B981"))
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#10B981"))
            }
            "late" -> {
                holder.tvStatus.text = "Late"
                holder.tvStatus.setTextColor(Color.parseColor("#F59E0B"))
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#F59E0B"))
            }
            "absent" -> {
                holder.tvStatus.text = "Absent"
                holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#EF4444"))
            }
            "removed" -> {
                holder.tvStatus.text = "Removed"
                holder.tvStatus.setTextColor(Color.parseColor("#888888"))
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#888888"))
            }
            else -> {
                holder.tvStatus.text = log.status.replaceFirstChar { it.uppercase() }
                holder.tvStatus.setTextColor(Color.parseColor("#7B68EE"))
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#7B68EE"))
            }
        }

        // Show notes if available
        if (log.notes.isNotEmpty()) {
            holder.tvNotes.visibility = View.VISIBLE
            holder.tvNotes.text = "Note: ${log.notes}"
        } else {
            holder.tvNotes.visibility = View.GONE
        }

        // Click listeners
        holder.btnEdit.setOnClickListener { onEditClick(log) }
        holder.btnDelete.setOnClickListener { onDeleteClick(log) }
    }

    override fun getItemCount() = logs.size

    fun updateData(newLogs: List<AttendanceLog>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}

