package com.clutchalarm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.ui.GameApp
import com.clutchalarm.app.ui.GameViewModel

class MainActivity : ComponentActivity() {
    private val gameViewModel by viewModels<GameViewModel> {
        viewModelFactory {
            initializer { GameViewModel(MockGameRepository(application.assets)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GameApp(gameViewModel)
        }
    }
}
