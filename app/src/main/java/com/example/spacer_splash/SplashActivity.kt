package com.example.spacer_splash

import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val root = findViewById<View>(R.id.splashRoot)
        val glow = findViewById<View>(R.id.glow)
        val logo = findViewById<ImageView>(R.id.logo)

        (root.background as? AnimationDrawable)?.apply {
            setEnterFadeDuration(600)
            setExitFadeDuration(600)
            start()
        }

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(950)
            .withStartAction { logo.rotation = -18f }
            .withEndAction {
                // tiny bounce
                logo.animate()
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(160)
                    .withEndAction {
                        logo.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(140)
                            .start()
                    }
                    .start()
            }
            .start()

        // Glow pulse
        glow.animate()
            .alpha(1f)
            .setDuration(650)
            .withEndAction {
                glow.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .alpha(0.65f)
                    .setDuration(700)
                    .withEndAction {
                        glow.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(700)
                            .start()
                    }
                    .start()
            }
            .start()


        Handler(Looper.getMainLooper()).postDelayed({
            root.animate().alpha(0f).setDuration(220).withEndAction {
                startActivity(Intent(this, LoginActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }.start()
        }, 1900)
    }
}