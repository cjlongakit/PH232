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

class AdminEventsFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var eventsContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_events, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        eventsContainer = view.findViewById(R.id.eventsContainer)

        loadEvents()
    }

    private fun loadEvents() {
        db.collection("events")
            .get()
            .addOnSuccessListener { result ->
                eventsContainer.removeAllViews()

                if (result.isEmpty) {
                    addEmptyStateView()
                    return@addOnSuccessListener
                }

                for (document in result) {
                    val event = document.toObject(Event::class.java).copy(id = document.id)
                    addEventCard(event)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading events: ${e.message}", Toast.LENGTH_SHORT).show()
                addEmptyStateView()
            }
    }

    private fun addEventCard(event: Event) {
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
            text = event.name.ifEmpty { event.title.ifEmpty { "Untitled Event" } }
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val dateTimeView = TextView(requireContext()).apply {
            val dateStr = event.date.ifEmpty { "No date set" }
            val timeStr = event.time.ifEmpty { "" }
            text = if (timeStr.isNotEmpty()) "$dateStr at $timeStr" else dateStr
            textSize = 14f
            setTextColor(resources.getColor(R.color.gray_text, null))
        }

        val locationView = TextView(requireContext()).apply {
            text = "Location: ${event.location.ifEmpty { "Not specified" }}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.gray_text, null))
        }

        val qrView = TextView(requireContext()).apply {
            text = "QR Code: ${event.qrCode.ifEmpty { "Not generated" }}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.purple_primary, null))
        }

        contentLayout.addView(nameView)
        contentLayout.addView(dateTimeView)
        contentLayout.addView(locationView)
        contentLayout.addView(qrView)
        cardView.addView(contentLayout)
        eventsContainer.addView(cardView)
    }

    private fun addEmptyStateView() {
        val textView = TextView(requireContext()).apply {
            text = "No events found"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        eventsContainer.addView(textView)
    }
}
