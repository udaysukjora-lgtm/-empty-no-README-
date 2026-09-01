package com.example.askqustion.data.remote

import android.util.Base64
import com.example.askqustion.BuildConfig
import com.example.askqustion.config.Config
import com.example.askqustion.data.local.CredentialStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Attaches WordPress "Application Password" Basic Auth when the user is
 * logged in. Reads (GET) work fine without it on an open site; writes
 * (asking a question, posting an answer) need it.
 */
class BasicAuthInterceptor(private val credentialStore: CredentialStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val creds = runBlocking { credentialStore.current() }
        val request = chain.request().newBuilder().apply {
            if (creds != null) {
                val raw = "${creds.username}:${creds.appPassword}"
                val encoded = Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
                addHeader("Authorization", "Basic $encoded")
            }
        }.build()
        return chain.proceed(request)
    }
}

object NetworkModule {

    fun okHttpClient(credentialStore: CredentialStore): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor(credentialStore))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun apiService(client: OkHttpClient): WpApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(Config.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(WpApiService::class.java)
    }

    /** A second client/service pair with fixed one-off credentials, used only to validate a login attempt. */
    fun apiServiceWithCredentials(username: String, appPassword: String): WpApiService {
        val raw = "$username:$appPassword"
        val encoded = Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Basic $encoded")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(Config.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(WpApiService::class.java)
    }
}
