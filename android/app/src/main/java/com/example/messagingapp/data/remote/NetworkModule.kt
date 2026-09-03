package com.example.messagingapp.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 10.0.2.2 is the Android emulator's alias for the host machine's localhost,
 * matching the FastAPI backend (main.py) run with `uvicorn main:app --reload`.
 * Point BASE_URL/WS_URL at your deployed backend for a real device.
 */
object NetworkModule {
    const val BASE_URL = "http://10.0.2.2:8000/"
    const val WS_URL = "ws://10.0.2.2:8000/ws"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
