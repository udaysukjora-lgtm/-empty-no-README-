package com.example.askqustion.data.repository

import com.example.askqustion.data.model.NewCommentRequest
import com.example.askqustion.data.model.NewPostRequest
import com.example.askqustion.data.model.WpCategory
import com.example.askqustion.data.model.WpComment
import com.example.askqustion.data.model.WpPost
import com.example.askqustion.data.remote.WpApiService
import retrofit2.HttpException

class QaRepository(private val api: WpApiService) {

    suspend fun getQuestions(page: Int, search: String? = null, categoryId: Long? = null): List<WpPost> =
        api.getPosts(page = page, search = search?.takeIf { it.isNotBlank() }, categoryId = categoryId)

    suspend fun getQuestion(id: Long): WpPost = api.getPost(id)

    suspend fun getAnswers(postId: Long): List<WpComment> = api.getComments(postId)

    suspend fun postAnswer(postId: Long, content: String): WpComment =
        api.createComment(NewCommentRequest(post = postId, content = content))

    suspend fun getCategories(): List<WpCategory> = api.getCategories()

    /**
     * Tries to publish the question directly; if the logged-in account only
     * has permission to submit for review (e.g. a Contributor role),
     * WordPress returns 403 rest_cannot_publish and we retry as "pending".
     */
    suspend fun askQuestion(title: String, content: String, categoryId: Long?): WpPost {
        val categories = categoryId?.let { listOf(it) }
        return try {
            api.createPost(NewPostRequest(title = title, content = content, status = "publish", categories = categories))
        } catch (e: HttpException) {
            if (e.code() == 403) {
                api.createPost(NewPostRequest(title = title, content = content, status = "pending", categories = categories))
            } else {
                throw e
            }
        }
    }
}
