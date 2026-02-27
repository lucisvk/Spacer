package com.example.spacer_splash

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreate = findViewById<TextView>(R.id.tvCreate)
        val tvForgot = findViewById<TextView>(R.id.tvForgot)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            // Basic validation
            when {
                email.isEmpty() -> toast("Email is required")
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> toast("Enter a valid email")
                password.isEmpty() -> toast("Password is required")
                password.length < 6 -> toast("Password must be at least 6 characters")
                else -> {
                    // TODO: replace this with real auth (API / Firebase / your backend)
                    fakeLogin(email, password)
                }
            }
        }

        tvCreate.setOnClickListener {
            startActivity(Intent(this, CreateAccountActivity::class.java))
            val prefill = intent.getStringExtra("prefill_email")
            if (!prefill.isNullOrBlank()) etEmail.setText(prefill)
        }

        tvForgot.setOnClickListener {
            // ill add this feature in sometime this week
            toast("Forgot password clicked (wire next)")
        }
    }

    private fun fakeLogin(email: String, password: String) {
        // Tempo demo logic:
        // Will send out database connection to team
        val success = true

        if (success) {
            toast("Logged in!")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            toast("Invalid credentials")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}