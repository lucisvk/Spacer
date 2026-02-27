package com.example.spacer_splash

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CreateAccountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)

        val btnCreate = findViewById<Button>(R.id.btnCreateAccount)
        val tvBack = findViewById<TextView>(R.id.tvBackToLogin)

        tvBack.setOnClickListener {
            finish() // returns to LoginActivity
        }

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirm = etConfirm.text.toString()

            when {
                name.isEmpty() -> toast("Name is required")
                email.isEmpty() -> toast("Email is required")
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> toast("Enter a valid email")
                password.length < 6 -> toast("Password must be at least 6 characters")
                password != confirm -> toast("Passwords do not match")
                else -> {
                    fakeCreateAccount(name, email, password) // dummy values for now for yall to clone
                }
            }
        }
    }

    private fun fakeCreateAccount(name: String, email: String, password: String) {
        // For now: assume success, send user back to login with email pre-filled
        toast("Account created! Please log in.")

        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra("prefill_email", email)
        startActivity(intent)
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}