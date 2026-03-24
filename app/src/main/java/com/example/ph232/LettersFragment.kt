package com.example.ph232

import android.content.Context
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
import com.google.firebase.firestore.ListenerRegistration

class LettersFragment : Fragment() {

    private lateinit var tvPendingCount: TextView
    private lateinit var tabToDo: TextView
    private lateinit var tabCompleted: TextView
    private lateinit var rvLetters: RecyclerView
    private lateinit var adapter: LettersAdapter
    private lateinit var repository: FirebaseRepository

    private var isToDoSelected = true
    private var pendingLetters = mutableListOf<Letter>()
    private var completedLetters = mutableListOf<Letter>()
    private var lettersListener: ListenerRegistration? = null
    private var studentId: String = ""
    private var progressManager: ProgressManager? = null
    private var isFirstLoad = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_letters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        // Get student ID from shared preferences
        val sharedPreferences = requireContext().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        studentId = sharedPreferences.getString("USER_PH", "") ?: ""

        tvPendingCount = view.findViewById(R.id.tvPendingCount)
        tabToDo = view.findViewById(R.id.tabToDo)
        tabCompleted = view.findViewById(R.id.tabCompleted)
        rvLetters = view.findViewById(R.id.rvLetters)

        // Setup RecyclerView
        adapter = LettersAdapter(pendingLetters) { letter ->
            markLetterAsTurnedIn(letter)
        }
        rvLetters.layoutManager = LinearLayoutManager(requireContext())
        rvLetters.adapter = adapter

        // Setup real-time listener for letters
        progressManager = ProgressManager(requireContext())
        progressManager?.show("Loading letters...")
        setupLettersListener()

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

    private fun setupLettersListener() {
        // Use real-time listener for automatic sync
        lettersListener = repository.listenToLetters { letters ->
            pendingLetters.clear()
            completedLetters.clear()

            for (letter in letters) {
                // Filter by student ID if available, otherwise show all
                if (studentId.isEmpty() || letter.studentId == studentId || letter.studentId.isEmpty()) {
                    val status = letter.status.lowercase()
                    if (status == "completed" || status == "turned in" || status == "turned_in" || letter.isCompleted) {
                        completedLetters.add(letter.copy(isCompleted = true))
                    } else {
                        pendingLetters.add(letter.copy(isCompleted = false))
                    }
                }
            }

            // Update UI
            updatePendingCount(pendingLetters.size)
            if (isToDoSelected) {
                adapter.updateData(pendingLetters)
            } else {
                adapter.updateData(completedLetters)
            }
            if (isFirstLoad) {
                isFirstLoad = false
                progressManager?.dismiss()
            }
        }
    }

    private fun markLetterAsTurnedIn(letter: Letter) {
        if (letter.id.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot update letter without ID", Toast.LENGTH_SHORT).show()
            return
        }

        repository.updateLetterStatus(
            letterId = letter.id,
            status = "Turned In",
            isCompleted = true,
            onSuccess = {
                Toast.makeText(requireContext(), "Marked as turned in", Toast.LENGTH_SHORT).show()
                // No need to reload - real-time listener will update automatically
            },
            onFailure = { e ->
                Toast.makeText(requireContext(), "Error updating letter: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
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

    override fun onDestroyView() {
        super.onDestroyView()
        progressManager?.dismiss()
        lettersListener?.remove()
    }
}

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
