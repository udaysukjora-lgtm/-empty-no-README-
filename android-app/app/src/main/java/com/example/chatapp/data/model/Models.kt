package com.example.chatapp.data.model

import com.google.gson.annotations.SerializedName

data class SendOtpRequest(
    @SerializedName("phone_number") val phoneNumber: String,
)

data class SendOtpResponse(
    val message: String,
    @SerializedName("dev_note") val devNote: String? = null,
)

data class VerifyOtpRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    val otp: String,
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
)

data class ConversationSummary(
    @SerializedName("conversation_id") val conversationId: Long,
)

data class ConversationCreateRequest(
    @SerializedName("participant_phone") val participantPhone: String,
)

data class ConversationCreateResponse(
    @SerializedName("conversation_id") val conversationId: Long,
)

data class MessageOut(
    val id: Long,
    @SerializedName("conversation_id") val conversationId: Long,
    @SerializedName("sender_id") val senderId: Long,
    val content: String?,
    @SerializedName("message_type") val messageType: String,
    @SerializedName("media_url") val mediaUrl: String?,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
)
