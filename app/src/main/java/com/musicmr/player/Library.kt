package com.musicmr.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val tabs = listOf(
    "Songs" to Icons.Rounded.MusicNote,
    "Albums" to Icons.Rounded.Album,
    "Artists" to Icons.Rounded.Person,
    "Favorites" to Icons.Rounded.Favorite,
    "Playlists" to Icons.Rounded.QueueMusic
)

@Composable
fun App(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r[perm] == true
        if (granted) vm.load()
    }
    val request: () -> Unit = {
        val perms = mutableListOf(perm)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        launcher.launch(perms.toTypedArray())
    }
    LaunchedEffect(Unit) { if (granted) vm.load() else request() }
    if (!granted) {
        PermissionScreen(onGrant = request)
    } else {
        Library(vm)
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Violet, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("Music MR", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow access to your music to get started",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Allow access") }
    }
}

@Composable
fun Library(vm: PlayerViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pickSong by remember { mutableStateOf<Song?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Song?>(null) }
    val uriHandler = LocalUriHandler.current

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && detail != null) { detail = null }
    BackHandler(enabled = !showPlayer && detail == null && searching) {
        searching = false
        query = ""
    }

    val songs = vm.songs
    val d = detail
    val q = query.trim()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                Column {
                    MiniPlayer(vm) { showPlayer = true }
                    NavigationBar(containerColor = Surface1) {
                        tabs.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i && d == null,
                                onClick = { tab = i; detail = null },
                                icon = { Icon(icon, null) },
                                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Violet.copy(alpha = 0.35f),
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = Color(0xFF9A9AAE),
                                    unselectedTextColor = Color(0xFF9A9AAE)
                                )
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (d != null) {
                        IconButton(onClick = { detail = null }) { Icon(Icons.Rounded.ArrowBack, null) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                d.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                d.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search songs, artists, albums") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Rounded.Close, null) }
                    } else {
                        Text(
                            "Music MR",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, null) }
                        Pill(Icons.Rounded.Send, "Channel", TelegramBlue) { uriHandler.openUri(TELEGRAM_URL) }
                    }
                }

                val searchResults = if (q.isNotEmpty()) songs.filter {
                    it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
                } else null

                when {
                    d != null -> SongList(d.songs, vm, true) { pickSong = it }
                    searchResults != null -> SongList(searchResults, vm, false) { pickSong = it }
                    tab == 0 -> if (!vm.loaded) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        SongList(songs, vm, true) { pickSong = it }
                    }
                    tab == 1 -> AlbumsGrid(songs) { detail = it }
                    tab == 2 -> ArtistsList(songs) { detail = it }
                    tab == 3 -> SongList(
                        songs.filter { it.id in vm.favorites }, vm, true, "No favorites yet"
                    ) { pickSong = it }
                    else -> PlaylistsTab(vm, songs, onOpen = { detail = it }, onNew = { pending = null; newPlaylist = true })
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlaying(vm) { showPlayer = false }
        }
    }

    pickSong?.let { s ->
        AlertDialog(
            onDismissRequest = { pickSong = null },
            confirmButton = { TextButton(onClick = { pickSong = null }) { Text("Cancel") } },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    vm.playlists.keys.forEach { name ->
                        TextButton(onClick = { vm.addToPlaylist(name, s); pickSong = null }) { Text(name) }
                    }
                    TextButton(onClick = { pending = s; pickSong = null; newPlaylist = true }) {
                        Text("+ New playlist")
                    }
                }
            }
        )
    }

    if (newPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newPlaylist = false; pending = null },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        vm.createPlaylist(n)
                        pending?.let { vm.addToPlaylist(n, it) }
                    }
                    newPlaylist = false
                    pending = null
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { newPlaylist = false; pending = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SongList(
    list: List<Song>,
    vm: PlayerViewModel,
    showButtons: Boolean,
    emptyText: String = "No songs found",
    onMore: (Song) -> Unit
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val currentId = vm.current?.id
    LazyColumn(Modifier.fillMaxSize()) {
        if (showButtons) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { vm.playAll(list) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play all")
                    }
                    FilledTonalButton(onClick = { vm.shuffleAll(list) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
        }
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            SongRow(
                song = s,
                playing = s.id == currentId,
                fav = s.id in vm.favorites,
                onClick = { vm.playQueue(list, i) },
                onFav = { vm.toggleFav(s) },
                onPlayNext = { vm.playNext(s) },
                onQueue = { vm.addToQueue(s) },
                onAddToPlaylist = { onMore(s) }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun AlbumsGrid(songs: List<Song>, onOpen: (Detail) -> Unit) {
    val albums = remember(songs) {
        songs.groupBy { it.albumId }.values.toList().sortedBy { it[0].album.lowercase() }
    }
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it[0].albumId }) { list ->
            Column(Modifier.clickable { onOpen(Detail(list[0].album, list[0].artist, list)) }) {
                Art(list[0].artUri, Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    list[0].album,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    list[0].artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ArtistsList(songs: List<Song>, onOpen: (Detail) -> Unit) {
    val artists = remember(songs) {
        songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }
    }
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No artists", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.first }) { (name, list) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(Detail(name, "${list.size} songs", list)) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Violet, Cyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${list.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(vm: PlayerViewModel, songs: List<Song>, onOpen: (Detail) -> Unit, onNew: () -> Unit) {
    val byId = remember(songs) { songs.associateBy { it.id } }
    Column(Modifier.fillMaxSize()) {
        Button(onClick = onNew, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("New playlist")
        }
        if (vm.playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.playlists.keys.toList(), key = { it }) { name ->
                    val list = vm.playlists[name].orEmpty().mapNotNull { byId[it] }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(Detail(name, "${list.size} songs", list)) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Art(list.firstOrNull()?.artUri, Modifier.size(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${list.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.deletePlaylist(name) }) {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val s = vm.current ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1B2A))
            .clickable(onClick = onOpen)
    ) {
        LinearProgressIndicator(
            progress = { if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Violet,
            trackColor = Color.Transparent
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Art(s.artUri, Modifier.size(44.dp), 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    s.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Rounded.SkipNext, null) }
        }
    }
}
