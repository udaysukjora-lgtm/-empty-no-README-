package com.example.askqustion.ui.questions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.askqustion.data.model.WpComment
import com.example.askqustion.data.model.WpPost
import com.example.askqustion.data.repository.AuthRepository
import com.example.askqustion.data.repository.QaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuestionDetailUiState(
    val question: WpPost? = null,
    val answers: List<WpComment> = emptyList(),
    val draftAnswer: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

class QuestionDetailViewModel(
    private val questionId: Long,
    private val qaRepository: QaRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionDetailUiState())
    val uiState: StateFlow<QuestionDetailUiState> = _uiState

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedInFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val question = qaRepository.getQuestion(questionId)
                val answers = qaRepository.getAnswers(questionId)
                question to answers
            }.onSuccess { (question, answers) ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    question = question,
                    answers = answers,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Question load nahi ho paaya",
                )
            }
        }
    }

    fun onDraftAnswerChanged(value: String) {
        _uiState.value = _uiState.value.copy(draftAnswer = value)
    }

    fun postAnswer() {
        val text = _uiState.value.draftAnswer.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
            runCatching { qaRepository.postAnswer(questionId, text) }
                .onSuccess { newAnswer ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        draftAnswer = "",
                        answers = _uiState.value.answers + newAnswer,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = e.message ?: "Answer post nahi ho paaya",
                    )
                }
        }
    }

    companion object {
        fun factory(questionId: Long, qaRepository: QaRepository, authRepository: AuthRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    QuestionDetailViewModel(questionId, qaRepository, authRepository) as T
            }
    }
}
