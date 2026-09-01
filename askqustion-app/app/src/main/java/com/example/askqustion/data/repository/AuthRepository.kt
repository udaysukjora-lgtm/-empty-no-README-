package com.example.askqustion.data.repository

import com.example.askqustion.data.local.CredentialStore
import com.example.askqustion.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

class AuthRepository(private val credentialStore: CredentialStore) {

    val isLoggedInFlow: Flow<Boolean> = credentialStore.credentialsFlow.map { it != null }
    val displayNameFlow: Flow<String?> = credentialStore.credentialsFlow.map { it?.displayName }

    /** Verifies the Application Password against /users/me, then persists it if valid. */
    suspend fun login(username: String, appPassword: String): Result<String> = runCatching {
        val api = NetworkModule.apiServiceWithCredentials(username, appPassword)
        val me = try {
            api.getMe()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                throw IllegalArgumentException("Username ya Application Password galat hai")
            }
            throw e
        }
        credentialStore.save(username, appPassword, me.name)
        me.name
    }

    suspend fun logout() = credentialStore.clear()
}
