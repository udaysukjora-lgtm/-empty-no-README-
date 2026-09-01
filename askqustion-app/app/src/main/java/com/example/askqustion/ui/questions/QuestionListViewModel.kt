package com.example.askqustion.ui.questions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.askqustion.data.model.WpPost
import com.example.askqustion.data.repository.AuthRepository
import com.example.askqustion.data.repository.QaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuestionListUiState(
    val questions: List<WpPost> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
)

class QuestionListViewModel(
    private val qaRepository: QaRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionListUiState())
    val uiState: StateFlow<QuestionListUiState> = _uiState

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedInFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val displayName: StateFlow<String?> = authRepository.displayNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        refresh()
    }

    fun onSearchQueryChanged(value: String) {
        _uiState.value = _uiState.value.copy(searchQuery = value)
    }

    fun search() = refresh()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { qaRepository.getQuestions(page = 1, search = _uiState.value.searchQuery) }
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, questions = list)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Questions load nahi ho paaye",
                    )
                }
        }
    }

    companion object {
        fun factory(qaRepository: QaRepository, authRepository: AuthRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    QuestionListViewModel(qaRepository, authRepository) as T
            }
    }
}
