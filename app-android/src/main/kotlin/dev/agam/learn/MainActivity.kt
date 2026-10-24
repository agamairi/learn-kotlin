package dev.agam.learn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.agam.learn.ui.home.HomeScreen
import dev.agam.learn.ui.theme.LearnKotlinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LearnKotlinTheme {
                HomeScreen()
            }
        }
    }
}
