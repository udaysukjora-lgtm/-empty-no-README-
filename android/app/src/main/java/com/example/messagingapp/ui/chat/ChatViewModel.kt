package com.example.messagingapp.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.messagingapp.data.local.TokenManager
import com.example.messagingapp.data.model.MessageOut
import com.example.messagingapp.data.remote.ChatSocket
import com.example.messagingapp.data.remote.NetworkModule
import com.example.messagingapp.util.decodeUserIdFromJwt
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<MessageOut> = emptyList(),
    val draft: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val myUserId: Long? = null,
    val peerTyping: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val api = NetworkModule.apiService

    private var conversationId: Long = -1
    private var chatSocket: ChatSocket? = null
    private var token: String? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun start(conversationId: Long) {
        if (this.conversationId == conversationId) return
        this.conversationId = conversationId
        viewModelScope.launch {
            val storedToken = tokenManager.tokenFlow.first() ?: return@launch
            token = storedToken
            _uiState.value = _uiState.value.copy(myUserId = decodeUserIdFromJwt(storedToken))
            loadHistory(storedToken)
            connectSocket(storedToken)
        }
    }

    private suspend fun loadHistory(token: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val history = api.getMessages("Bearer $token", conversationId)
            _uiState.value = _uiState.value.copy(isLoading = false, messages = history.reversed())
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "History load fail hui: ${e.message}")
        }
    }

    private fun connectSocket(token: String) {
        val socket = ChatSocket(token)
        chatSocket = socket
        viewModelScope.launch {
            socket.connect().collect { event -> handleEvent(event) }
        }
    }

    private fun handleEvent(event: JsonObject) {
        when (event.get("event")?.asString) {
            "message:new" -> {
                val eventConversationId = event.get("conversation_id")?.asLong
                if (eventConversationId != conversationId) return
                val incoming = MessageOut(
                    id = event.get("id")?.asLong ?: 0L,
                    conversation_id = conversationId,
                    sender_id = event.get("sender_id")?.asLong ?: 0L,
                    content = event.get("content")?.asString,
                    message_type = event.get("message_type")?.asString ?: "text",
                    media_url = event.get("media_url")?.asString,
                    status = "sent",
                    created_at = ""
                )
                _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + incoming)
            }
            "typing:start" -> _uiState.value = _uiState.value.copy(peerTyping = true)
            "typing:stop" -> _uiState.value = _uiState.value.copy(peerTyping = false)
        }
    }

    fun onDraftChange(value: String) {
        _uiState.value = _uiState.value.copy(draft = value)
    }

    fun sendMessage() {
        val text = _uiState.value.draft.trim()
        if (text.isBlank()) return
        chatSocket?.sendMessage(conversationId, text)

        val myId = _uiState.value.myUserId ?: -1L
        val optimistic = MessageOut(
            id = 0L,
            conversation_id = conversationId,
            sender_id = myId,
            content = text,
            message_type = "text",
            media_url = null,
            status = "sent",
            created_at = ""
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + optimistic,
            draft = ""
        )
    }
}
