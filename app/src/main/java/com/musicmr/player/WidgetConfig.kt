package com.musicmr.player

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            MusicTheme {
                Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
                    ConfigScreen { kind, key, label ->
                        WidgetPrefs.saveSource(this, widgetId, kind, key, label)
                        WidgetUpdater.updateAll(applicationContext)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(onPick: (String, String, String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { Store(ctx.applicationContext) }
    var artists by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        artists = withContext(Dispatchers.IO) {
            try {
                WidgetQueue.build(ctx.applicationContext, "all", "").map { it.artist }.distinct()
                    .sortedBy { it.lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    val tags = store.tags().keys.toList()
    val playlists = store.playlists().keys.toList()

    @Composable
    fun Header(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = Violet,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
        )
    }

    @Composable
    fun Option(text: String, onClick: () -> Unit) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }

    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(
            "Choose what this widget plays",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item { Header("Quick") }
            item { Option("All songs") { onPick("all", "", "All songs") } }
            item { Option("Favorites") { onPick("fav", "", "Favorites") } }
            if (tags.isNotEmpty()) {
                item { Header("Tags") }
                items(tags.size) { i -> Option(tags[i]) { onPick("tag", tags[i], tags[i]) } }
            }
            if (playlists.isNotEmpty()) {
                item { Header("Playlists") }
                items(playlists.size) { i -> Option(playlists[i]) { onPick("playlist", playlists[i], playlists[i]) } }
            }
            if (artists.isNotEmpty()) {
                item { Header("Artists") }
                items(artists.size) { i -> Option(artists[i]) { onPick("artist", artists[i], artists[i]) } }
            }
        }
    }
}
