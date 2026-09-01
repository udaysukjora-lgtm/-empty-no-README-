package com.example.chatapp.data.repository

import com.example.chatapp.data.local.SessionManager
import com.example.chatapp.data.model.ConversationCreateRequest
import com.example.chatapp.data.model.ConversationCreateResponse
import com.example.chatapp.data.model.ConversationSummary
import com.example.chatapp.data.model.MessageOut
import com.example.chatapp.data.remote.ApiService
import com.example.chatapp.data.remote.ChatWebSocketClient
import com.example.chatapp.data.remote.WsEvent
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class ChatRepository(
    private val api: ApiService,
    private val wsClient: ChatWebSocketClient,
    private val sessionManager: SessionManager,
) {
    suspend fun listConversations(): List<ConversationSummary> = api.listConversations()

    suspend fun startConversation(participantPhone: String): ConversationCreateResponse =
        api.createConversation(ConversationCreateRequest(participantPhone))

    suspend fun getMessages(conversationId: Long, before: Long? = null): List<MessageOut> =
        api.getMessages(conversationId, before)

    /** Opens the live socket for the currently logged-in user; emits until cancelled. */
    fun observeSocket(): Flow<WsEvent> = flow {
        val token = sessionManager.currentToken() ?: return@flow
        emitAll(wsClient.connect(token))
    }

    fun sendMessage(conversationId: Long, content: String) {
        val payload = JsonObject().apply {
            addProperty("event", "message:send")
            addProperty("conversation_id", conversationId)
            addProperty("content", content)
            addProperty("message_type", "text")
        }
        wsClient.send(payload)
    }

    fun markRead(messageId: Long) {
        val payload = JsonObject().apply {
            addProperty("event", "message:read")
            addProperty("message_id", messageId)
        }
        wsClient.send(payload)
    }

    fun sendTyping(conversationId: Long, isTyping: Boolean) {
        val payload = JsonObject().apply {
            addProperty("event", if (isTyping) "typing:start" else "typing:stop")
            addProperty("conversation_id", conversationId)
        }
        wsClient.send(payload)
    }

    fun closeSocket() = wsClient.disconnect()
}
