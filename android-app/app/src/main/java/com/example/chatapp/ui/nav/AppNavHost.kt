package com.example.chatapp.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.data.repository.ChatRepository
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.conversations.ConversationsScreen
import com.example.chatapp.ui.login.LoginScreen

private const val ROUTE_LOGIN = "login"
private const val ROUTE_CONVERSATIONS = "conversations"
private const val ROUTE_CHAT = "chat/{conversationId}"

/** Distinguishes "DataStore hasn't emitted yet" from "emitted, no token stored". */
private sealed class TokenState {
    data object Loading : TokenState()
    data class Resolved(val token: String?) : TokenState()
}

@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    chatRepository: ChatRepository,
) {
    val navController: NavHostController = rememberNavController()

    val tokenState by produceState<TokenState>(initialValue = TokenState.Loading, authRepository) {
        authRepository.tokenFlow.collect { value = TokenState.Resolved(it) }
    }

    val resolved = tokenState as? TokenState.Resolved
    if (resolved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (resolved.token.isNullOrBlank()) ROUTE_LOGIN else ROUTE_CONVERSATIONS,
    ) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                authRepository = authRepository,
                onLoggedIn = {
                    navController.navigate(ROUTE_CONVERSATIONS) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(ROUTE_CONVERSATIONS) {
            ConversationsScreen(
                chatRepository = chatRepository,
                authRepository = authRepository,
                onOpenConversation = { id -> navController.navigate("chat/$id") },
                onLoggedOut = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_CONVERSATIONS) { inclusive = true }
                    }
                },
            )
        }
        composable(ROUTE_CHAT) { backStackEntry ->
            val conversationId = backStackEntry.arguments
                ?.getString("conversationId")
                ?.toLongOrNull() ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                chatRepository = chatRepository,
                authRepository = authRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
