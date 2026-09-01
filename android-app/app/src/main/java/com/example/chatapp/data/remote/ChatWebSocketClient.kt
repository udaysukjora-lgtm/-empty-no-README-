package com.example.chatapp.data.remote

import com.example.chatapp.config.Config
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response as OkResponse

sealed class WsConnectionState {
    data object Connecting : WsConnectionState()
    data object Connected : WsConnectionState()
    data class Disconnected(val reason: String?) : WsConnectionState()
}

sealed class WsEvent {
    data class Message(val json: JsonObject) : WsEvent()
    data class State(val state: WsConnectionState) : WsEvent()
}

/**
 * Thin wrapper over the backend's single `/ws?token=...` socket. Emits raw
 * JSON events (`message:new`, `message:status`, `typing:start`, ...) plus
 * connection-state changes as a cold Flow; call [send] to push client
 * events like `message:send`.
 */
class ChatWebSocketClient(private val client: OkHttpClient) {

    private var webSocket: WebSocket? = null

    fun connect(token: String): Flow<WsEvent> = callbackFlow {
        trySend(WsEvent.State(WsConnectionState.Connecting))

        val request = Request.Builder()
            .url("${Config.WS_URL}?token=$token")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: OkResponse) {
                trySend(WsEvent.State(WsConnectionState.Connected))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                if (json != null) trySend(WsEvent.Message(json))
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                trySend(WsEvent.State(WsConnectionState.Disconnected(reason)))
                close()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: OkResponse?) {
                trySend(WsEvent.State(WsConnectionState.Disconnected(t.message)))
                close(t)
            }
        }

        webSocket = client.newWebSocket(request, listener)

        awaitClose {
            webSocket?.close(1000, "client closed")
            webSocket = null
        }
    }

    fun send(json: JsonObject): Boolean = webSocket?.send(json.toString()) ?: false

    fun disconnect() {
        webSocket?.close(1000, "client closed")
        webSocket = null
    }
}
