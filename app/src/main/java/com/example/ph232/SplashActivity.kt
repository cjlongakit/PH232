package com.example.ph232

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dark mode preference
        val darkPrefs = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val isDarkMode = darkPrefs.getBoolean("DARK_MODE", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        // Make it fullscreen / immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_splash)

        // Find views
        val cvLogo = findViewById<CardView>(R.id.cvSplashLogo)
        val tvTitle = findViewById<TextView>(R.id.tvSplashTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvSplashSubtitle)
        val llLoading = findViewById<LinearLayout>(R.id.llSplashLoading)

        // Initially invisible
        cvLogo.alpha = 0f
        cvLogo.scaleX = 0f
        cvLogo.scaleY = 0f
        tvTitle.alpha = 0f
        tvTitle.translationY = 60f
        tvSubtitle.alpha = 0f
        tvSubtitle.translationY = 40f
        llLoading.alpha = 0f

        // Animate logo - pop in with bounce
        val logoAnimSet = AnimatorSet()
        logoAnimSet.playTogether(
            ObjectAnimator.ofFloat(cvLogo, "alpha", 0f, 1f).setDuration(600),
            ObjectAnimator.ofFloat(cvLogo, "scaleX", 0f, 1f).apply {
                duration = 700
                interpolator = OvershootInterpolator(1.5f)
            },
            ObjectAnimator.ofFloat(cvLogo, "scaleY", 0f, 1f).apply {
                duration = 700
                interpolator = OvershootInterpolator(1.5f)
            }
        )
        logoAnimSet.start()

        // Animate title - slide up and fade in
        Handler(Looper.getMainLooper()).postDelayed({
            val titleAnimSet = AnimatorSet()
            titleAnimSet.playTogether(
                ObjectAnimator.ofFloat(tvTitle, "alpha", 0f, 1f).setDuration(500),
                ObjectAnimator.ofFloat(tvTitle, "translationY", 60f, 0f).setDuration(500)
            )
            titleAnimSet.start()
        }, 350)

        // Animate subtitle
        Handler(Looper.getMainLooper()).postDelayed({
            val subtitleAnimSet = AnimatorSet()
            subtitleAnimSet.playTogether(
                ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f).setDuration(500),
                ObjectAnimator.ofFloat(tvSubtitle, "translationY", 40f, 0f).setDuration(500)
            )
            subtitleAnimSet.start()
        }, 550)

        // Animate loading indicator
        Handler(Looper.getMainLooper()).postDelayed({
            ObjectAnimator.ofFloat(llLoading, "alpha", 0f, 1f).setDuration(600).start()
        }, 800)

        // Add a subtle continuous pulse animation to the logo
        Handler(Looper.getMainLooper()).postDelayed({
            val pulseX = ObjectAnimator.ofFloat(cvLogo, "scaleX", 1f, 1.05f, 1f).apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
            }
            val pulseY = ObjectAnimator.ofFloat(cvLogo, "scaleY", 1f, 1.05f, 1f).apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
            }
            AnimatorSet().apply {
                playTogether(pulseX, pulseY)
                start()
            }
        }, 1200)

        // Navigate after delay
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, 2800)
    }

    private fun navigateToNextScreen() {
        val sharedPreferences = getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        val activeUser = sharedPreferences.getString("USER_PH", null)
        val activeRole = sharedPreferences.getString("USER_ROLE", null)

        val intent = if (activeUser != null) {
            when (activeRole) {
                "admin" -> Intent(this, AdminDashboardActivity::class.java)
                "staff" -> Intent(this, StaffDashboardActivity::class.java)
                else -> Intent(this, DashboardActivity::class.java)
            }
        } else {
            Intent(this, MainActivity::class.java)
        }

        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

