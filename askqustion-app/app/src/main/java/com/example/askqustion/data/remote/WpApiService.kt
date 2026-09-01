package com.example.askqustion.data.remote

import com.example.askqustion.data.model.NewCommentRequest
import com.example.askqustion.data.model.NewPostRequest
import com.example.askqustion.data.model.WpCategory
import com.example.askqustion.data.model.WpComment
import com.example.askqustion.data.model.WpPost
import com.example.askqustion.data.model.WpUser
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface WpApiService {

    /** "Questions" list. `_embed=1` pulls in author + featured image in one call. */
    @GET("wp/v2/posts")
    suspend fun getPosts(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null,
        @Query("categories") categoryId: Long? = null,
        @Query("_embed") embed: Int = 1,
    ): List<WpPost>

    @GET("wp/v2/posts/{id}")
    suspend fun getPost(
        @Path("id") id: Long,
        @Query("_embed") embed: Int = 1,
    ): WpPost

    @POST("wp/v2/posts")
    suspend fun createPost(@Body request: NewPostRequest): WpPost

    /** "Answers" for a question. */
    @GET("wp/v2/comments")
    suspend fun getComments(
        @Query("post") postId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): List<WpComment>

    @POST("wp/v2/comments")
    suspend fun createComment(@Body request: NewCommentRequest): WpComment

    @GET("wp/v2/categories")
    suspend fun getCategories(@Query("per_page") perPage: Int = 50): List<WpCategory>

    /** Also doubles as a login check: 401 means the credentials are wrong. */
    @GET("wp/v2/users/me")
    suspend fun getMe(): WpUser
}
