package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HomeDashboardScreen
import com.example.ui.SpreadsheetScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: SpreadsheetViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == "EDITOR") {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            "EDITOR" -> SpreadsheetScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                            else -> HomeDashboardScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
