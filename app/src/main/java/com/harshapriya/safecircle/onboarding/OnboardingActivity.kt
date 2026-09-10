package com.harshapriya.safecircle.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.MainActivity
import com.harshapriya.safecircle.R

class OnboardingActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("safecircle_onboarding", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.getBoolean("completed", false)) {
            openApp()
            return
        }

        setContentView(R.layout.activity_onboarding)
        findViewById<MaterialButton>(R.id.onboardingContinueButton).setOnClickListener {
            prefs.edit().putBoolean("completed", true).apply()
            openApp()
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
