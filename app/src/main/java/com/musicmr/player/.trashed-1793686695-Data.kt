package com.musicmr.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val artUri: Uri,
    val path: String,
    val dateAdded: Long
) {
    val folder: String get() = path.substringBeforeLast('/', "")
}

data class Detail(val kind: String, val key: String, val title: String, val subtitle: String)

data class TagData(val color: Int, val ids: List<Long>)

object MusicRepository {
    fun query(ctx: Context): List<Song> {
        val list = mutableListOf<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val sel = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.DURATION} >= 1000"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val artBase = Uri.parse("content://media/external/audio/albumart")
        ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, null, order)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val albumId = c.getLong(4)
                val artist = c.getString(2)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                val album = c.getString(3)?.takeIf { it.isNotBlank() } ?: "Unknown album"
                list.add(
                    Song(
                        id = id,
                        title = c.getString(1) ?: "Unknown",
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        duration = c.getLong(5),
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        artUri = ContentUris.withAppendedId(artBase, albumId),
                        path = c.getString(6) ?: "",
                        dateAdded = c.getLong(7)
                    )
                )
            }
        }
        return list
    }
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("store", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> =
        (sp.getStringSet("fav", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    fun saveFavorites(s: Set<Long>) {
        sp.edit().putStringSet("fav", s.map { it.toString() }.toSet()).apply()
    }

    fun playlists(): Map<String, List<Long>> {
        val result = linkedMapOf<String, List<Long>>()
        try {
            val o = JSONObject(sp.getString("pl", "{}") ?: "{}")
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val a = o.getJSONArray(k)
                result[k] = (0 until a.length()).map { a.getLong(it) }
            }
        } catch (ex: Exception) {
        }
        return result
    }

    fun tags(): Map<String, TagData> {
        val result = linkedMapOf<String, TagData>()
        try {
            val o = JSONObject(sp.getString("tags", "{}") ?: "{}")
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val t = o.getJSONObject(k)
                val a = t.getJSONArray("ids")
                result[k] = TagData(t.getInt("c"), (0 until a.length()).map { a.getLong(it) })
            }
        } catch (ex: Exception) {
        }
        return result
    }

    fun saveTags(m: Map<String, TagData>) {
        val o = JSONObject()
        m.forEach { (k, t) ->
            val arr = JSONArray()
            t.ids.forEach { arr.put(it) }
            val to = JSONObject()
            to.put("c", t.color)
            to.put("ids", arr)
            o.put(k, to)
        }
        sp.edit().putString("tags", o.toString()).apply()
    }

    fun excluded(): Set<String> = (sp.getStringSet("excl", emptySet()) ?: emptySet()).toSet()

    fun saveExcluded(s: Set<String>) {
        sp.edit().putStringSet("excl", s.toSet()).apply()
    }

    fun telegramUrl(): String = sp.getString("tg", null) ?: TELEGRAM_URL

    fun saveTelegramUrl(u: String) {
        sp.edit().putString("tg", u).apply()
    }

    fun attempted(): Set<String> = (sp.getStringSet("attempted", emptySet()) ?: emptySet()).toSet()

    fun saveAttempted(s: Set<String>) {
        sp.edit().putStringSet("attempted", s.toSet()).apply()
    }

    fun sortMode(): Int = sp.getInt("sort", 0)

    fun saveSortMode(i: Int) {
        sp.edit().putInt("sort", i).apply()
    }

    fun playCounts(): Map<Long, Int> {
        val result = HashMap<Long, Int>()
        try {
            val o = JSONObject(sp.getString("plays", "{}") ?: "{}")
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toLongOrNull() ?: continue
                result[id] = o.getInt(k)
            }
        } catch (ex: Exception) {
        }
        return result
    }

    fun savePlayCounts(m: Map<Long, Int>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k.toString(), v) }
        sp.edit().putString("plays", o.toString()).apply()
    }

    fun savePlaylists(m: Map<String, List<Long>>) {
        val o = JSONObject()
        m.forEach { (k, v) ->
            val arr = JSONArray()
            v.forEach { arr.put(it) }
            o.put(k, arr)
        }
        sp.edit().putString("pl", o.toString()).apply()
    }
}
