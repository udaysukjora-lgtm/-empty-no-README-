package com.example.chatapp.config

import com.example.chatapp.BuildConfig

/**
 * Central place for backend endpoints. BASE_URL comes from the BuildConfig
 * field set in app/build.gradle.kts (defaults to the emulator's alias for
 * the host machine's localhost). WS_URL is derived from it.
 */
object Config {
    val BASE_URL: String = BuildConfig.BASE_URL

    val WS_URL: String
        get() = BASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/ws"
}
