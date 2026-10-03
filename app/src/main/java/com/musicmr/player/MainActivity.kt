package com.musicmr.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        try {
            startService(Intent(this, PlaybackService::class.java))
        } catch (ex: Exception) {
        }
        setContent {
            MusicTheme {
                Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
                    val vm: PlayerViewModel = viewModel()
                    App(vm)
                }
            }
        }
    }
}
