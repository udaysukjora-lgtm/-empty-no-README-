package com.example.chatapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.MessageOut
import com.example.chatapp.data.remote.WsConnectionState
import com.example.chatapp.data.remote.WsEvent
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.data.repository.ChatRepository
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<MessageOut> = emptyList(),
    val draft: String = "",
    val currentUserId: Long? = null,
    val isLoadingHistory: Boolean = false,
    val connectionState: WsConnectionState = WsConnectionState.Connecting,
    val errorMessage: String? = null,
    val peerTyping: Boolean = false,
)

class ChatViewModel(
    private val conversationId: Long,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(currentUserId = authRepository.userIdFlow.first())
        }
        loadHistory()
        connectSocket()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistory = true)
            runCatching { chatRepository.getMessages(conversationId) }
                .onSuccess { newest50 ->
                    // Backend returns newest-first; show oldest-first in the list.
                    _uiState.value = _uiState.value.copy(
                        isLoadingHistory = false,
                        messages = newest50.reversed(),
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingHistory = false,
                        errorMessage = e.message ?: "Messages load nahi ho paaye",
                    )
                }
        }
    }

    private fun connectSocket() {
        viewModelScope.launch {
            chatRepository.observeSocket().collect { event ->
                when (event) {
                    is WsEvent.State -> {
                        _uiState.value = _uiState.value.copy(connectionState = event.state)
                    }
                    is WsEvent.Message -> handleIncoming(event.json)
                }
            }
        }
    }

    private fun handleIncoming(json: JsonObject) {
        val eventName = json.get("event")?.asString ?: return
        when (eventName) {
            "message:new" -> {
                val convoId = json.get("conversation_id")?.asLong ?: return
                if (convoId != conversationId) return
                val incoming = MessageOut(
                    id = json.get("id").asLong,
                    conversationId = convoId,
                    senderId = json.get("sender_id").asLong,
                    content = json.get("content")?.takeIf { !it.isJsonNull }?.asString,
                    messageType = json.get("message_type")?.asString ?: "text",
                    mediaUrl = json.get("media_url")?.takeIf { !it.isJsonNull }?.asString,
                    status = "sent",
                    createdAt = "",
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + incoming,
                    peerTyping = false,
                )
                val myId = _uiState.value.currentUserId
                if (myId != null && incoming.senderId != myId) {
                    chatRepository.markRead(incoming.id)
                }
            }
            "typing:start" -> {
                if (json.get("conversation_id")?.asLong == conversationId) {
                    _uiState.value = _uiState.value.copy(peerTyping = true)
                }
            }
            "typing:stop" -> {
                if (json.get("conversation_id")?.asLong == conversationId) {
                    _uiState.value = _uiState.value.copy(peerTyping = false)
                }
            }
            "message:status" -> {
                val messageId = json.get("message_id")?.asLong ?: return
                val newStatus = json.get("status")?.asString ?: return
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map {
                        if (it.id == messageId) it.copy(status = newStatus) else it
                    },
                )
            }
        }
    }

    fun onDraftChanged(value: String) {
        _uiState.value = _uiState.value.copy(draft = value)
        chatRepository.sendTyping(conversationId, isTyping = value.isNotBlank())
    }

    fun sendMessage() {
        val text = _uiState.value.draft.trim()
        if (text.isBlank()) return
        chatRepository.sendMessage(conversationId, text)
        _uiState.value = _uiState.value.copy(draft = "")
        chatRepository.sendTyping(conversationId, isTyping = false)
    }

    override fun onCleared() {
        super.onCleared()
        chatRepository.closeSocket()
    }

    companion object {
        fun factory(conversationId: Long, chatRepository: ChatRepository, authRepository: AuthRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(conversationId, chatRepository, authRepository) as T
            }
    }
}
