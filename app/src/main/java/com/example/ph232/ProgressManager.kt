package com.example.ph232
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
class ProgressManager {
    private var dialog: Dialog? = null
    constructor(activity: Activity) {
        dialog = Dialog(activity)
    }
    constructor(context: Context) {
        dialog = Dialog(context)
    }
    fun show(message: String = "Loading...") {
        try {
            if (dialog == null) return
            val context = dialog!!.context
            if (context is Activity && (context.isFinishing || context.isDestroyed)) return
            if (dialog!!.isShowing) return
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
            val tvMessage = view.findViewById<TextView>(R.id.tvLoadingMessage)
            tvMessage?.text = message
            dialog!!.setContentView(view)
            dialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog!!.setCancelable(false)
            val ivMascot = view.findViewById<ImageView>(R.id.ivMascotLoader)
            val rotationAnim = AnimationUtils.loadAnimation(context, R.anim.mascot_rotation)
            ivMascot?.startAnimation(rotationAnim)
            dialog!!.show()
        } catch (_: Exception) { }
    }
    fun dismiss() {
        try {
            val ivMascot = dialog?.findViewById<ImageView>(R.id.ivMascotLoader)
            ivMascot?.clearAnimation()
            if (dialog?.isShowing == true) {
                dialog?.dismiss()
            }
        } catch (_: Exception) { }
    }
}
