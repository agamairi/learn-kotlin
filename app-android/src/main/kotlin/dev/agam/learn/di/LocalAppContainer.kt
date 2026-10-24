package dev.agam.learn.di

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for accessing the [AppContainer] throughout the Compose hierarchy.
 */
val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer has not been provided in this CompositionLocalScope")
}
