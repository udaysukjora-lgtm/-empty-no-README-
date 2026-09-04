package com.example.chatapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class LoginStep { ENTER_PHONE, ENTER_OTP }

data class LoginUiState(
    val step: LoginStep = LoginStep.ENTER_PHONE,
    val phoneNumber: String = "",
    val otp: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val devHint: String? = null,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onPhoneChanged(value: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = value, errorMessage = null)
    }

    fun onOtpChanged(value: String) {
        _uiState.value = _uiState.value.copy(otp = value, errorMessage = null)
    }

    fun sendOtp() {
        val phone = _uiState.value.phoneNumber.trim()
        if (phone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Phone number daaliye")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { authRepository.sendOtp(phone) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        step = LoginStep.ENTER_OTP,
                        devHint = response.devNote,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "OTP bhejne mein dikkat aayi",
                    )
                }
        }
    }

    fun verifyOtp(onSuccess: () -> Unit) {
        val phone = _uiState.value.phoneNumber.trim()
        val otp = _uiState.value.otp.trim()
        if (otp.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "OTP daaliye")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { authRepository.verifyOtp(phone, otp) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "OTP galat hai ya verify nahi hua",
                    )
                }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LoginViewModel(authRepository) as T
        }
    }
}
