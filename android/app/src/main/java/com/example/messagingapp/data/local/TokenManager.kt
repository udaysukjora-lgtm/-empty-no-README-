package com.example.messagingapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth")

class TokenManager(private val context: Context) {
    private val tokenKey = stringPreferencesKey("access_token")

    val tokenFlow: Flow<String?> = context.authDataStore.data.map { it[tokenKey] }

    suspend fun saveToken(token: String) {
        context.authDataStore.edit { it[tokenKey] = token }
    }

    suspend fun clearToken() {
        context.authDataStore.edit { it.remove(tokenKey) }
    }
}
