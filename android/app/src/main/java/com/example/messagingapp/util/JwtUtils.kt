package com.example.messagingapp.util

import android.util.Base64
import com.google.gson.Gson

private data class JwtPayload(val sub: String? = null)

/** Decodes the `sub` claim (our user id) out of the backend's JWT without verifying it. */
fun decodeUserIdFromJwt(token: String): Long? {
    return try {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val payload = Gson().fromJson(String(decoded, Charsets.UTF_8), JwtPayload::class.java)
        payload.sub?.toLongOrNull()
    } catch (e: Exception) {
        null
    }
}
