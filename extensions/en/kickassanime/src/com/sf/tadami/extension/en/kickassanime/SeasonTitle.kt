package com.sf.tadami.extension.en.kickassanime

/**
 * Puts the season first, as in the other sources: "Kusuriya no Hitorigoto 3rd Season Part 2" and "The Apothecary
 * Diaries Season 3 Part 2" give "Season 3 Part 2 - <name>". The romaji and English titles mark seasons differently
 * ("2nd Season", "Second Season", "Season Two", "Season II", "Cour 2"), so both are normalized to the same label.
 */
internal object SeasonTitle {
    private val NUM_WORDS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    private val ORD_WORDS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")
    private val ROMAN = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")
    private val NUM = """\d+|[ivx]+|${NUM_WORDS.joinToString("|")}"""
    private val ORD = """\d+(?:st|nd|rd|th)|${ORD_WORDS.joinToString("|")}"""

    // "<name>(: | - | – | )<marker>[ (tag)...]", marker = Season N | Nth Season | [The] Final Season [Part|Cour N] | Part N | Cour N | Nth-cour
    private val SEASON_SUFFIX = Regex(
        """^(.+?)(?:\s*:\s*|\s+[-–]\s+|\s+)""" +
            """(?:(?:the\s+)?(?:season\s+($NUM)|($ORD)\s+season|(final)\s+season)(?:\s+(?:part|cour)\s+(\d+))?""" +
            """|(?:part|cour)\s+(\d+)|(\d+)(?:st|nd|rd|th)[\s-]cour)""" +
            """((?:\s*\([^)]*\))*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_NUMBER = Regex("""^(.+?)\s+(\d{1,2}|[IVX]+)((?:\s*\([^)]*\))*)$""")
    private val SLUG_SEASON = Regex("""(?:^|-)(?:season-(\d+)|(\d+)(?:st|nd|rd|th)-season)-[0-9a-f]{4}$""")
    private val SEASON_WORD = Regex("""\bseason\b""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("""^(\d+)(?:st|nd|rd|th)?$""")

    private class Parsed(val label: String, val name: String, val season: Int?)

    private fun String.toNumber(): Int? {
        val s = lowercase()
        NUMBER.find(s)?.let { return it.groupValues[1].toInt() }
        return listOf(NUM_WORDS, ORD_WORDS, ROMAN).firstNotNullOfOrNull { words ->
            words.indexOf(s).takeIf { it >= 0 }?.plus(1)
        }
    }

    private fun parse(title: String, type: String?): Parsed? {
        val g = SEASON_SUFFIX.matchEntire(title.trim())?.groupValues ?: return null
        val seasonText = g[2].ifEmpty { g[3] }
        val season = if (seasonText.isEmpty()) null else seasonText.toNumber() ?: return null
        val final = g[4].isNotEmpty()
        val part = g[5].ifEmpty { g[6] }.ifEmpty { g[7] }
        // "<Film> Part 2" is not a season
        if (season == null && !final && type == "movie") return null
        val label = listOfNotNull(
            season?.let { "Season $it" } ?: "Final Season".takeIf { final },
            part.takeIf { it.isNotEmpty() }?.let { "Part $it" },
        ).joinToString(" ")
        return Parsed(label, g[1] + g[8], season)
    }

    /**
     * The title to show, with the season first, and the bare name. [other] is the anime's other title (English or
     * romaji) and [slug] its url slug: a bare trailing "2" or "II" only counts as a season when one of them says so,
     * since it is part of many names ("Lupin III", "Yes! Precure 5").
     */
    fun split(title: String, other: String?, slug: String, type: String?): Pair<String, String> {
        parse(title, type)?.let { return "${it.label} - ${it.name}" to it.name }
        val m = TRAILING_NUMBER.matchEntire(title.trim())
        if (m != null && !SEASON_WORD.containsMatchIn(title)) {
            val hint = other?.let { parse(it, type)?.season }
                ?: SLUG_SEASON.find(slug)?.groupValues?.let { it[1].ifEmpty { it[2] }.toInt() }
            if (hint != null && hint >= 2 && m.groupValues[2].toNumber() == hint) {
                val name = m.groupValues[1] + m.groupValues[3]
                return "Season $hint - $name" to name
            }
        }
        return title to title
    }
}
