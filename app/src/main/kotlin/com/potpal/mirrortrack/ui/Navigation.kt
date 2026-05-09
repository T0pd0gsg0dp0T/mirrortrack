package com.potpal.mirrortrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.potpal.mirrortrack.ui.categories.CategoryDetailScreen
import com.potpal.mirrortrack.ui.categories.DataPointDetailScreen
import com.potpal.mirrortrack.ui.insights.InsightsScreen
import com.potpal.mirrortrack.ui.onboarding.OnboardingScreen
import com.potpal.mirrortrack.ui.onboarding.OnboardingViewModel
import com.potpal.mirrortrack.ui.permissions.PermissionsScreen
import com.potpal.mirrortrack.ui.settings.SettingsScreen
import com.potpal.mirrortrack.ui.unlock.UnlockScreen

object Routes {
    const val UNLOCK = "unlock"
    const val POST_UNLOCK = "post_unlock"
    const val ONBOARDING = "onboarding?startGroup={startGroup}"
    const val INSIGHTS = "insights"
    const val CATEGORY_DETAIL = "category/{categoryName}"
    const val DATAPOINT_DETAIL = "datapoint/{rowId}"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    fun categoryDetail(categoryName: String) = "category/$categoryName"
    fun datapointDetail(rowId: Long) = "datapoint/$rowId"
    fun onboarding(startGroupId: String? = null): String =
        if (startGroupId == null) "onboarding?startGroup=" else "onboarding?startGroup=$startGroupId"
}

@Composable
fun MirrorTrackNavHost(isDbOpen: Boolean) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isDbOpen) Routes.POST_UNLOCK else Routes.UNLOCK,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.UNLOCK) {
                UnlockScreen(
                    onUnlocked = {
                        navController.navigate(Routes.POST_UNLOCK) {
                            popUpTo(Routes.UNLOCK) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.POST_UNLOCK) {
                val vm: OnboardingViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    val seen = vm.entrySeenOnce()
                    val target = if (seen) Routes.INSIGHTS else Routes.onboarding(null)
                    navController.navigate(target) {
                        popUpTo(Routes.POST_UNLOCK) { inclusive = true }
                    }
                }
                Box(modifier = Modifier.fillMaxSize())
            }

            composable(
                Routes.ONBOARDING,
                arguments = listOf(
                    navArgument("startGroup") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = false
                    }
                )
            ) { backStackEntry ->
                val arg = backStackEntry.arguments?.getString("startGroup")?.takeIf { it.isNotEmpty() }
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.INSIGHTS) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                    startGroupId = arg
                )
            }

            composable(Routes.INSIGHTS) {
                InsightsScreen(
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS)
                    },
                    onNavigateToCategoryDetail = { categoryName ->
                        navController.navigate(Routes.categoryDetail(categoryName))
                    },
                    onResumeOnboarding = { startGroupId ->
                        navController.navigate(Routes.onboarding(startGroupId))
                    }
                )
            }

            composable(
                Routes.CATEGORY_DETAIL,
                arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
            ) { backStackEntry ->
                val categoryName = backStackEntry.arguments?.getString("categoryName") ?: return@composable
                CategoryDetailScreen(
                    categoryName = categoryName,
                    onDataPointClick = { rowId ->
                        navController.navigate(Routes.datapointDetail(rowId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                Routes.DATAPOINT_DETAIL,
                arguments = listOf(navArgument("rowId") { type = NavType.LongType })
            ) { backStackEntry ->
                val rowId = backStackEntry.arguments?.getLong("rowId") ?: return@composable
                DataPointDetailScreen(
                    rowId = rowId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToPermissions = {
                        navController.navigate(Routes.PERMISSIONS)
                    },
                    onResumeOnboarding = { startGroupId ->
                        navController.navigate(Routes.onboarding(startGroupId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PERMISSIONS) {
                PermissionsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
