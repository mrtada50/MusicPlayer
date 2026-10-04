package com.musicmr.player

object TitleParser {
    private val sep = Regex("\\s+[-\u2013\u2014]\\s+")
    private val handle = Regex("\\s*@[\\p{L}\\p{N}_.]+")
    private val junk = Regex(
        "\\s*[(\\[][^)\\]]*(?:official|lyrics?|audio|video|kbps|hq)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE
    )
    private val spaces = Regex("\\s{2,}")

    fun clean(s: String): String {
        var t = s.replace('_', ' ')
        t = junk.replace(t, "")
        t = handle.replace(t, "")
        t = spaces.replace(t, " ")
        return t.trim().trim('-', '\u2013', '\u2014', ' ')
    }

    /** modes: 0 = automatic, 1 = swapped, 2 = original (untouched). */
    fun process(raw: List<Song>, smart: Boolean, reverse: Boolean, modes: Map<Long, Int>): List<Song> {
        if (!smart && modes.isEmpty()) return raw
        val freq = HashMap<String, Int>()
        val splits = HashMap<Long, Pair<String, String>>()
        for (s in raw) {
            val parts = clean(s.rawTitle).split(sep, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                val l = parts[0].trim()
                val r = parts[1].trim()
                splits[s.id] = Pair(l, r)
                freq[l.lowercase()] = (freq[l.lowercase()] ?: 0) + 1
                freq[r.lowercase()] = (freq[r.lowercase()] ?: 0) + 1
            }
        }
        return raw.map { s ->
            val mode = modes[s.id] ?: 0
            if (mode == 2 || (!smart && mode == 0)) return@map s
            val sp = splits[s.id]
            if (sp == null) {
                val c = clean(s.rawTitle)
                return@map if (c.isNotBlank() && c != s.title) s.copy(title = c) else s
            }
            val l = sp.first
            val r = sp.second
            val tag = s.rawArtist.trim()
            var artistLeft = when {
                tag.equals(l, ignoreCase = true) -> true
                tag.equals(r, ignoreCase = true) -> false
                else -> {
                    val cl = freq[l.lowercase()] ?: 0
                    val cr = freq[r.lowercase()] ?: 0
                    if (cl != cr) cl > cr else !reverse
                }
            }
            if (mode == 1) artistLeft = !artistLeft
            if (artistLeft) s.copy(title = r, artist = l) else s.copy(title = l, artist = r)
        }
    }
}
