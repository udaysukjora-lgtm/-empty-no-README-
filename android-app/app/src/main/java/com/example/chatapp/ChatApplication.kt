package com.example.chatapp

import android.app.Application
import com.example.chatapp.data.local.SessionManager
import com.example.chatapp.data.remote.ChatWebSocketClient
import com.example.chatapp.data.remote.NetworkModule
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.data.repository.ChatRepository

/**
 * Simple hand-rolled DI container (no Hilt/Koin) so the whole graph is
 * visible in one place. ViewModels reach these via [AppContainer].
 */
class ChatApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val sessionManager = SessionManager(app)
    private val okHttpClient = NetworkModule.okHttpClient(sessionManager)
    private val apiService = NetworkModule.apiService(sessionManager)
    private val wsClient = ChatWebSocketClient(okHttpClient)

    val authRepository = AuthRepository(apiService, sessionManager)
    val chatRepository = ChatRepository(apiService, wsClient, sessionManager)
}
