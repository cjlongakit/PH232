package com.example.ph232

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LettersFragment : Fragment() {

    private lateinit var tvPendingCount: TextView
    private lateinit var tabToDo: TextView
    private lateinit var tabCompleted: TextView
    private lateinit var rvLetters: RecyclerView
    private lateinit var adapter: LettersAdapter

    private var isToDoSelected = true

    // Sample data
    private val pendingLetters = listOf(
        Letter("Letters", "Pending", "Feb 2, 2026", false),
        Letter("Letters", "Pending", "Feb 2, 2026", false),
        Letter("Letters", "Pending", "Feb 2, 2026", false),
        Letter("Letters", "Pending", "Feb 2, 2026", false)
    )

    private val completedLetters = listOf(
        Letter("Letters", "Completed", "Jan 15, 2026", true),
        Letter("Letters", "Completed", "Jan 10, 2026", true)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_letters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvPendingCount = view.findViewById(R.id.tvPendingCount)
        tabToDo = view.findViewById(R.id.tabToDo)
        tabCompleted = view.findViewById(R.id.tabCompleted)
        rvLetters = view.findViewById(R.id.rvLetters)

        // Set pending count with bold number
        updatePendingCount(pendingLetters.size)

        // Setup RecyclerView
        adapter = LettersAdapter(pendingLetters) { letter ->
            Toast.makeText(requireContext(), "Marked as turned in", Toast.LENGTH_SHORT).show()
        }
        rvLetters.layoutManager = LinearLayoutManager(requireContext())
        rvLetters.adapter = adapter

        // Tab click listeners
        tabToDo.setOnClickListener {
            if (!isToDoSelected) {
                isToDoSelected = true
                updateTabUI()
                adapter.updateData(pendingLetters)
            }
        }

        tabCompleted.setOnClickListener {
            if (isToDoSelected) {
                isToDoSelected = false
                updateTabUI()
                adapter.updateData(completedLetters)
            }
        }
    }

    private fun updatePendingCount(count: Int) {
        val text = "You have $count Pending Letters"
        val spannable = SpannableString(text)
        val start = text.indexOf(count.toString())
        val end = start + count.toString().length
        spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // Also bold "Pending"
        val pendingStart = text.indexOf("Pending")
        val pendingEnd = pendingStart + "Pending".length
        spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), pendingStart, pendingEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvPendingCount.text = spannable
    }

    private fun updateTabUI() {
        if (isToDoSelected) {
            tabToDo.setBackgroundResource(R.drawable.tab_selected)
            tabToDo.setTextColor(resources.getColor(R.color.white, null))
            tabCompleted.setBackgroundResource(android.R.color.transparent)
            tabCompleted.setTextColor(resources.getColor(R.color.black, null))
        } else {
            tabCompleted.setBackgroundResource(R.drawable.tab_selected)
            tabCompleted.setTextColor(resources.getColor(R.color.white, null))
            tabToDo.setBackgroundResource(android.R.color.transparent)
            tabToDo.setTextColor(resources.getColor(R.color.black, null))
        }
    }
}

data class Letter(
    val title: String,
    val status: String,
    val deadline: String,
    val isCompleted: Boolean
)

class LettersAdapter(
    private var letters: List<Letter>,
    private val onMarkTurnedIn: (Letter) -> Unit
) : RecyclerView.Adapter<LettersAdapter.LetterViewHolder>() {

    class LetterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLetterTitle: TextView = view.findViewById(R.id.tvLetterTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
        val btnMarkTurnedIn: View = view.findViewById(R.id.btnMarkTurnedIn)
        val badgePending: View = view.findViewById(R.id.badgePending)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LetterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_letter, parent, false)
        return LetterViewHolder(view)
    }

    override fun onBindViewHolder(holder: LetterViewHolder, position: Int) {
        val letter = letters[position]
        holder.tvLetterTitle.text = letter.title
        holder.tvStatus.text = letter.status
        holder.tvDeadline.text = letter.deadline

        if (letter.isCompleted) {
            holder.badgePending.visibility = View.GONE
            holder.btnMarkTurnedIn.visibility = View.GONE
        } else {
            holder.badgePending.visibility = View.VISIBLE
            holder.btnMarkTurnedIn.visibility = View.VISIBLE
            holder.btnMarkTurnedIn.setOnClickListener {
                onMarkTurnedIn(letter)
            }
        }
    }

    override fun getItemCount() = letters.size

    fun updateData(newLetters: List<Letter>) {
        letters = newLetters
        notifyDataSetChanged()
    }
}
