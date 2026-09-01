package com.example.askqustion

import android.app.Application
import com.example.askqustion.data.local.CredentialStore
import com.example.askqustion.data.remote.NetworkModule
import com.example.askqustion.data.repository.AuthRepository
import com.example.askqustion.data.repository.QaRepository

class AskQustionApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val credentialStore = CredentialStore(app)
    private val okHttpClient = NetworkModule.okHttpClient(credentialStore)
    private val apiService = NetworkModule.apiService(okHttpClient)

    val authRepository = AuthRepository(credentialStore)
    val qaRepository = QaRepository(apiService)
}
