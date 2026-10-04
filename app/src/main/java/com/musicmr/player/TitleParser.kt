package com.musicmr.player

object TitleParser {
    private val sep = Regex("\\s+[-\u2013\u2014]\\s+")
    private val handle = Regex("\\s*@[\\p{L}\\p{N}_.]+")
    private val junk = Regex(
        "\\s*[(\\[][^)\\]]*(?:official|lyrics?|audio|video|kbps|hq)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE
    )
    private val domain = Regex(
        "\\s*(?:https?://)?(?:www\\.)?[\\p{L}\\p{N}-]+\\.(?:com|net|org|me|tv|fm|ir|iq|co)\\b\\S*",
        RegexOption.IGNORE_CASE
    )
    private val leadNum = Regex("^\\s*(?:track\\s*)?\\d{1,3}\\s*[.)\\-\u2013]\\s*", RegexOption.IGNORE_CASE)
    private val spaces = Regex("\\s{2,}")

    private fun isArabic(c: Char) =
        c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F' || c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF'

    private fun isLatin(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '\u00C0'..'\u024F'

    private fun hasArabic(s: String) = s.any { isArabic(it) }
    private fun hasLatin(s: String) = s.any { isLatin(it) }

    fun clean(s: String): String {
        var t = s.replace('_', ' ')
        t = leadNum.replace(t, "")
        t = junk.replace(t, "")
        t = domain.replace(t, "")
        t = handle.replace(t, "")
        t = spaces.replace(t, " ")
        return t.trim().trim('-', '\u2013', '\u2014', ' ')
    }

    /** Splits "English part + Arabic part" (or the reverse) written without any separator. */
    private fun splitByScript(s: String): List<String>? {
        val ia = s.indexOfFirst { isArabic(it) }
        val il = s.indexOfFirst { isLatin(it) }
        if (ia < 0 || il < 0) return null
        val cut = maxOf(ia, il)
        val first = s.substring(0, cut)
        val second = s.substring(cut)
        val ok = if (ia < il) second.none { isArabic(it) } else second.none { isLatin(it) }
        if (!ok) return null
        val a = first.trim().trim('-', '\u2013', '\u2014', '(', ')', '[', ']', ' ')
        val b = second.trim().trim('-', '\u2013', '\u2014', '(', ')', '[', ']', ' ')
        if (a.isBlank() || b.isBlank()) return null
        return listOf(a, b)
    }

    private fun partsOf(c: String): List<String> {
        val bySep = c.split(sep).map { it.trim() }.filter { it.isNotEmpty() }
        if (bySep.size >= 2) return bySep
        return splitByScript(c) ?: listOf(c)
    }

    /** Returns (title, artist) when the title mixes Arabic and English, otherwise null. */
    private fun mixed(parts: List<String>, tag: String, latinFreq: Map<String, Int>): Pair<String, String>? {
        val ar = parts.filter { hasArabic(it) }
        val la = parts.filter { !hasArabic(it) && it.count { c -> isLatin(c) } >= 2 }
        if (ar.isEmpty() || la.isEmpty()) return null
        val tagKnown = tag.isNotBlank() && tag != "Unknown artist" && tag != "<unknown>"
        val lp = la.first()
        val title = ar.last()
        val artist = when {
            ar.size > 1 -> ar.first()
            tagKnown && hasArabic(tag) -> tag
            tagKnown && tag.equals(lp, ignoreCase = true) -> lp
            (latinFreq[lp.lowercase()] ?: 0) >= 2 -> lp
            !tagKnown -> lp
            else -> tag
        }
        return Pair(title, artist)
    }

    /**
     * modes (keyed by file path, per song only): 0 = automatic, 1 = swapped, 2 = original.
     * edits (keyed by file path): manual title and artist for that single song.
     * arabic: when a title mixes English and Arabic, the Arabic part becomes the song name.
     */
    fun process(
        raw: List<Song>,
        smart: Boolean,
        reverse: Boolean,
        modes: Map<String, Int>,
        edits: Map<String, Pair<String, String>> = emptyMap(),
        arabic: Boolean = true
    ): List<Song> {
        if (!smart && modes.isEmpty() && edits.isEmpty()) return raw
        val freq = HashMap<String, Int>()
        val latinFreq = HashMap<String, Int>()
        val splits = HashMap<Long, Pair<String, String>>()
        val cleaned = HashMap<Long, String>()
        val partsMap = HashMap<Long, List<String>>()
        for (s in raw) {
            val c = clean(s.rawTitle)
            cleaned[s.id] = c
            val two = c.split(sep, limit = 2)
            if (two.size == 2 && two[0].isNotBlank() && two[1].isNotBlank()) {
                val l = two[0].trim()
                val r = two[1].trim()
                splits[s.id] = Pair(l, r)
                freq[l.lowercase()] = (freq[l.lowercase()] ?: 0) + 1
                freq[r.lowercase()] = (freq[r.lowercase()] ?: 0) + 1
            }
            if (arabic) {
                val parts = partsOf(c)
                partsMap[s.id] = parts
                if (parts.any { hasArabic(it) }) {
                    parts.filter { !hasArabic(it) && hasLatin(it) }
                        .map { it.lowercase() }
                        .distinct()
                        .forEach { latinFreq[it] = (latinFreq[it] ?: 0) + 1 }
                }
            }
        }
        val base = raw.map { s ->
            val mode = modes[s.path] ?: 0
            if (mode == 2 || (!smart && mode == 0)) return@map s
            val c = cleaned[s.id] ?: s.rawTitle
            if (arabic) {
                val m = mixed(partsMap[s.id].orEmpty(), s.rawArtist.trim(), latinFreq)
                if (m != null) {
                    return@map if (mode == 1) s.copy(title = m.second, artist = m.first)
                    else s.copy(title = m.first, artist = m.second)
                }
            }
            val sp = splits[s.id]
            if (sp == null) {
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
        if (edits.isEmpty()) return base
        return base.map { s ->
            val e = edits[s.path]
            if (e != null) s.copy(title = e.first, artist = e.second) else s
        }
    }
}
