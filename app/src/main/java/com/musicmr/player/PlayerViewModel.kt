package com.musicmr.player

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
        isPlaying = player.isPlaying
        shuffle = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        speed = player.playbackParameters.speed
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@PlayerViewModel.isPlaying = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
            songs = list
            byId = list.associateBy { it.id }
            loaded = true
            syncCurrent()
        }
    }

    private fun toItem(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title)
                .setArtist(s.artist)
                .setAlbumTitle(s.album)
                .setArtworkUri(s.artUri)
                .build()
        )
        .build()

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
