package dev.agam.learn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.agam.learn.di.LocalAppContainer
import dev.agam.learn.ui.navigation.LearnAppNavHost
import dev.agam.learn.ui.experience.LearningExperienceProvider
import dev.agam.learn.ui.theme.LearnKotlinTheme

/**
 * Main activity entry point hosting the application navigation graph.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as LearnApplication).appContainer

        setContent {
            LearnKotlinTheme {
                CompositionLocalProvider(LocalAppContainer provides appContainer) {
                    LearningExperienceProvider(
                        settingsRepository = appContainer.settingsRepository
                    ) {
                        LearnAppNavHost()
                    }
                }
            }
        }
    }
}
