package com.example.askqustion.data.model

import com.google.gson.annotations.SerializedName

/**
 * WordPress wraps HTML fields (title, content, excerpt) in a `{"rendered": "..."}`
 * object so it can also carry a `raw` variant for authenticated requests.
 */
data class RenderedText(
    val rendered: String,
    val raw: String? = null,
)

data class WpPost(
    val id: Long,
    val date: String,
    val slug: String,
    val link: String,
    val title: RenderedText,
    val content: RenderedText,
    val excerpt: RenderedText,
    val author: Long,
    val categories: List<Long> = emptyList(),
    @SerializedName("comment_status") val commentStatus: String? = null,
    @SerializedName("_embedded") val embedded: WpEmbedded? = null,
)

data class WpEmbedded(
    @SerializedName("author") val author: List<WpUser>? = null,
    @SerializedName("wp:featuredmedia") val featuredMedia: List<WpMedia>? = null,
)

data class WpMedia(
    @SerializedName("source_url") val sourceUrl: String?,
)

data class WpUser(
    val id: Long,
    val name: String,
    @SerializedName("avatar_urls") val avatarUrls: Map<String, String>? = null,
)

data class WpComment(
    val id: Long,
    val post: Long,
    val parent: Long,
    @SerializedName("author_name") val authorName: String,
    val date: String,
    val content: RenderedText,
)

data class WpCategory(
    val id: Long,
    val name: String,
    val slug: String,
    val count: Int,
)

data class NewPostRequest(
    val title: String,
    val content: String,
    val status: String,
    val categories: List<Long>? = null,
)

data class NewCommentRequest(
    val post: Long,
    val content: String,
    val parent: Long? = null,
)
