package com.musicmr.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NameDialog(title: String, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = Color(0xFFFB7185)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PlaylistPickerDialog(
    vm: PlayerViewModel,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onDone: () -> Unit = onDismiss
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            "New playlist",
            onCreate = { n ->
                vm.createPlaylist(n)
                vm.addToPlaylist(n, songs)
                onDone()
            },
            onDismiss = { creating = false }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            title = { Text("Add to playlist") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "+ New playlist",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp)
                    )
                    vm.playlists.keys.forEach { name ->
                        Text(
                            name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.addToPlaylist(name, songs); onDone() }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun TagPickerDialog(
    vm: PlayerViewModel,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onDone: () -> Unit = onDismiss
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            "New tag",
            onCreate = { n ->
                vm.createTag(n)
                vm.addToTag(n, songs)
                onDone()
            },
            onDismiss = { creating = false }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            title = { Text("Add to tag") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "+ New tag",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp)
                    )
                    vm.tags.forEach { (name, t) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.addToTag(name, songs); onDone() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.size(12.dp).clip(CircleShape).background(Color(t.color)))
                            Spacer(Modifier.width(12.dp))
                            Text(name)
                        }
                    }
                }
            }
        )
    }
}
