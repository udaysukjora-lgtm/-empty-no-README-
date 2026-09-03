package com.example.messagingapp.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Thin wrapper over the backend's `/ws` endpoint (message:send / message:read / typing:*). */
class ChatSocket(private val token: String) {
    private var webSocket: WebSocket? = null
    private val gson = Gson()

    fun connect(): Flow<JsonObject> = callbackFlow {
        val request = Request.Builder().url("${NetworkModule.WS_URL}?token=$token").build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { gson.fromJson(text, JsonObject::class.java) }
                    .onSuccess { trySend(it) }
            }
        }
        webSocket = NetworkModule.okHttpClient.newWebSocket(request, listener)
        awaitClose {
            webSocket?.close(1000, null)
            webSocket = null
        }
    }

    fun sendMessage(conversationId: Long, content: String) {
        val payload = mapOf(
            "event" to "message:send",
            "conversation_id" to conversationId,
            "content" to content,
            "message_type" to "text"
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun sendTyping(conversationId: Long, started: Boolean) {
        val payload = mapOf(
            "event" to if (started) "typing:start" else "typing:stop",
            "conversation_id" to conversationId
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun markRead(messageId: Long) {
        val payload = mapOf("event" to "message:read", "message_id" to messageId)
        webSocket?.send(gson.toJson(payload))
    }
}
