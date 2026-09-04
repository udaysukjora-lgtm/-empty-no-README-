package com.example.chatapp.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.ConversationSummary
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isStartingConversation: Boolean = false,
)

class ConversationsViewModel(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { chatRepository.listConversations() }
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, conversations = list)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Conversations load nahi ho payi",
                    )
                }
        }
    }

    fun startConversation(participantPhone: String, onCreated: (Long) -> Unit) {
        if (participantPhone.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStartingConversation = true, errorMessage = null)
            runCatching { chatRepository.startConversation(participantPhone) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(isStartingConversation = false)
                    onCreated(response.conversationId)
                    refresh()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isStartingConversation = false,
                        errorMessage = e.message ?: "Conversation start nahi ho paayi",
                    )
                }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    companion object {
        fun factory(chatRepository: ChatRepository, authRepository: AuthRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ConversationsViewModel(chatRepository, authRepository) as T
            }
    }
}
