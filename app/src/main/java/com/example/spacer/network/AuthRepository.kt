package com.example.spacer.network

import retrofit2.Response

class AuthRepository {
    private val api = RetrofitInstance.api

    suspend fun signup(request: SignupRequest): Response<SignupResponse> {
        return api.signup(request)
    }

    suspend fun login(request: LoginRequest): Response<LoginResponse> {
        return api.login(request)
    }
}
