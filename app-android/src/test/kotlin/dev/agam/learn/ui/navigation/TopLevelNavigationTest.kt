package dev.agam.learn.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TopLevelNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeShortcutThenOtherTabsReturnsToHomeAndKeepsReviewTabState() {
        compose.setContent {
            MaterialTheme {
                TopLevelNavigationHarness()
            }
        }

        compose.onNodeWithTag("current_route").assertTextEquals("Current route: home")
        compose.onNodeWithText("Open Review from Home").performClick()
        compose.onNodeWithTag("current_route").assertTextEquals("Current route: review")
        compose.onNodeWithText("Add review state").performClick()
        compose.onNodeWithText("Review state: 1").assertIsDisplayed()

        compose.onNodeWithText("Stats tab").performClick()
        compose.onNodeWithTag("current_route").assertTextEquals("Current route: stats")
        compose.onNodeWithText("Settings tab").performClick()
        compose.onNodeWithTag("current_route").assertTextEquals("Current route: settings")
        compose.onNodeWithText("Home tab").performClick()

        compose.onNodeWithTag("current_route").assertTextEquals("Current route: home")
        compose.onNodeWithText("Home destination").assertIsDisplayed()

        // Switching back to Review restores its own saved state without changing Home's root.
        compose.onNodeWithText("Review tab").performClick()
        compose.onNodeWithTag("current_route").assertTextEquals("Current route: review")
        compose.onNodeWithText("Review state: 1").assertIsDisplayed()

        compose.onNodeWithText("Home tab").performClick()
        compose.onNodeWithTag("current_route").assertTextEquals("Current route: home")
    }
}

@Composable
private fun TopLevelNavigationHarness() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Current route: $currentRoute", modifier = Modifier.testTag("current_route"))
        Row {
            TopLevelRouteButton(navController, "Home tab", Screen.Home.route)
            TopLevelRouteButton(navController, "Review tab", Screen.Review.route)
            TopLevelRouteButton(navController, "Stats tab", Screen.Stats.route)
            TopLevelRouteButton(navController, "Settings tab", Screen.Settings.route)
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.weight(1f)
        ) {
            composable(Screen.Home.route) {
                Column {
                    Text("Home destination")
                    Button(onClick = { navController.navigateToTopLevel(Screen.Review.route) }) {
                        Text("Open Review from Home")
                    }
                }
            }
            composable(Screen.Review.route) {
                var reviewState by rememberSaveable { mutableIntStateOf(0) }
                Column {
                    Text("Review destination")
                    Text("Review state: $reviewState")
                    Button(onClick = { reviewState++ }) {
                        Text("Add review state")
                    }
                }
            }
            composable(Screen.Stats.route) { Text("Stats destination") }
            composable(Screen.Settings.route) { Text("Settings destination") }
        }
    }
}

@Composable
private fun TopLevelRouteButton(
    navController: NavHostController,
    label: String,
    route: String
) {
    Button(onClick = { navController.navigateToTopLevel(route) }) {
        Text(label)
    }
}
