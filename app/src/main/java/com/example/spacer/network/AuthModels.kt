package com.example.spacer.network

data class SignupRequest(
    val username: String,
    val email: String,
    val password: String,
    val first_name: String?,
    val last_name: String?
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val id: Int,
    val username: String,
    val email: String,
    val first_name: String?,
    val last_name: String?
)

data class SignupResponse(
    val message: String,
    val user: UserDto
)

data class LoginResponse(
    val message: String,
    val token: String,
    val user: UserDto
)
