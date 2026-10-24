package dev.agam.learn.ui.navigation

/**
 * Destinations and route definitions for navigation-compose.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Path : Screen("path")

    data object Lesson : Screen("lesson/{lessonId}") {
        fun createRoute(lessonId: String): String = "lesson/$lessonId"
    }

    data object CodeExercise : Screen("code_exercise/{exerciseId}") {
        fun createRoute(exerciseId: String): String = "code_exercise/$exerciseId"
    }

    data object Problem : Screen("problem/{problemId}") {
        fun createRoute(problemId: String): String = "problem/$problemId"
    }

    data object Review : Screen("review")
    data object Stats : Screen("stats")
    data object Settings : Screen("settings")
}
