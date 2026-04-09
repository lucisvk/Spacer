package com.example.spacer.network

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {

    private val supabase = SupabaseManager.client

    private fun configErrorOrNull(): Exception? {
        if (com.example.spacer.BuildConfig.SUPABASE_URL.isBlank() ||
            com.example.spacer.BuildConfig.SUPABASE_KEY.isBlank()
        ) {
            return Exception(
                "Supabase config missing. Set SUPABASE_URL and SUPABASE_KEY in local.properties, gradle.properties, or environment variables."
            )
        }
        return null
    }

    suspend fun signup(request: SignupRequest): Result<String> {
        configErrorOrNull()?.let { return Result.failure(it) }
        return try {
            if (request.email.isBlank() || request.password.isBlank()) {
                return Result.failure(Exception("Email and password are required"))
            }
            if (request.username.isBlank()) {
                return Result.failure(Exception("Username is required"))
            }

            if (request.password.length < 6) {
                return Result.failure(Exception("Password must be at least 6 characters"))
            }

            supabase.auth.signUpWith(Email) {
                email = request.email
                password = request.password
                data = buildJsonObject {
                    put("username", request.username)
                    // Display name for profile = chosen username (matches DB `name` + trigger).
                    put("name", request.username.trim())
                    put("full_name", request.username.trim())
                    request.phoneNumber?.takeIf { it.isNotBlank() }?.let { put("phone_number", it) }
                    request.dateOfBirth?.takeIf { it.isNotBlank() }?.let { put("date_of_birth", it) }
                    put("allow_updates", JsonPrimitive(request.allowUpdates))
                }
            }

            // If a session is available immediately after signup, ensure username/full_name are
            // persisted on profiles so Profile screens can fetch them reliably.
            val currentUser = supabase.auth.currentUserOrNull()
            if (currentUser != null) {
                supabase.from("profiles").upsert(
                    mapOf(
                        "id" to currentUser.id,
                        "email" to request.email,
                        "username" to request.username.trim(),
                        "name" to request.username.trim(),
                        "about_me" to ""
                    )
                )
            }

            Result.success("Account created. Check your email to confirm your account if required.")
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("already registered", ignoreCase = true) == true ->
                    "This email is already registered"
                e.message?.contains("password", ignoreCase = true) == true ->
                    "Password does not meet Supabase requirements"
                e.message?.contains("signup is disabled", ignoreCase = true) == true ->
                    "Signup is disabled in Supabase auth settings"
                else -> "Signup failed: ${e.message ?: "Unknown error"}"
            }
            Result.failure(Exception(message))
        }
    }

    /**
     * Starts the OAuth flow (Custom Tab → provider → deeplink).
     * Configure Google / GitHub / Discord in the Supabase dashboard and add redirect URL `spacer://auth`.
     */
    suspend fun signInWithOAuth(provider: OAuthProvider): Result<Unit> {
        configErrorOrNull()?.let { return Result.failure(it) }
        return try {
            supabase.auth.signInWith(provider)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(request: LoginRequest): Result<String> {
        configErrorOrNull()?.let { return Result.failure(it) }
        return try {
            if (request.email.isBlank() || request.password.isBlank()) {
                return Result.failure(Exception("Email and password are required"))
            }

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

    suspend fun resolveCurrentDisplayName(): String? {
        return try {
            val user = supabase.auth.currentUserOrNull() ?: return null

            val metadataName = user.userMetadata
                ?.get("name")
                ?.toString()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }

            val fullName = user.userMetadata
                ?.get("full_name")
                ?.toString()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }

            val emailPrefix = user.email
                ?.substringBefore("@")
                ?.takeIf { it.isNotBlank() }

            metadataName ?: fullName ?: emailPrefix
        } catch (_: Exception) {
            null
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}