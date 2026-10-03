package com.musicmr.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val folders = remember(vm.allSongs) {
        vm.allSongs.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
    }
    var link by remember(vm.telegramUrl) { mutableStateOf(vm.telegramUrl) }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, null) }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "A quick scan runs automatically every time you open the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.rescan(true) },
                        enabled = !vm.scanning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (vm.scanning) "Scanning..." else "Deep scan now")
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Telegram link", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it },
                            singleLine = true,
                            placeholder = { Text("https://t.me/...") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { vm.saveTelegramLink(link) }) { Text("Save") }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Turn a folder off to hide its audio from the player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }
            items(folders, key = { it.first }) { (path, list) ->
                val hidden = path in vm.excluded
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        null,
                        tint = if (hidden) muted else Violet,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            path.substringAfterLast('/').ifEmpty { "/" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${list.size} songs • " + path.removePrefix("/storage/emulated/0/"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = muted
                        )
                    }
                    Switch(checked = !hidden, onCheckedChange = { vm.setFolderHidden(path, !it) })
                }
            }
        }
    }
}
