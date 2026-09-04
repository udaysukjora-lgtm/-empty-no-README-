package com.example.chatapp.data.remote

import com.example.chatapp.data.model.ConversationCreateRequest
import com.example.chatapp.data.model.ConversationCreateResponse
import com.example.chatapp.data.model.ConversationSummary
import com.example.chatapp.data.model.MessageOut
import com.example.chatapp.data.model.SendOtpRequest
import com.example.chatapp.data.model.SendOtpResponse
import com.example.chatapp.data.model.TokenResponse
import com.example.chatapp.data.model.VerifyOtpRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): SendOtpResponse

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): TokenResponse

    @GET("conversations")
    suspend fun listConversations(): List<ConversationSummary>

    @POST("conversations")
    suspend fun createConversation(@Body request: ConversationCreateRequest): ConversationCreateResponse

    @GET("conversations/{id}/messages")
    suspend fun getMessages(
        @Path("id") conversationId: Long,
        @Query("before") before: Long? = null,
    ): List<MessageOut>
}
