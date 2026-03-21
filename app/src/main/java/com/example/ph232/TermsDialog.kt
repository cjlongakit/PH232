package com.example.ph232

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TermsDialog : DialogFragment() {

    companion object {
        fun newInstance(): TermsDialog = TermsDialog()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_terms, null)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseTerms)

        btnClose.setOnClickListener { dismiss() }

        val dlg = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dlg
    }
}

