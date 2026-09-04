package com.example.chatapp.data.repository

import com.example.chatapp.data.local.SessionManager
import com.example.chatapp.data.model.SendOtpRequest
import com.example.chatapp.data.model.SendOtpResponse
import com.example.chatapp.data.model.VerifyOtpRequest
import com.example.chatapp.data.remote.ApiService
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager,
) {
    val tokenFlow: Flow<String?> = sessionManager.tokenFlow
    val userIdFlow: Flow<Long?> = sessionManager.userIdFlow

    suspend fun sendOtp(phoneNumber: String): SendOtpResponse =
        api.sendOtp(SendOtpRequest(phoneNumber))

    suspend fun verifyOtp(phoneNumber: String, otp: String): String {
        val response = api.verifyOtp(VerifyOtpRequest(phoneNumber, otp))
        sessionManager.saveSession(response.accessToken, phoneNumber)
        return response.accessToken
    }

    suspend fun currentToken(): String? = sessionManager.currentToken()

    suspend fun logout() = sessionManager.clear()
}
