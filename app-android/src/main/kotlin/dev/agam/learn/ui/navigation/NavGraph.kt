package dev.agam.learn.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.agam.learn.ai.AiAccess
import dev.agam.learn.ai.loadAiAccess
import dev.agam.learn.di.AppContainer
import dev.agam.learn.di.LocalAppContainer
import dev.agam.learn.model.Exercise
import dev.agam.learn.ui.code.CodeExerciseScreen
import dev.agam.learn.ui.code.CodeExerciseViewModel
import dev.agam.learn.ui.experience.LocalLearningFeedback
import dev.agam.learn.ui.home.HomeScreen
import dev.agam.learn.ui.lesson.LessonScreen
import dev.agam.learn.ui.lesson.LessonViewModel
import dev.agam.learn.ui.path.PathScreen
import dev.agam.learn.ui.problem.MultiPartProblemScreen
import dev.agam.learn.ui.problem.MultiPartProblemViewModel
import dev.agam.learn.ui.review.ReviewScreen
import dev.agam.learn.ui.settings.SettingsScreen
import dev.agam.learn.ui.stats.StatsScreen
import kotlinx.coroutines.flow.collect

/** Saved-state key the studio screens use to tell the lesson which exercise was completed. */
private const val COMPLETED_EXERCISE_KEY = "completed_exercise_id"

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

/** Finds an exercise anywhere in the course catalogue. */
private suspend fun findExercise(appContainer: AppContainer, exerciseId: String): Exercise? =
    appContainer.courseRepository.listCourses()
        .flatMap { it.chapters }
        .flatMap { it.lessons }
        .flatMap { it.exercises }
        .firstOrNull { it.id == exerciseId }

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
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                            label = {
                                Text(
                                    text = item.title,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                val appContainer = LocalAppContainer.current
                var aiAccess by remember { mutableStateOf<AiAccess?>(null) }
                LaunchedEffect(Unit) {
                    aiAccess = loadAiAccess(appContainer.settingsRepository, appContainer.secretStore)
                }
                val access = aiAccess
                val lessonViewModel: LessonViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    key = lessonId,
                    factory = LessonViewModel.provideFactory(
                        appContainer = appContainer,
                        isInterviewMode = access?.interviewMode ?: false,
                        hasApiKey = access?.hasApiKey ?: false
                    )
                )

                // Set by a studio screen when the learner returns from a passed drill or problem.
                val completedExerciseId by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(COMPLETED_EXERCISE_KEY, null)
                    .collectAsState()
                LaunchedEffect(completedExerciseId) {
                    val exerciseId = completedExerciseId ?: return@LaunchedEffect
                    backStackEntry.savedStateHandle.remove<String>(COMPLETED_EXERCISE_KEY)
                    lessonViewModel.onStudioExerciseCompleted(exerciseId)
                }

                LessonScreen(
                    lessonId = lessonId,
                    viewModel = lessonViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOpenExercise = { route ->
                        navController.navigate(route)
                    }
                )
            }

            // Code Exercise Screen
            composable(
                route = Screen.CodeExercise.route,
                arguments = listOf(
                    navArgument("exerciseId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: ""
                val appContainer = LocalAppContainer.current
                var exercise by remember { mutableStateOf<Exercise.WriteFunction?>(null) }
                var aiAccess by remember { mutableStateOf<AiAccess?>(null) }

                LaunchedEffect(exerciseId) {
                    val found = findExercise(appContainer, exerciseId) as? Exercise.WriteFunction
                    // AI access is set before the exercise, so the ViewModel is never built with stale settings.
                    aiAccess = loadAiAccess(appContainer.settingsRepository, appContainer.secretStore)
                    exercise = found
                }

                val currentExercise = exercise
                val access = aiAccess
                if (currentExercise != null && access != null) {
                    // Scoped to this back-stack entry, so leaving the screen clears its jobs and rotation keeps the code.
                    val viewModel: CodeExerciseViewModel = viewModel(
                        key = currentExercise.id,
                        factory = viewModelFactory {
                            initializer {
                                CodeExerciseViewModel(
                                    exercise = currentExercise,
                                    codeRunner = appContainer.languageSupport.runner,
                                    progressRecorder = appContainer.progressRecorder,
                                    aiTutor = appContainer.aiTutor,
                                    isInterviewMode = access.interviewMode,
                                    hasApiKey = access.hasApiKey
                                )
                            }
                        }
                    )
                    val state by viewModel.uiState.collectAsState()
                    val learningFeedback = LocalLearningFeedback.current
                    LaunchedEffect(viewModel, learningFeedback) {
                        viewModel.feedbackEvents.collect { correct ->
                            learningFeedback.onAnswer(correct)
                        }
                    }

                    CodeExerciseScreen(
                        state = state,
                        onCodeChange = viewModel::onCodeChange,
                        onRunClick = viewModel::runCode,
                        onCheckClick = viewModel::checkCode,
                        onRevealHintClick = viewModel::revealNextHint,
                        onExplainMistakeClick = viewModel::askAiExplainMistake,
                        onAskHintClick = { viewModel.askAiHint() },
                        onAskHintLevelClick = viewModel::askAiHint,
                        onReviewCodeClick = viewModel::askAiReview,
                        onDismissOutcome = viewModel::dismissOutcome,
                        onDismissAiDialog = viewModel::dismissAiDialog,
                        onNavigateBack = { navController.popBackStack() },
                        onFinish = {
                            // The code drill has recorded its passing attempt; the lesson only advances on this id.
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                COMPLETED_EXERCISE_KEY,
                                currentExercise.id
                            )
                            navController.popBackStack()
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Multi-part Problem Screen
            composable(
                route = Screen.Problem.route,
                arguments = listOf(
                    navArgument("problemId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val problemId = backStackEntry.arguments?.getString("problemId") ?: ""
                val appContainer = LocalAppContainer.current
                var problem by remember { mutableStateOf<Exercise.MultiPartProblem?>(null) }
                var aiAccess by remember { mutableStateOf<AiAccess?>(null) }

                LaunchedEffect(problemId) {
                    val found = findExercise(appContainer, problemId) as? Exercise.MultiPartProblem
                    aiAccess = loadAiAccess(appContainer.settingsRepository, appContainer.secretStore)
                    problem = found
                }

                val currentProblem = problem
                val access = aiAccess
                if (currentProblem != null && access != null) {
                    // Scoped to this back-stack entry: leaving the screen cancels the part timer.
                    val viewModel: MultiPartProblemViewModel = viewModel(
                        key = currentProblem.id,
                        factory = viewModelFactory {
                            initializer {
                                MultiPartProblemViewModel(
                                    problem = currentProblem,
                                    codeRunner = appContainer.languageSupport.runner,
                                    progressRecorder = appContainer.progressRecorder,
                                    aiTutor = appContainer.aiTutor,
                                    isInterviewModeDefault = access.interviewMode,
                                    hasApiKey = access.hasApiKey
                                )
                            }
                        }
                    )
                    val state by viewModel.uiState.collectAsState()
                    val learningFeedback = LocalLearningFeedback.current
                    LaunchedEffect(viewModel, learningFeedback) {
                        viewModel.feedbackEvents.collect { correct ->
                            learningFeedback.onAnswer(correct)
                        }
                    }

                    MultiPartProblemScreen(
                        state = state,
                        onCodeChange = viewModel::onCodeChange,
                        onSelectPart = viewModel::selectPart,
                        onRevealNextPart = viewModel::revealNextPart,
                        onRunClick = viewModel::runCode,
                        onCheckClick = viewModel::checkPart,
                        onToggleInterviewMode = viewModel::toggleInterviewMode,
                        onToggleTimer = viewModel::toggleTimer,
                        onResetTimer = viewModel::resetTimer,
                        onExplainMistakeClick = viewModel::askAiExplainMistake,
                        onAskHintClick = viewModel::askAiHint,
                        onReviewCodeClick = viewModel::askAiReview,
                        onDismissOutcome = viewModel::dismissOutcome,
                        onDismissAiDialog = viewModel::dismissAiDialog,
                        onNavigateBack = { navController.popBackStack() },
                        onFinish = {
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                COMPLETED_EXERCISE_KEY,
                                currentProblem.id
                            )
                            navController.popBackStack()
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Review Queue
            composable(Screen.Review.route) {
                ReviewScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Stats & Analytics
            composable(Screen.Stats.route) {
                StatsScreen()
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
