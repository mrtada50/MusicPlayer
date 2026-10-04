package com.musicmr.player

import android.app.Application
import android.database.ContentObserver
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val store = Store(ctx)
    val player: ExoPlayer = PlayerHolder.get(ctx)
    private var byId: Map<Long, Song> = emptyMap()

    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var favorites by mutableStateOf<Set<Long>>(emptySet())
        private set
    var playlists by mutableStateOf<Map<String, List<Long>>>(emptyMap())
        private set
    var current by mutableStateOf<Song?>(null)
        private set
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var sleepLeft by mutableIntStateOf(0)
        private set

    var tags by mutableStateOf<Map<String, TagData>>(emptyMap())
        private set
    var pendingDelete: List<Song> = emptyList()
    var allSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var excluded by mutableStateOf<Set<String>>(emptySet())
        private set
    var sortMode by mutableIntStateOf(0)
        private set
    var playCounts by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set
    var scanning by mutableStateOf(false)
        private set
    private var lastScan = 0L
    private var countedId: Long? = null
    var telegramUrl by mutableStateOf(TELEGRAM_URL)
        private set
    private var attempted: Set<String> = emptySet()
    private var rawSongs: List<Song> = emptyList()
    var smartTitles by mutableStateOf(true)
        private set
    var reverseOrder by mutableStateOf(false)
        private set
    var arabicSong by mutableStateOf(true)
        private set
    var titleModes by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var edits by mutableStateOf<Map<String, Pair<String, String>>>(emptyMap())
        private set

    private var reloadJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleReload()
        }
    }

    // Audio effects
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var effSession = -1
    private var savedLevels: List<Int> = emptyList()
    var eqLevels by mutableStateOf<List<Int>>(emptyList())
        private set
    var eqFreqs by mutableStateOf<List<Int>>(emptyList())
        private set
    var eqMin by mutableIntStateOf(-1500)
        private set
    var eqMax by mutableIntStateOf(1500)
        private set
    var presets by mutableStateOf<List<String>>(emptyList())
        private set
    var bassStrength by mutableIntStateOf(0)
        private set

    private var sleepJob: Job? = null

    init {
        favorites = store.favorites()
        playlists = store.playlists()
        tags = store.tags()
        excluded = store.excluded()
        sortMode = store.sortMode()
        playCounts = store.playCounts()
        telegramUrl = store.telegramUrl()
        attempted = store.attempted()
        smartTitles = store.smartTitles()
        reverseOrder = store.reverseOrder()
        arabicSong = store.arabicSong()
        titleModes = store.titleModes()
        edits = store.edits()
        isPlaying = player.isPlaying
        shuffle = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        speed = player.playbackParameters.speed
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@PlayerViewModel.isPlaying = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                countedId = null
                syncCurrent()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncCurrent()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                shuffle = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                this@PlayerViewModel.repeatMode = repeatMode
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                speed = playbackParameters.speed
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupEffects()
            }
        })
        viewModelScope.launch {
            while (true) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
                if (player.isPlaying && position > 10_000L) {
                    val id = current?.id
                    if (id != null && id != countedId) {
                        countedId = id
                        playCounts = playCounts + (id to ((playCounts[id] ?: 0) + 1))
                        store.savePlayCounts(playCounts)
                    }
                }
                delay(400)
            }
        }
        setupEffects()
        try {
            ctx.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (ex: Exception) {
        }
    }

    private fun scheduleReload() {
        if (!loaded) return
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(1000)
            load()
        }
    }

    override fun onCleared() {
        try {
            ctx.contentResolver.unregisterContentObserver(observer)
        } catch (ex: Exception) {
        }
        super.onCleared()
    }

    // ---------- Library ----------
    fun load() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    MusicRepository.query(ctx)
                } catch (ex: Exception) {
                    emptyList()
                }
            }
            rawSongs = list
            recomputeTitles()
            loaded = true
        }
    }

    private fun recomputeTitles() {
        allSongs = TitleParser.process(rawSongs, smartTitles, reverseOrder, titleModes, edits, arabicSong)
        byId = allSongs.associateBy { it.id }
        applyFilter()
    }

    fun changeSmartTitles(b: Boolean) {
        smartTitles = b
        store.saveSmartTitles(b)
        recomputeTitles()
    }

    fun changeReverseOrder(b: Boolean) {
        reverseOrder = b
        store.saveReverseOrder(b)
        recomputeTitles()
    }

    fun changeArabicSong(b: Boolean) {
        arabicSong = b
        store.saveArabicSong(b)
        recomputeTitles()
    }

    fun changeTitleMode(song: Song, mode: Int) {
        titleModes = if (mode == 0) titleModes - song.path else titleModes + (song.path to mode)
        store.saveTitleModes(titleModes)
        recomputeTitles()
    }

    fun saveEdit(song: Song, title: String, artist: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        val a = artist.trim().ifEmpty { "Unknown artist" }
        edits = edits + (song.path to Pair(t, a))
        store.saveEdits(edits)
        recomputeTitles()
    }

    fun clearEdit(song: Song) {
        edits = edits - song.path
        store.saveEdits(edits)
        recomputeTitles()
    }

    private fun applyFilter() {
        songs = allSongs.filter { it.folder !in excluded }
        syncCurrent()
    }

    private fun toItem(s: Song): MediaItem = s.toMediaItem()

    private fun syncCurrent() {
        val n = player.mediaItemCount
        queue = (0 until n).mapNotNull { i ->
            player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { byId[it] }
        }
        current = player.currentMediaItem?.mediaId?.toLongOrNull()?.let { byId[it] }
    }

    // ---------- Playback ----------
    fun playQueue(list: List<Song>, index: Int) {
        if (list.isEmpty()) return
        player.setMediaItems(list.map { toItem(it) }, index.coerceIn(0, list.size - 1), 0L)
        player.prepare()
        player.play()
    }

    fun playAll(list: List<Song>) {
        player.shuffleModeEnabled = false
        playQueue(list, 0)
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        player.shuffleModeEnabled = true
        playQueue(list, Random.nextInt(list.size))
    }

    fun playIndex(i: Int) {
        player.seekToDefaultPosition(i)
        player.play()
    }

    fun playNext(song: Song) {
        if (player.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
        } else {
            player.addMediaItem(player.currentMediaItemIndex + 1, toItem(song))
        }
    }

    fun addToQueue(song: Song) {
        if (player.mediaItemCount == 0) playQueue(listOf(song), 0) else player.addMediaItem(toItem(song))
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.mediaItemCount == 0 && songs.isNotEmpty()) playQueue(songs, 0) else player.play()
        }
    }

    fun next() = player.seekToNext()
    fun previous() = player.seekToPrevious()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun changeSpeed(s: Float) = player.setPlaybackSpeed(s)

    // ---------- Sleep timer ----------
    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepLeft = 0
        if (minutes <= 0) return
        sleepJob = viewModelScope.launch {
            var left = minutes * 60
            while (left > 0) {
                sleepLeft = left
                delay(1000)
                left--
            }
            sleepLeft = 0
            player.pause()
        }
    }

    // ---------- Favorites & playlists ----------
    fun toggleFav(song: Song) {
        favorites = if (song.id in favorites) favorites - song.id else favorites + song.id
        store.saveFavorites(favorites)
    }

    fun createPlaylist(name: String) {
        if (name in playlists) return
        playlists = playlists + (name to emptyList())
        store.savePlaylists(playlists)
    }

    fun addToPlaylist(name: String, list: List<Song>) {
        val old = playlists[name].orEmpty()
        val merged = (old + list.map { it.id }).distinct()
        playlists = playlists + (name to merged)
        store.savePlaylists(playlists)
    }

    fun removeFromPlaylist(name: String, ids: Set<Long>) {
        val old = playlists[name] ?: return
        playlists = playlists + (name to old.filter { it !in ids })
        store.savePlaylists(playlists)
    }

    fun addFavorites(list: List<Song>) {
        favorites = favorites + list.map { it.id }
        store.saveFavorites(favorites)
    }

    // ---------- Tags ----------
    fun createTag(name: String) {
        val n = name.trim()
        if (n.isEmpty() || n in tags) return
        val c = TagColors[tags.size % TagColors.size].toArgb()
        tags = tags + (n to TagData(c, emptyList()))
        store.saveTags(tags)
    }

    fun addToTag(name: String, list: List<Song>) {
        val t = tags[name] ?: return
        tags = tags + (name to t.copy(ids = (t.ids + list.map { it.id }).distinct()))
        store.saveTags(tags)
    }

    fun removeFromTag(name: String, ids: Set<Long>) {
        val t = tags[name] ?: return
        tags = tags + (name to t.copy(ids = t.ids.filter { it !in ids }))
        store.saveTags(tags)
    }

    fun deleteTag(name: String) {
        tags = tags - name
        store.saveTags(tags)
    }

    // ---------- Delete ----------
    fun finishDelete(removed: List<Song>) {
        val ids = removed.map { it.id }.toSet()
        if (ids.isEmpty()) return
        for (i in player.mediaItemCount - 1 downTo 0) {
            val id = player.getMediaItemAt(i).mediaId.toLongOrNull()
            if (id != null && id in ids) player.removeMediaItem(i)
        }
        favorites = favorites - ids
        store.saveFavorites(favorites)
        playlists = playlists.mapValues { (_, v) -> v.filter { it !in ids } }
        store.savePlaylists(playlists)
        tags = tags.mapValues { (_, t) -> t.copy(ids = t.ids.filter { it !in ids }) }
        store.saveTags(tags)
        load()
    }

    fun deletePlaylist(name: String) {
        playlists = playlists - name
        store.savePlaylists(playlists)
    }

    // ---------- Sorting, hidden folders, scanning ----------
    fun setSort(mode: Int) {
        sortMode = mode
        store.saveSortMode(mode)
    }

    fun sortSongs(list: List<Song>): List<Song> = when (sortMode) {
        0 -> list.sortedBy { it.title.lowercase() }
        1 -> list.sortedBy { it.dateAdded }
        2 -> list.sortedByDescending { it.dateAdded }
        3 -> list.sortedWith(
            compareByDescending<Song> { playCounts[it.id] ?: 0 }.thenBy { it.title.lowercase() }
        )
        else -> list.sortedWith(
            compareByDescending<Song> { it.id in favorites }.thenByDescending { it.dateAdded }
        )
    }

    fun setFolderHidden(folder: String, hidden: Boolean) {
        excluded = if (hidden) excluded + folder else excluded - folder
        store.saveExcluded(excluded)
        applyFilter()
    }

    fun saveTelegramLink(raw: String) {
        var u = raw.trim()
        if (u.isEmpty()) {
            u = TELEGRAM_URL
        } else if (u.startsWith("@")) {
            u = "https://t.me/" + u.removePrefix("@")
        } else if ("://" !in u) {
            u = if (u.startsWith("t.me/") || u.startsWith("telegram.me/")) "https://$u" else "https://t.me/$u"
        }
        telegramUrl = u
        store.saveTelegramUrl(u)
    }

    fun onResume() {
        if (loaded) rescan(false)
    }

    fun rescan(deep: Boolean) {
        if (scanning) return
        scanning = true
        val known = allSongs.map { it.path }.toSet() + attempted
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { findNewAudio(known, deep) }
            if (files.isNotEmpty()) {
                try {
                    MediaScannerConnection.scanFile(ctx, files.toTypedArray(), null, null)
                } catch (ex: Exception) {
                }
                attempted = (attempted + files).toList().takeLast(3000).toSet()
                store.saveAttempted(attempted)
                delay(1200)
            }
            load()
            scanning = false
        }
    }

    private fun findNewAudio(known: Set<String>, deep: Boolean): List<String> {
        val found = mutableListOf<String>()
        try {
            val roots = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                File(Environment.getExternalStorageDirectory(), "Telegram")
            )
            val exts = setOf("mp3", "m4a", "aac", "ogg", "opus", "oga", "wav", "flac", "amr")
            val depth = if (deep) 6 else 3
            val limit = if (deep) 20000 else 3000
            val deadline = System.currentTimeMillis() + (if (deep) 6000L else 700L)
            var visited = 0
            for (root in roots) {
                if (!root.exists()) continue
                for (f in root.walkTopDown().maxDepth(depth)) {
                    visited++
                    if (visited > limit || System.currentTimeMillis() > deadline) return found
                    if (f.isFile && f.extension.lowercase() in exts && f.absolutePath !in known) {
                        found.add(f.absolutePath)
                        if (found.size >= 1500) return found
                    }
                }
            }
        } catch (ex: Exception) {
        }
        return found
    }

    // ---------- Equalizer ----------
    fun setupEffects() {
        val sid = player.audioSessionId
        if (sid == C.AUDIO_SESSION_ID_UNSET || sid == 0 || sid == effSession) return
        try {
            eq?.release()
            bass?.release()
            val z = Equalizer(0, sid)
            z.setEnabled(true)
            val n = z.numberOfBands.toInt()
            val range = z.bandLevelRange
            eqMin = range[0].toInt()
            eqMax = range[1].toInt()
            eqFreqs = (0 until n).map { z.getCenterFreq(it.toShort()) / 1000 }
            if (savedLevels.size == n) {
                savedLevels.forEachIndexed { i, l -> z.setBandLevel(i.toShort(), l.toShort()) }
            }
            eqLevels = (0 until n).map { z.getBandLevel(it.toShort()).toInt() }
            presets = (0 until z.numberOfPresets.toInt()).map { z.getPresetName(it.toShort()) }
            eq = z
            effSession = sid
        } catch (ex: Exception) {
        }
        try {
            val b = BassBoost(0, sid)
            b.setEnabled(true)
            b.setStrength(bassStrength.toShort())
            bass = b
        } catch (ex: Exception) {
        }
    }

    fun setBand(i: Int, level: Int) {
        try {
            eq?.setBandLevel(i.toShort(), level.toShort())
        } catch (ex: Exception) {
        }
        eqLevels = eqLevels.toMutableList().also { if (i in it.indices) it[i] = level }
        savedLevels = eqLevels
    }

    fun usePreset(i: Int) {
        val z = eq ?: return
        try {
            z.usePreset(i.toShort())
            eqLevels = (0 until z.numberOfBands.toInt()).map { z.getBandLevel(it.toShort()).toInt() }
            savedLevels = eqLevels
        } catch (ex: Exception) {
        }
    }

    fun setBass(v: Int) {
        bassStrength = v
        try {
            bass?.setStrength(v.toShort())
        } catch (ex: Exception) {
        }
    }
}
