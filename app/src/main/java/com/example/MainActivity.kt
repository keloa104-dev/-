package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.PuckPreferences
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.SmartPuckTheme

class MainActivity : ComponentActivity() {
    private lateinit var prefs: PuckPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PuckPreferences(this)
        enableEdgeToEdge()

        setContent {
            val appTheme by prefs.appThemeState.collectAsState()
            val useDark = when (appTheme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            SmartPuckTheme(darkTheme = useDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(prefs = prefs)
                }
            }
        }
    }
}

