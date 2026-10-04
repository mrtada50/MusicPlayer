package com.musicmr.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ACT_TOGGLE = "com.musicmr.player.WIDGET_TOGGLE"
const val ACT_NEXT = "com.musicmr.player.WIDGET_NEXT"
const val ACT_PREV = "com.musicmr.player.WIDGET_PREV"
const val ACT_PLAY_SOURCE = "com.musicmr.player.WIDGET_PLAY_SOURCE"

object WidgetPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun saveNow(ctx: Context, title: String, artist: String, art: String, playing: Boolean) {
        sp(ctx).edit()
            .putString("now_title", title)
            .putString("now_artist", artist)
            .putString("now_art", art)
            .putBoolean("now_playing", playing)
            .apply()
    }

    fun setPlaying(ctx: Context, playing: Boolean) {
        sp(ctx).edit().putBoolean("now_playing", playing).apply()
    }

    fun title(ctx: Context): String = sp(ctx).getString("now_title", "") ?: ""
    fun artist(ctx: Context): String = sp(ctx).getString("now_artist", "") ?: ""
    fun art(ctx: Context): String = sp(ctx).getString("now_art", "") ?: ""
    fun playing(ctx: Context): Boolean = sp(ctx).getBoolean("now_playing", false)

    fun saveSource(ctx: Context, id: Int, kind: String, key: String, label: String) {
        sp(ctx).edit().putString("w$id", listOf(kind, key, label).joinToString("\u0001")).apply()
    }

    fun source(ctx: Context, id: Int): Triple<String, String, String>? {
        val raw = sp(ctx).getString("w$id", null) ?: return null
        val p = raw.split("\u0001")
        if (p.size < 3) return null
        return Triple(p[0], p[1], p[2])
    }

    fun removeSource(ctx: Context, id: Int) {
        sp(ctx).edit().remove("w$id").apply()
    }
}

object WidgetQueue {
    fun build(ctx: Context, kind: String, key: String): List<Song> {
        val store = Store(ctx)
        val raw = try {
            MusicRepository.query(ctx)
        } catch (e: Exception) {
            emptyList()
        }
        val excluded = store.excluded()
        val all = TitleParser.process(raw, store.smartTitles(), store.reverseOrder(), store.titleModes())
            .filter { it.folder !in excluded }
        val byId = all.associateBy { it.id }
        return when (kind) {
            "fav" -> {
                val f = store.favorites()
                all.filter { it.id in f }
            }
            "tag" -> store.tags()[key]?.ids.orEmpty().mapNotNull { byId[it] }
            "playlist" -> store.playlists()[key].orEmpty().mapNotNull { byId[it] }
            "artist" -> all.filter { it.artist == key }
            else -> all
        }
    }
}

object WidgetUpdater {
    private fun pi(ctx: Context, action: String, tag: String, extras: (Intent) -> Unit = {}): PendingIntent {
        val i = Intent(ctx, WidgetActionReceiver::class.java).setAction(action)
        i.data = Uri.parse("musicmr://widget/$tag/$action")
        extras(i)
        return PendingIntent.getBroadcast(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

    fun updateAll(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val pIds = mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidgetProvider::class.java))
            if (pIds.isNotEmpty()) mgr.updateAppWidget(pIds, playerViews(ctx))
            val sIds = mgr.getAppWidgetIds(ComponentName(ctx, SourceWidgetProvider::class.java))
            for (id in sIds) mgr.updateAppWidget(id, sourceViews(ctx, id))
        } catch (e: Exception) {
        }
    }

    private fun playerViews(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_player)
        val title = WidgetPrefs.title(ctx)
        val artist = WidgetPrefs.artist(ctx)
        v.setTextViewText(R.id.w_title, if (title.isBlank()) "Music MR" else title)
        v.setTextViewText(R.id.w_artist, if (title.isBlank()) "Tap play to shuffle your music" else artist)
        v.setImageViewResource(
            R.id.w_play,
            if (WidgetPrefs.playing(ctx)) R.drawable.ic_w_pause else R.drawable.ic_w_play
        )
        val bmp = loadArt(ctx, WidgetPrefs.art(ctx))
        if (bmp != null) v.setImageViewBitmap(R.id.w_art, bmp) else v.setImageViewResource(R.id.w_art, R.drawable.ic_w_note)
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
        v.setOnClickPendingIntent(R.id.w_play, pi(ctx, ACT_TOGGLE, "p"))
        v.setOnClickPendingIntent(R.id.w_next, pi(ctx, ACT_NEXT, "p"))
        v.setOnClickPendingIntent(R.id.w_prev, pi(ctx, ACT_PREV, "p"))
        return v
    }

    private fun sourceViews(ctx: Context, id: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_source)
        val src = WidgetPrefs.source(ctx, id) ?: Triple("all", "", "All songs")
        val kindLabel = when (src.first) {
            "fav" -> "Favorites"
            "tag" -> "Tag"
            "playlist" -> "Playlist"
            "artist" -> "Artist"
            else -> "Library"
        }
        v.setTextViewText(R.id.w_label, kindLabel)
        v.setTextViewText(R.id.w_name, src.third)
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
        v.setOnClickPendingIntent(R.id.w_play, pi(ctx, ACT_PLAY_SOURCE, "s$id-play") {
            it.putExtra("kind", src.first).putExtra("key", src.second).putExtra("shuffle", false)
        })
        v.setOnClickPendingIntent(R.id.w_shuffle, pi(ctx, ACT_PLAY_SOURCE, "s$id-shuffle") {
            it.putExtra("kind", src.first).putExtra("key", src.second).putExtra("shuffle", true)
        })
        return v
    }

    private fun loadArt(ctx: Context, uriStr: String): Bitmap? {
        if (uriStr.isBlank()) return null
        return try {
            val uri = Uri.parse(uriStr)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 320) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }
}

class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.updateAll(context)
    }
}

class SourceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.updateAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.removeSource(context, it) }
    }
}

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        try {
            app.startService(Intent(app, PlaybackService::class.java))
        } catch (e: Exception) {
        }
        val player = PlayerHolder.get(app)
        when (action) {
            ACT_NEXT -> if (player.mediaItemCount > 0) player.seekToNext()
            ACT_PREV -> if (player.mediaItemCount > 0) player.seekToPrevious()
            ACT_TOGGLE -> {
                if (player.mediaItemCount == 0) {
                    startSource(app, "all", "", true, goAsync())
                } else if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
            ACT_PLAY_SOURCE -> startSource(
                app,
                intent.getStringExtra("kind") ?: "all",
                intent.getStringExtra("key") ?: "",
                intent.getBooleanExtra("shuffle", false),
                goAsync()
            )
        }
    }

    private fun startSource(app: Context, kind: String, key: String, shuffle: Boolean, pending: PendingResult) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val songs = withContext(Dispatchers.IO) { WidgetQueue.build(app, kind, key) }
                if (songs.isNotEmpty()) {
                    val list = if (shuffle) songs.shuffled() else songs
                    val player = PlayerHolder.get(app)
                    player.setMediaItems(list.map { it.toMediaItem() }, 0, 0L)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
