package com.potpal.mirrortrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.potpal.mirrortrack.ui.categories.CategoryDetailScreen
import com.potpal.mirrortrack.ui.categories.DataPointDetailScreen
import com.potpal.mirrortrack.ui.insights.InsightsScreen
import com.potpal.mirrortrack.ui.permissions.PermissionsScreen
import com.potpal.mirrortrack.ui.settings.SettingsScreen
import com.potpal.mirrortrack.ui.unlock.UnlockScreen

object Routes {
    const val UNLOCK = "unlock"
    const val INSIGHTS = "insights"
    const val CATEGORY_DETAIL = "category/{categoryName}"
    const val DATAPOINT_DETAIL = "datapoint/{rowId}"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"

    fun categoryDetail(categoryName: String) = "category/$categoryName"
    fun datapointDetail(rowId: Long) = "datapoint/$rowId"
}

@Composable
fun MirrorTrackNavHost(isDbOpen: Boolean) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isDbOpen) Routes.INSIGHTS else Routes.UNLOCK,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.UNLOCK) {
                UnlockScreen(
                    onUnlocked = {
                        navController.navigate(Routes.INSIGHTS) {
                            popUpTo(Routes.UNLOCK) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.INSIGHTS) {
                InsightsScreen(
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS)
                    },
                    onNavigateToCategoryDetail = { categoryName ->
                        navController.navigate(Routes.categoryDetail(categoryName))
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
