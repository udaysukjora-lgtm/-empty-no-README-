package com.example.messagingapp.ui.conversations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.messagingapp.data.local.TokenManager
import com.example.messagingapp.data.model.ConversationCreateRequest
import com.example.messagingapp.data.model.ConversationSummary
import com.example.messagingapp.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val newChatPhone: String = "",
    val startedConversationId: Long? = null
)

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val api = NetworkModule.apiService

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private suspend fun bearerToken(): String? {
        val token = tokenManager.tokenFlow.first() ?: return null
        return "Bearer $token"
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val bearer = bearerToken()
            if (bearer == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Login required")
                return@launch
            }
            try {
                val conversations = api.listConversations(bearer)
                _uiState.value = _uiState.value.copy(isLoading = false, conversations = conversations)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Load fail hua: ${e.message}")
            }
        }
    }

    fun onNewChatPhoneChange(value: String) {
        _uiState.value = _uiState.value.copy(newChatPhone = value, error = null)
    }

    fun startConversation() {
        val phone = _uiState.value.newChatPhone.trim()
        if (phone.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Participant ka phone number daaliye")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val bearer = bearerToken()
            if (bearer == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Login required")
                return@launch
            }
            try {
                val response = api.createConversation(bearer, ConversationCreateRequest(phone))
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    newChatPhone = "",
                    startedConversationId = response.conversation_id
                )
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Conversation banane mein dikkat: ${e.message}"
                )
            }
        }
    }

    fun consumeStartedConversation() {
        _uiState.value = _uiState.value.copy(startedConversationId = null)
    }
}
