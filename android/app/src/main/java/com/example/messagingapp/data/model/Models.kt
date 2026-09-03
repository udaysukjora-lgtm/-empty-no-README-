package com.example.messagingapp.data.model

data class SendOtpRequest(val phone_number: String)

data class SendOtpResponse(val message: String? = null, val dev_note: String? = null)

data class VerifyOtpRequest(val phone_number: String, val otp: String)

data class TokenResponse(val access_token: String, val token_type: String = "bearer")

data class ConversationSummary(val conversation_id: Long)

data class ConversationCreateRequest(val participant_phone: String)

data class ConversationCreateResponse(val conversation_id: Long)

data class MessageOut(
    val id: Long,
    val conversation_id: Long,
    val sender_id: Long,
    val content: String?,
    val message_type: String,
    val media_url: String?,
    val status: String,
    val created_at: String
)
