package com.example.askqustion.ui.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.askqustion.data.model.WpComment
import com.example.askqustion.data.repository.AuthRepository
import com.example.askqustion.data.repository.QaRepository
import com.example.askqustion.util.htmlToPlainText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailScreen(
    questionId: Long,
    qaRepository: QaRepository,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val viewModel: QuestionDetailViewModel = viewModel(
        factory = QuestionDetailViewModel.factory(questionId, qaRepository, authRepository),
    )
    val state by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Question") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (isLoggedIn) {
                AnswerInputBar(
                    draft = state.draftAnswer,
                    isSubmitting = state.isSubmitting,
                    onDraftChanged = viewModel::onDraftAnswerChanged,
                    onSend = viewModel::postAnswer,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(onClick = onOpenLogin) { Text("Answer dene ke liye login karein") }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.question == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.question == null -> {
                    Text(
                        text = state.errorMessage ?: "Question load nahi ho paaya",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
                else -> {
                    val question = state.question!!
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = question.title.rendered.htmlToPlainText(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = question.content.rendered.htmlToPlainText(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            HorizontalDivider()
                            Text(
                                text = "${state.answers.size} Answers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        items(state.answers, key = { it.id }) { answer ->
                            AnswerCard(answer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerCard(answer: WpComment) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = answer.authorName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = answer.content.rendered.htmlToPlainText(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AnswerInputBar(
    draft: String,
    isSubmitting: Boolean,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Apna answer likhein...") },
            enabled = !isSubmitting,
        )
        IconButton(onClick = onSend, enabled = !isSubmitting) {
            Icon(Icons.Filled.Send, contentDescription = "Answer bhejein")
        }
    }
}
