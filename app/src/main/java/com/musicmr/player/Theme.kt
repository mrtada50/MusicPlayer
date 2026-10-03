package com.musicmr.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0B12)
val Surface1 = Color(0xFF15151F)
val Violet = Color(0xFF8B5CF6)
val Cyan = Color(0xFF22D3EE)
val TelegramBlue = Color(0xFF229ED9)
const val TELEGRAM_URL = "https://t.me/musicmr1999"

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Violet,
        secondary = Cyan,
        background = Bg,
        surface = Surface1,
        onSurface = Color.White,
        onBackground = Color.White,
        surfaceVariant = Color(0xFF1E1E2C),
        onSurfaceVariant = Color(0xFFB4B4C4)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
