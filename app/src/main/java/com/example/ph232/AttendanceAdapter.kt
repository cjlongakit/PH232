package com.example.ph232

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttendanceAdapter(private var records: List<Attendance>) : RecyclerView.Adapter<AttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvAttName)
        val tvTime: TextView = view.findViewById(R.id.tvAttTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.tvName.text = record.studentName
        holder.tvTime.text = record.scanTime.ifEmpty { record.time }
    }

    override fun getItemCount() = records.size

    fun updateData(newRecords: List<Attendance>) {
        records = newRecords
        notifyDataSetChanged()
    }
}
