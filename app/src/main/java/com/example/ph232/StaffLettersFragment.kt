package com.example.ph232

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

class StaffLettersFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var rvLetters: RecyclerView
    private lateinit var fabAddLetter: ExtendedFloatingActionButton

    private lateinit var repository: FirebaseRepository
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: StaffLetterAdapter
    private var allLetters = mutableListOf<StaffLetter>()
    private var staffUsername: String = ""
    private var lettersListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_staff_letters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = FirebaseRepository.getInstance()

        val prefs = requireActivity().getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        staffUsername = prefs.getString("USER_PH", "unknown") ?: "unknown"

        tabLayout = view.findViewById(R.id.tabLayoutLetters)
        rvLetters = view.findViewById(R.id.rvStaffLetters)
        fabAddLetter = view.findViewById(R.id.fabAddLetter)

        val statuses = listOf("ALL", "PENDING", "ON HAND", "TURN IN", "LATE")
        for (status in statuses) {
            tabLayout.addTab(tabLayout.newTab().setText(status))
        }

        rvLetters.layoutManager = LinearLayoutManager(requireContext())
        adapter = StaffLetterAdapter(emptyList()) { letter ->
            showUpdateStatusDialog(letter)
        }
        rvLetters.adapter = adapter

        setupLettersListener()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { filterList() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        fabAddLetter.setOnClickListener {
            showAddLetterDialog()
        }
    }

    private fun setupLettersListener() {
        lettersListener = repository.listenToStaffLetters(staffUsername) { letters ->
            allLetters.clear()
            allLetters.addAll(letters)
            filterList()
        }
    }

    private fun filterList() {
        val selectedTab = tabLayout.getTabAt(tabLayout.selectedTabPosition)?.text.toString()
        if (selectedTab == "ALL") {
            adapter.updateData(allLetters)
        } else {
            val filtered = allLetters.filter { it.status.equals(selectedTab, ignoreCase = true) }
            adapter.updateData(filtered)
        }
    }

    private fun showAddLetterDialog() {
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val etPhNumber = EditText(requireContext())
        etPhNumber.hint = "PH323 ID (e.g. PH323-000)"
        layout.addView(etPhNumber)

        val btnVerify = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btnVerify.text = "Verify Student"
        layout.addView(btnVerify)

        val tvStudentName = TextView(requireContext())
        tvStudentName.text = "Name will appear here..."
        tvStudentName.setPadding(0, 10, 0, 20)
        layout.addView(tvStudentName)

        val spinnerType = Spinner(requireContext())
        val types = arrayOf("Gift", "Reply", "General", "Final Letter", "First Letter")
        spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)
        layout.addView(spinnerType)

        val etDeadline = EditText(requireContext())
        etDeadline.hint = "Select Deadline Date"
        etDeadline.isFocusable = false
        etDeadline.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                etDeadline.setText(String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, day))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        layout.addView(etDeadline)

        var verifiedName = ""
        btnVerify.setOnClickListener {
            val searchId = etPhNumber.text.toString().trim()
            if (searchId.isNotEmpty()) {
                db.collection("users").document(searchId).get().addOnSuccessListener { doc ->
                    if (doc.exists() && doc.getString("role") == "beneficiary") {
                        val fName = doc.getString("FirstName") ?: doc.getString("firstName") ?: ""
                        val lName = doc.getString("LastName") ?: doc.getString("lastName") ?: ""
                        verifiedName = "$fName $lName".trim()
                        tvStudentName.text = "✓ Found: $verifiedName"
                        tvStudentName.setTextColor(Color.parseColor("#10B981"))
                    } else {
                        tvStudentName.text = "✗ Student not found"
                        tvStudentName.setTextColor(Color.RED)
                        verifiedName = ""
                    }
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create New Letter")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val type = spinnerType.selectedItem.toString()
                val deadline = etDeadline.text.toString()
                val phId = etPhNumber.text.toString().trim()

                if (verifiedName.isNotEmpty() && deadline.isNotEmpty()) {
                    val letter = StaffLetter(
                        phNumber = phId,
                        studentName = verifiedName,
                        type = type,
                        deadline = deadline,
                        status = "PENDING",
                        caseworker = staffUsername
                    )
                    repository.addStaffLetter(letter,
                        onSuccess = { Toast.makeText(requireContext(), "Letter Added!", Toast.LENGTH_SHORT).show() },
                        onFailure = { Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                    )
                } else {
                    Toast.makeText(requireContext(), "Please verify student and set a deadline.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpdateStatusDialog(letter: StaffLetter) {
        val statuses = arrayOf("PENDING", "ON HAND", "TURN IN", "LATE")
        var selectedIndex = statuses.indexOf(letter.status.uppercase())
        if (selectedIndex == -1) selectedIndex = 0

        AlertDialog.Builder(requireContext())
            .setTitle("Update Letter Status")
            .setSingleChoiceItems(statuses, selectedIndex) { dialog, which ->
                val newStatus = statuses[which]
                repository.updateStaffLetterStatus(letter.id, newStatus,
                    onSuccess = { Toast.makeText(requireContext(), "Status Updated", Toast.LENGTH_SHORT).show() },
                    onFailure = { Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                )
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lettersListener?.let { repository.removeListener(it) }
    }
}
