package com.example.chatapp.ui.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.data.repository.ChatRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    chatRepository: ChatRepository,
    authRepository: AuthRepository,
    onOpenConversation: (Long) -> Unit,
    onLoggedOut: () -> Unit,
) {
    val viewModel: ConversationsViewModel = viewModel(
        factory = ConversationsViewModel.factory(chatRepository, authRepository),
    )
    val state by viewModel.uiState.collectAsState()
    var showStartDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversations") },
                actions = {
                    IconButton(onClick = { viewModel.logout(onLoggedOut) }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showStartDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nayi conversation")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.conversations.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.conversations.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Abhi koi conversation nahi hai.\n+ dabakar nayi shuru karein.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.conversations) { convo ->
                            Card(
                                onClick = { onOpenConversation(convo.conversationId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                ListItem(
                                    headlineContent = { Text("Conversation #${convo.conversationId}") },
                                    leadingContent = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
                                )
                            }
                        }
                    }
                }
            }

            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    if (showStartDialog) {
        StartConversationDialog(
            isSubmitting = state.isStartingConversation,
            onDismiss = { showStartDialog = false },
            onConfirm = { phone ->
                viewModel.startConversation(phone) { id ->
                    showStartDialog = false
                    onOpenConversation(id)
                }
            },
        )
    }
}

@Composable
private fun StartConversationDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nayi conversation") },
        text = {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Doosre user ka phone number") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = !isSubmitting, onClick = { onConfirm(phone.trim()) }) {
                Text("Shuru karein")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
