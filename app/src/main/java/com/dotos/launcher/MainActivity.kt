package com.dotos.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.designsystem.DotTheme
import com.dotos.feature.home.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = graph
        setContent {
            val settings by graph.settings.settingsOrNull.collectAsStateWithLifecycle()
            DotTheme {
                val s = settings
                if (s == null) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                } else {
                    HomeScreen(settings = s)
                }
            }
        }
    }
}
