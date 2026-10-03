package com.musicmr.player

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
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
    "Library" to Icons.Rounded.QueueMusic
)

class SongActions(
    val onPlaylist: (Song) -> Unit,
    val onTag: (Song) -> Unit,
    val onDelete: (Song) -> Unit,
    val onToggle: (Song) -> Unit
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
        if (Build.VERSION.SDK_INT <= 28) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var playlistFor by remember { mutableStateOf<List<Song>?>(null) }
    var tagFor by remember { mutableStateOf<List<Song>?>(null) }
    var deleteFor by remember { mutableStateOf<List<Song>?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    val holder = remember { arrayOfNulls<ActivityResultLauncher<IntentSenderRequest>>(1) }
    val launchSender: (IntentSender) -> Unit = { sender ->
        holder[0]?.launch(IntentSenderRequest.Builder(sender).build())
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        DeleteHelper.onResult(ctx, vm, r.resultCode == Activity.RESULT_OK, launchSender)
    }
    holder[0] = deleteLauncher

    val songs = remember(vm.songs, vm.sortMode, vm.favorites) { vm.sortSongs(vm.songs) }
    val byId = remember(songs) { songs.associateBy { it.id } }
    val d = detail
    val q = query.trim()
    val selecting = selected.isNotEmpty()
    val selectedSongs = remember(selected, byId) { selected.mapNotNull { byId[it] } }

    val detailSongs = remember(d, songs, vm.playlists, vm.tags) {
        if (d == null) emptyList() else when (d.kind) {
            "album" -> songs.filter { it.albumId.toString() == d.key }
            "artist" -> songs.filter { it.artist == d.key }
            "playlist" -> vm.playlists[d.key].orEmpty().mapNotNull { byId[it] }
            "tag" -> vm.tags[d.key]?.ids.orEmpty().mapNotNull { byId[it] }
            else -> emptyList()
        }
    }
    val favSongs = remember(songs, vm.favorites) { songs.filter { it.id in vm.favorites } }
    val searchResults: List<Song>? = remember(songs, q) {
        if (q.isEmpty()) null else songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }
    val visibleList: List<Song> = when {
        d != null -> detailSongs
        searchResults != null -> searchResults
        tab == 0 -> songs
        tab == 3 -> favSongs
        else -> emptyList()
    }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && selecting) { selected = emptySet() }
    BackHandler(enabled = !showPlayer && !selecting && d != null) { detail = null }
    BackHandler(enabled = !showPlayer && !selecting && d == null && searching) {
        searching = false
        query = ""
    }
    BackHandler(enabled = showSettings) { showSettings = false }

    val actions = SongActions(
        onPlaylist = { playlistFor = listOf(it) },
        onTag = { tagFor = listOf(it) },
        onDelete = { deleteFor = listOf(it) },
        onToggle = { s -> selected = if (s.id in selected) selected - s.id else selected + s.id }
    )

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
                                onClick = { tab = i; detail = null; searching = false; query = ""; selected = emptySet() },
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
                if (selecting) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selected = emptySet() }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Close, null)
                        }
                        Text(
                            "${selected.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        IconButton(
                            onClick = { selected = visibleList.map { it.id }.toSet() },
                            modifier = Modifier.size(40.dp)
                        ) { Icon(Icons.Rounded.SelectAll, null) }
                        IconButton(
                            onClick = { vm.addFavorites(selectedSongs); selected = emptySet() },
                            modifier = Modifier.size(40.dp)
                        ) { Icon(Icons.Rounded.Favorite, null) }
                        IconButton(onClick = { playlistFor = selectedSongs }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.PlaylistAdd, null)
                        }
                        IconButton(onClick = { tagFor = selectedSongs }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Label, null)
                        }
                        if (d != null && (d.kind == "playlist" || d.kind == "tag")) {
                            IconButton(
                                onClick = {
                                    if (d.kind == "playlist") vm.removeFromPlaylist(d.key, selected)
                                    else vm.removeFromTag(d.key, selected)
                                    selected = emptySet()
                                },
                                modifier = Modifier.size(40.dp)
                            ) { Icon(Icons.Rounded.RemoveCircleOutline, null) }
                        }
                        IconButton(onClick = { deleteFor = selectedSongs }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFB7185))
                        }
                    }
                } else {
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
                                    if (d.kind == "playlist" || d.kind == "tag") "${detailSongs.size} songs" else d.subtitle,
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
                            IconButton(onClick = { searching = false; query = "" }) {
                                Icon(Icons.Rounded.Close, null)
                            }
                        } else {
                            Text(
                                "Music MR",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, null) }
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, null) }
                            Pill(Icons.Rounded.Send, "Channel", TelegramBlue) {
                                runCatching { uriHandler.openUri(vm.telegramUrl) }
                            }
                        }
                    }
                }

                val contentKey = when {
                    d != null -> "d:${d.kind}:${d.key}"
                    searchResults != null -> "search"
                    else -> "tab$tab"
                }
                AnimatedContent(
                    targetState = contentKey,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "content"
                ) { key ->
                    when {
                        key.startsWith("d:") -> SongList(detailSongs, vm, true, selected, actions)
                        key == "search" -> SongList(searchResults.orEmpty(), vm, false, selected, actions)
                        key == "tab0" -> if (!vm.loaded) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            SongList(songs, vm, true, selected, actions, onSort = { showSort = true })
                        }
                        key == "tab1" -> AlbumsGrid(songs) { detail = it }
                        key == "tab2" -> ArtistsList(songs) { detail = it }
                        key == "tab3" -> SongList(favSongs, vm, true, selected, actions, "No favorites yet", onSort = { showSort = true })
                        else -> CollectionsTab(vm) { detail = it }
                    }
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

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            SettingsScreen(vm) { showSettings = false }
        }
    }

    playlistFor?.let { list ->
        PlaylistPickerDialog(
            vm, list,
            onDismiss = { playlistFor = null },
            onDone = { playlistFor = null; selected = emptySet() }
        )
    }
    tagFor?.let { list ->
        TagPickerDialog(
            vm, list,
            onDismiss = { tagFor = null },
            onDone = { tagFor = null; selected = emptySet() }
        )
    }
    deleteFor?.let { list ->
        ConfirmDialog(
            title = if (list.size == 1) "Delete this song?" else "Delete ${list.size} songs?",
            text = "The files will be permanently deleted from your device.",
            confirmLabel = "Delete",
            onConfirm = {
                deleteFor = null
                selected = emptySet()
                DeleteHelper.start(ctx, vm, list, launchSender)
            },
            onDismiss = { deleteFor = null }
        )
    }
    if (showSort) {
        ChoiceDialog(
            "Sort by",
            listOf(
                "Name",
                "Download date (oldest first)",
                "Newest first",
                "Most played",
                "Favorites first, then newest"
            ),
            vm.sortMode,
            { vm.setSort(it) },
            { showSort = false }
        )
    }
}

@Composable
fun SongList(
    list: List<Song>,
    vm: PlayerViewModel,
    showButtons: Boolean,
    selected: Set<Long>,
    actions: SongActions,
    emptyText: String = "No songs found",
    onSort: (() -> Unit)? = null
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val currentId = vm.current?.id
    val selecting = selected.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        if (showButtons) {
            item(key = "buttons", contentType = "buttons") {
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
                    if (onSort != null) {
                        IconButton(onClick = onSort) { Icon(Icons.Rounded.Sort, null) }
                    }
                }
            }
        }
        itemsIndexed(list, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { i, s ->
            SongRow(
                song = s,
                playing = s.id == currentId,
                fav = s.id in vm.favorites,
                selected = s.id in selected,
                selecting = selecting,
                onClick = { if (selecting) actions.onToggle(s) else vm.playQueue(list, i) },
                onLongClick = { actions.onToggle(s) },
                onFav = { vm.toggleFav(s) },
                onPlayNext = { vm.playNext(s) },
                onQueue = { vm.addToQueue(s) },
                onPlaylist = { actions.onPlaylist(s) },
                onTag = { actions.onTag(s) },
                onDelete = { actions.onDelete(s) }
            )
        }
        item(key = "end", contentType = "end") { Spacer(Modifier.height(8.dp)) }
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
            Column(
                Modifier.clickable {
                    onOpen(Detail("album", list[0].albumId.toString(), list[0].album, list[0].artist))
                }
            ) {
                Art(list[0].artUri, Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
                Spacer(Modifier.height(8.dp))
                Text(list[0].album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
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
                    .clickable { onOpen(Detail("artist", name, name, "${list.size} songs")) }
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
fun CollectionsTab(vm: PlayerViewModel, onOpen: (Detail) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegChip("Playlists", mode == 0) { mode = 0 }
            SegChip("Tags", mode == 1) { mode = 1 }
            Spacer(Modifier.weight(1f))
            Pill(Icons.Rounded.Add, "New", Violet) { creating = true }
        }
        if (mode == 0) {
            if (vm.playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No playlists yet", color = muted)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.playlists.keys.toList(), key = { it }) { name ->
                        val count = vm.playlists[name].orEmpty().size
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(Detail("playlist", name, name, "$count songs")) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Violet.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.QueueMusic, null, tint = Violet) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text("$count songs", style = MaterialTheme.typography.bodySmall, color = muted)
                            }
                            IconButton(onClick = { vm.deletePlaylist(name) }) {
                                Icon(Icons.Rounded.Delete, null, tint = muted)
                            }
                        }
                    }
                }
            }
        } else {
            if (vm.tags.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tags yet. Create one, then add songs with one tap.", color = muted, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.tags.keys.toList(), key = { it }) { name ->
                        val t = vm.tags[name]
                        val c = Color(t?.color ?: 0xFF8B5CF6.toInt())
                        val count = t?.ids?.size ?: 0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(Detail("tag", name, name, "$count songs")) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Label, null, tint = c) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text("$count songs", style = MaterialTheme.typography.bodySmall, color = muted)
                            }
                            IconButton(onClick = { vm.deleteTag(name) }) {
                                Icon(Icons.Rounded.Delete, null, tint = muted)
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog(
            if (mode == 0) "New playlist" else "New tag",
            onCreate = { n ->
                if (mode == 0) vm.createPlaylist(n) else vm.createTag(n)
                creating = false
            },
            onDismiss = { creating = false }
        )
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
