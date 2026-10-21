package com.zaitxcode.opencut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zaitxcode.opencut.ui.account.AccountScreen
import com.zaitxcode.opencut.ui.account.OnboardingScreen
import com.zaitxcode.opencut.ui.editor.EditorScreen
import com.zaitxcode.opencut.ui.export.ExportScreen
import com.zaitxcode.opencut.ui.home.HomeScreen
import com.zaitxcode.opencut.ui.theme.OpenCutTheme

/** Top level navigation destinations. */
object Routes {
    const val START = "start"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ACCOUNT = "account"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}"

    fun editor(projectId: String) = "editor/$projectId"
    fun export(projectId: String) = "export/$projectId"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenCutTheme {
                OpenCutNavHost()
            }
        }
    }
}

@Composable
private fun OpenCutNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Routes.START,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Decides whether the first launch gate is needed. Nothing is stored
        // here: the account store already answers the question.
        composable(Routes.START) {
            LaunchedEffect(Unit) {
                val app = context.applicationContext as OpenCutApp
                val destination = if (app.accountStore.hasProfile()) Routes.HOME else Routes.ONBOARDING
                navController.navigate(destination) {
                    popUpTo(Routes.START) { inclusive = true }
                }
            }
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenProject = { projectId -> navController.navigate(Routes.editor(projectId)) },
                onCreateProject = { projectId -> navController.navigate(Routes.editor(projectId)) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            EditorScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onExport = { navController.navigate(Routes.export(projectId)) },
            )
        }

        composable(
            route = Routes.EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            ExportScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
