package com.example.messagingapp.data.remote

import com.example.messagingapp.data.model.ConversationCreateRequest
import com.example.messagingapp.data.model.ConversationCreateResponse
import com.example.messagingapp.data.model.ConversationSummary
import com.example.messagingapp.data.model.MessageOut
import com.example.messagingapp.data.model.SendOtpRequest
import com.example.messagingapp.data.model.SendOtpResponse
import com.example.messagingapp.data.model.TokenResponse
import com.example.messagingapp.data.model.VerifyOtpRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/send-otp")
    suspend fun sendOtp(@Body body: SendOtpRequest): SendOtpResponse

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): TokenResponse

    @GET("conversations")
    suspend fun listConversations(@Header("Authorization") bearer: String): List<ConversationSummary>

    @POST("conversations")
    suspend fun createConversation(
        @Header("Authorization") bearer: String,
        @Body body: ConversationCreateRequest
    ): ConversationCreateResponse

    @GET("conversations/{id}/messages")
    suspend fun getMessages(
        @Header("Authorization") bearer: String,
        @Path("id") conversationId: Long,
        @Query("before") before: Long? = null
    ): List<MessageOut>
}
