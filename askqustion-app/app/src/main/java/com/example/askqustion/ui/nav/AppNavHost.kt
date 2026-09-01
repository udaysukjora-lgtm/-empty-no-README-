package com.example.askqustion.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.askqustion.data.repository.AuthRepository
import com.example.askqustion.data.repository.QaRepository
import com.example.askqustion.ui.ask.AskQuestionScreen
import com.example.askqustion.ui.login.LoginScreen
import com.example.askqustion.ui.questions.QuestionDetailScreen
import com.example.askqustion.ui.questions.QuestionListScreen

private const val ROUTE_QUESTIONS = "questions"
private const val ROUTE_QUESTION_DETAIL = "question/{questionId}"
private const val ROUTE_ASK = "ask"
private const val ROUTE_LOGIN = "login"

@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    qaRepository: QaRepository,
) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_QUESTIONS) {
        composable(ROUTE_QUESTIONS) {
            QuestionListScreen(
                qaRepository = qaRepository,
                authRepository = authRepository,
                onOpenQuestion = { id -> navController.navigate("question/$id") },
                onAskQuestion = { navController.navigate(ROUTE_ASK) },
                onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
            )
        }
        composable(ROUTE_QUESTION_DETAIL) { backStackEntry ->
            val questionId = backStackEntry.arguments
                ?.getString("questionId")
                ?.toLongOrNull() ?: return@composable
            QuestionDetailScreen(
                questionId = questionId,
                qaRepository = qaRepository,
                authRepository = authRepository,
                onBack = { navController.popBackStack() },
                onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
            )
        }
        composable(ROUTE_ASK) {
            AskQuestionScreen(
                qaRepository = qaRepository,
                onBack = { navController.popBackStack() },
                onPosted = { id ->
                    navController.popBackStack()
                    navController.navigate("question/$id")
                },
            )
        }
        composable(ROUTE_LOGIN) {
            LoginScreen(
                authRepository = authRepository,
                onLoggedIn = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
