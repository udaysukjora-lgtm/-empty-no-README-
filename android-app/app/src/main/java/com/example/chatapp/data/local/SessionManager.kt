package com.example.chatapp.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chat_session")

/** Persists the JWT + logged-in user's id across app restarts. */
class SessionManager(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("access_token")
        val USER_ID = longPreferencesKey("user_id")
        val PHONE = stringPreferencesKey("phone_number")
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }
    val userIdFlow: Flow<Long?> = context.dataStore.data.map { it[Keys.USER_ID] }

    suspend fun currentToken(): String? = tokenFlow.first()

    suspend fun saveSession(token: String, phoneNumber: String) {
        val userId = decodeUserIdFromJwt(token)
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.PHONE] = phoneNumber
            if (userId != null) prefs[Keys.USER_ID] = userId
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    /** The backend puts the user id in the JWT's "sub" claim. */
    private fun decodeUserIdFromJwt(token: String): Long? = try {
        val payload = token.split(".")[1]
        val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val decoded = String(Base64.decode(payload, flags))
        Regex("\"sub\"\\s*:\\s*\"?(\\d+)\"?").find(decoded)?.groupValues?.get(1)?.toLong()
    } catch (e: Exception) {
        null
    }
}
