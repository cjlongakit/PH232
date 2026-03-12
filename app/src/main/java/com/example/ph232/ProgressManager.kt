package com.example.ph232
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.animation.AnimationUtils
import android.widget.ImageView
class ProgressManager(private val activity: Activity) {
    private var dialog: Dialog? = null
    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (dialog != null && dialog!!.isShowing) return
        dialog = Dialog(activity)
        dialog?.setContentView(R.layout.dialog_loading)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.setCancelable(false)
        val ivMascot = dialog?.findViewById<ImageView>(R.id.ivMascotLoader)
        val rotationAnim = AnimationUtils.loadAnimation(activity, R.anim.mascot_rotation)
        ivMascot?.startAnimation(rotationAnim)
        dialog?.show()
    }
    fun dismiss() {
        val ivMascot = dialog?.findViewById<ImageView>(R.id.ivMascotLoader)
        ivMascot?.clearAnimation()
        dialog?.dismiss()
        dialog = null
    }
}
