package com.example.askqustion.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "askqustion_credentials")

/**
 * Holds the WordPress username + Application Password used for Basic Auth
 * on write requests (asking a question, posting an answer). Generate an
 * Application Password from wp-admin -> Users -> your profile -> Application
 * Passwords; it is NOT your normal WordPress login password.
 */
class CredentialStore(private val context: Context) {

    private object Keys {
        val USERNAME = stringPreferencesKey("wp_username")
        val APP_PASSWORD = stringPreferencesKey("wp_app_password")
        val DISPLAY_NAME = stringPreferencesKey("wp_display_name")
    }

    data class Credentials(val username: String, val appPassword: String, val displayName: String)

    val credentialsFlow: Flow<Credentials?> = context.dataStore.data.map { prefs ->
        val username = prefs[Keys.USERNAME]
        val appPassword = prefs[Keys.APP_PASSWORD]
        if (username != null && appPassword != null) {
            Credentials(username, appPassword, prefs[Keys.DISPLAY_NAME] ?: username)
        } else {
            null
        }
    }

    suspend fun current(): Credentials? = credentialsFlow.first()

    suspend fun save(username: String, appPassword: String, displayName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            prefs[Keys.APP_PASSWORD] = appPassword
            prefs[Keys.DISPLAY_NAME] = displayName
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
