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

    data object CodeExercise : Screen("code_exercise/{exerciseId}?lessonRunId={lessonRunId}") {
        fun createRoute(exerciseId: String, lessonRunId: String? = null): String =
            "code_exercise/$exerciseId" + (lessonRunId?.let { "?lessonRunId=$it" } ?: "")
    }

    data object Problem : Screen("problem/{problemId}?lessonRunId={lessonRunId}") {
        fun createRoute(problemId: String, lessonRunId: String? = null): String =
            "problem/$problemId" + (lessonRunId?.let { "?lessonRunId=$it" } ?: "")
    }

    data object Review : Screen("review")
    data object Stats : Screen("stats")
    data object Settings : Screen("settings")
}
