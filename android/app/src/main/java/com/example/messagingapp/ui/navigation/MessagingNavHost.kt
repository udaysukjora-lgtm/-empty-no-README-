package com.example.messagingapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.messagingapp.data.local.TokenManager
import com.example.messagingapp.ui.chat.ChatScreen
import com.example.messagingapp.ui.conversations.ConversationsScreen
import com.example.messagingapp.ui.login.LoginScreen

private object Routes {
    const val LOGIN = "login"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}"

    fun chat(conversationId: Long) = "chat/$conversationId"
}

private enum class AuthStatus { LOADING, LOGGED_OUT, LOGGED_IN }

@Composable
fun MessagingNavHost() {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    val authStatus by produceState(initialValue = AuthStatus.LOADING, tokenManager) {
        tokenManager.tokenFlow.collect { token ->
            value = if (token.isNullOrBlank()) AuthStatus.LOGGED_OUT else AuthStatus.LOGGED_IN
        }
    }

    if (authStatus == AuthStatus.LOADING) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    val navController = rememberNavController()
    val startDestination = if (authStatus == AuthStatus.LOGGED_IN) Routes.CONVERSATIONS else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.CONVERSATIONS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(onOpenConversation = { conversationId ->
                navController.navigate(Routes.chat(conversationId))
            })
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: -1L
            ChatScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
