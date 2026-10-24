package dev.agam.learn

import android.app.Application
import dev.agam.learn.di.AppContainer
import dev.agam.learn.di.DefaultAppContainer

/**
 * Custom application class initializing the top-level [AppContainer].
 */
class LearnApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}
