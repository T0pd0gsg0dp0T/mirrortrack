package com.potpal.mirrortrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.potpal.mirrortrack.ui.categories.CategoryBrowserScreen
import com.potpal.mirrortrack.ui.categories.CategoryDetailScreen
import com.potpal.mirrortrack.ui.categories.DataPointDetailScreen
import com.potpal.mirrortrack.ui.feed.LiveFeedScreen
import com.potpal.mirrortrack.ui.insights.InsightsScreen
import com.potpal.mirrortrack.ui.permissions.PermissionsScreen
import com.potpal.mirrortrack.ui.settings.SettingsScreen
import com.potpal.mirrortrack.ui.unlock.UnlockScreen

object Routes {
    const val UNLOCK = "unlock"
    const val FEED = "feed"
    const val INSIGHTS = "insights"
    const val CATEGORIES = "categories"
    const val CATEGORY_DETAIL = "category/{categoryName}"
    const val DATAPOINT_DETAIL = "datapoint/{rowId}"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"

    fun categoryDetail(categoryName: String) = "category/$categoryName"
    fun datapointDetail(rowId: Long) = "datapoint/$rowId"
}

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    data object Feed : BottomNavItem(Routes.FEED, Icons.Default.RssFeed, "Feed")
    data object Insights : BottomNavItem(Routes.INSIGHTS, Icons.Default.Psychology, "Insights")
    data object Categories : BottomNavItem(Routes.CATEGORIES, Icons.Default.Category, "Categories")
    data object Settings : BottomNavItem(Routes.SETTINGS, Icons.Default.Settings, "Settings")
}

@Composable
fun MirrorTrackNavHost(isDbOpen: Boolean) {
    val navController = rememberNavController()
    val bottomNavItems = listOf(
        BottomNavItem.Feed,
        BottomNavItem.Insights,
        BottomNavItem.Categories,
        BottomNavItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute != Routes.UNLOCK

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = navBackStackEntry?.destination?.hierarchy?.any {
                            it.route == item.route
                        } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isDbOpen) Routes.FEED else Routes.UNLOCK,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.UNLOCK) {
                UnlockScreen(
                    onUnlocked = {
                        navController.navigate(Routes.FEED) {
                            popUpTo(Routes.UNLOCK) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.FEED) {
                LiveFeedScreen(
                    onDataPointClick = { rowId ->
                        navController.navigate(Routes.datapointDetail(rowId))
                    }
                )
            }

            composable(Routes.INSIGHTS) {
                InsightsScreen()
            }

            composable(Routes.CATEGORIES) {
                CategoryBrowserScreen(
                    onCategoryClick = { categoryName ->
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
                    }
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
