package com.example.askqustion.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.askqustion.data.repository.QaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AskQuestionUiState(
    val title: String = "",
    val content: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

class AskQuestionViewModel(private val qaRepository: QaRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AskQuestionUiState())
    val uiState: StateFlow<AskQuestionUiState> = _uiState

    fun onTitleChanged(value: String) {
        _uiState.value = _uiState.value.copy(title = value, errorMessage = null)
    }

    fun onContentChanged(value: String) {
        _uiState.value = _uiState.value.copy(content = value, errorMessage = null)
    }

    fun submit(onPosted: (Long) -> Unit) {
        val title = _uiState.value.title.trim()
        val content = _uiState.value.content.trim()
        if (title.isBlank() || content.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Title aur details dono chahiye")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
            runCatching { qaRepository.askQuestion(title, content, categoryId = null) }
                .onSuccess { post ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    onPosted(post.id)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = e.message ?: "Question post nahi ho paaya",
                    )
                }
        }
    }

    companion object {
        fun factory(qaRepository: QaRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AskQuestionViewModel(qaRepository) as T
        }
    }
}
