package com.example.messagingapp.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.messagingapp.data.local.TokenManager
import com.example.messagingapp.data.model.SendOtpRequest
import com.example.messagingapp.data.model.VerifyOtpRequest
import com.example.messagingapp.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LoginStep { ENTER_PHONE, ENTER_OTP }

data class LoginUiState(
    val phone: String = "",
    val otp: String = "",
    val step: LoginStep = LoginStep.ENTER_PHONE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val api = NetworkModule.apiService

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onPhoneChange(value: String) {
        _uiState.value = _uiState.value.copy(phone = value, error = null)
    }

    fun onOtpChange(value: String) {
        _uiState.value = _uiState.value.copy(otp = value, error = null)
    }

    fun sendOtp() {
        val phone = _uiState.value.phone.trim()
        if (phone.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Pehle phone number daaliye")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                api.sendOtp(SendOtpRequest(phone))
                _uiState.value = _uiState.value.copy(isLoading = false, step = LoginStep.ENTER_OTP)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "OTP bhejne mein dikkat hui: ${e.message}"
                )
            }
        }
    }

    fun verifyOtp() {
        val phone = _uiState.value.phone.trim()
        val otp = _uiState.value.otp.trim()
        if (otp.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "OTP daaliye")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.verifyOtp(VerifyOtpRequest(phone, otp))
                tokenManager.saveToken(response.access_token)
                _uiState.value = _uiState.value.copy(isLoading = false, loggedIn = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Verify fail hua: ${e.message}"
                )
            }
        }
    }
}
