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

class AdminEventsFragment : Fragment() {

    private lateinit var repository: FirebaseRepository
    private lateinit var eventsContainer: LinearLayout
    private var eventsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_events, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()
        eventsContainer = view.findViewById(R.id.eventsContainer)

        setupEventsListener()
    }

    private fun setupEventsListener() {
        // Use real-time listener for automatic sync
        eventsListener = repository.listenToEvents { events ->
            eventsContainer.removeAllViews()

            if (events.isEmpty()) {
                addEmptyStateView()
                return@listenToEvents
            }

            for (event in events) {
                addEventCard(event)
            }
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
            text = if (event.qrCode.isNotEmpty()) "QR Code: Ready" else "QR Code: Not generated"
            textSize = 12f
            setTextColor(resources.getColor(R.color.purple_primary, null))
        }

        val statusView = TextView(requireContext()).apply {
            text = if (event.isActive) "Status: Active" else "Status: Inactive"
            textSize = 12f
            setTextColor(
                if (event.isActive) resources.getColor(R.color.status_turned_in, null)
                else resources.getColor(R.color.gray_text, null)
            )
        }

        // Show QR Button
        val showQrButton = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = if (event.qrCode.isNotEmpty()) "Show QR Code" else "Generate QR"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            setOnClickListener {
                showQrCodeDialog(event)
            }
        }

        contentLayout.addView(nameView)
        contentLayout.addView(dateTimeView)
        contentLayout.addView(locationView)
        contentLayout.addView(qrView)
        contentLayout.addView(statusView)
        contentLayout.addView(showQrButton)
        cardView.addView(contentLayout)
        eventsContainer.addView(cardView)
    }

    private fun showQrCodeDialog(event: Event) {
        val eventName = event.name.ifEmpty { event.title.ifEmpty { "Event" } }
        val dialog = QrGeneratorDialog.newInstance(eventName, event.id)
        dialog.show(parentFragmentManager, "QrGeneratorDialog")
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

    override fun onDestroyView() {
        super.onDestroyView()
        eventsListener?.remove()
    }
}
