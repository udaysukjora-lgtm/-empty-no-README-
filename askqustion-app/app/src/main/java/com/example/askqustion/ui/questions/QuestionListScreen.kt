package com.example.askqustion.ui.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.askqustion.data.model.WpPost
import com.example.askqustion.data.repository.AuthRepository
import com.example.askqustion.data.repository.QaRepository
import com.example.askqustion.util.htmlToPlainText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionListScreen(
    qaRepository: QaRepository,
    authRepository: AuthRepository,
    onOpenQuestion: (Long) -> Unit,
    onAskQuestion: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val viewModel: QuestionListViewModel = viewModel(
        factory = QuestionListViewModel.factory(qaRepository, authRepository),
    )
    val state by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val displayName by viewModel.displayName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isLoggedIn) "Hi, ${displayName ?: "there"}" else "AskQustion") },
                actions = {
                    IconButton(onClick = onOpenLogin) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Login/Profile")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (isLoggedIn) onAskQuestion() else onOpenLogin() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Question poochein") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Questions mein search karein") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.questions.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.questions.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = state.errorMessage ?: "Koi question nahi mila.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                            items(state.questions, key = { it.id }) { post ->
                                QuestionCard(post = post, onClick = { onOpenQuestion(post.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(post: WpPost, onClick: () -> Unit) {
    val authorName = post.embedded?.author?.firstOrNull()?.name
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = post.title.rendered.htmlToPlainText(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = post.excerpt.rendered.htmlToPlainText(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (authorName != null) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
