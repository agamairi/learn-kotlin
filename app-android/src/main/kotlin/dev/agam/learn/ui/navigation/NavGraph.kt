package dev.agam.learn.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.agam.learn.ui.home.HomeScreen
import dev.agam.learn.ui.lesson.LessonScreen
import dev.agam.learn.ui.path.PathScreen

private data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

private val BottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, "Home", Icons.Default.Home),
    BottomNavItem(Screen.Path.route, "Path", Icons.Default.PlayArrow),
    BottomNavItem(Screen.Review.route, "Review", Icons.Default.Refresh),
    BottomNavItem(Screen.Stats.route, "Stats", Icons.Default.Star),
    BottomNavItem(Screen.Settings.route, "Settings", Icons.Default.Settings)
)

/**
 * Main application navigation host with bottom navigation bar and route configuration.
 */
@Composable
fun LearnAppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isTopLevelDestination = BottomNavItems.any { it.route == currentRoute }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    BottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(text = item.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Home
            composable(Screen.Home.route) {
                HomeScreen(
                    onContinueLesson = { lessonId ->
                        navController.navigate(Screen.Lesson.createRoute(lessonId))
                    },
                    onOpenPath = {
                        navController.navigate(Screen.Path.route)
                    },
                    onOpenReview = {
                        navController.navigate(Screen.Review.route)
                    }
                )
            }

            // Path
            composable(Screen.Path.route) {
                PathScreen(
                    onLessonClick = { lessonId ->
                        navController.navigate(Screen.Lesson.createRoute(lessonId))
                    }
                )
            }

            // Lesson Player
            composable(
                route = Screen.Lesson.route,
                arguments = listOf(
                    navArgument("lessonId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                LessonScreen(
                    lessonId = lessonId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // Placeholders for parallel Wave 2 development
            composable(
                route = Screen.CodeExercise.route,
                arguments = listOf(
                    navArgument("exerciseId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: ""
                PlaceholderScreen(
                    title = "Code Exercise ($exerciseId)",
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Problem.route,
                arguments = listOf(
                    navArgument("problemId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val problemId = backStackEntry.arguments?.getString("problemId") ?: ""
                PlaceholderScreen(
                    title = "Problem ($problemId)",
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Review.route) {
                PlaceholderScreen(
                    title = "Review Queue",
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Stats.route) {
                PlaceholderScreen(
                    title = "Stats & Analytics",
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                PlaceholderScreen(
                    title = "Settings",
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
