package com.example.spacer.network

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from

class AuthRepository {

    private val supabase = SupabaseManager.client

    suspend fun signup(request: SignupRequest): Result<String> {
        return try {
            supabase.auth.signUpWith(Email) {
                email = request.email
                password = request.password
            }

            Result.success("Signup successful")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Signup auth failed: ${e::class.simpleName} - ${e.message}"))
        }
    }

    suspend fun login(request: LoginRequest): Result<String> {
        return try {
            supabase.auth.signInWith(Email) {
                email = request.email
                password = request.password
            }

            Result.success("Login successful")

        } catch (e: Exception) {

            val message = when {
                e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                    "Incorrect email or password"

                e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                    "Please verify your email before logging in"

                e.message?.contains("User not confirmed", ignoreCase = true) == true ->
                    "Please verify your email before logging in"

                else -> "Something went wrong. Please try again."
            }

            Result.failure(Exception(message))
        }
    }
}