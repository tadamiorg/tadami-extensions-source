package com.sf.tadami.extension.fr.animesama.filters

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.source.model.AnimeFilter
import com.sf.tadami.source.model.AnimeFilterList
import okhttp3.HttpUrl
import java.util.Calendar

/** The catalogue's filters (anime-sama.to/catalogue/), sent as its form does: langue[], current[], genre[]... */
object AnimeSamaFilters {

    private class CheckBoxVal(name: String) : AnimeFilter.CheckBox(name)

    /** Check boxes of (shown name, value sent) */
    open class CheckBoxFilterList(name: String, private val options: List<Pair<String, String>>) :
        AnimeFilter.CheckBoxGroup(name, options.map { CheckBoxVal(it.first) }) {
        val checked: List<String>
            get() = state.zip(options).filter { (box, _) -> box.state }.map { (_, option) -> option.second }
    }

    /** A choice of (shown name, value sent), an empty value sending nothing */
    open class SelectFilter(name: String, private val options: List<Pair<String, String>>) :
        AnimeFilter.Select(name, options.map { it.first }.toTypedArray()) {
        val value: String
            get() = options[state].second
    }

    class LanguageFilter(name: String) : CheckBoxFilterList(name, LANGUAGES.map { it to it })
    class StatusFilter(name: String, options: List<Pair<String, String>>) : CheckBoxFilterList(name, options)
    class SortFilter(name: String, options: List<Pair<String, String>>) : SelectFilter(name, options)
    class GenreFilter(name: String) : CheckBoxFilterList(name, GENRES.map { it to it })
    class YearMinFilter(name: String, any: String) : SelectFilter(name, years(any))
    class YearMaxFilter(name: String, any: String) : SelectFilter(name, years(any))

    fun filterList(i18n: i18n): AnimeFilterList = AnimeFilterList(
        LanguageFilter(i18n.getString("filter_languages")),
        StatusFilter(
            i18n.getString("filter_status"),
            listOf(
                i18n.getString("filter_status_ongoing") to "En cours",
                i18n.getString("filter_status_completed") to "Terminé",
            ),
        ),
        // The site's default first: titre
        SortFilter(
            i18n.getString("filter_sort"),
            listOf(
                i18n.getString("filter_sort_title") to "titre",
                i18n.getString("filter_sort_added") to "ajout",
                i18n.getString("filter_sort_newest") to "recent",
                i18n.getString("filter_sort_oldest") to "ancien",
                i18n.getString("filter_sort_episodes") to "episodes",
            ),
        ),
        GenreFilter(i18n.getString("filter_genres")),
        YearMinFilter(i18n.getString("filter_year_min"), i18n.getString("filter_any")),
        YearMaxFilter(i18n.getString("filter_year_max"), i18n.getString("filter_any")),
    )

    fun HttpUrl.Builder.addFilters(filters: AnimeFilterList): HttpUrl.Builder = apply {
        filters.forEach { filter ->
            when (filter) {
                is LanguageFilter -> filter.checked.forEach { addQueryParameter("langue[]", it) }
                is StatusFilter -> filter.checked.forEach { addQueryParameter("current[]", it) }
                is GenreFilter -> filter.checked.forEach { addQueryParameter("genre[]", it) }
                is SortFilter -> addQueryParameter("tri", filter.value)
                is YearMinFilter -> filter.value.takeIf { it.isNotEmpty() }?.let { addQueryParameter("annee_min", it) }
                is YearMaxFilter -> filter.value.takeIf { it.isNotEmpty() }?.let { addQueryParameter("annee_max", it) }
                else -> {}
            }
        }
    }

    /**
     * The versions (the end of the season urls: saison1/vf) of the languages checked, null when none is. VCN is
     * a Chinese original, at /vostfr like the Japanese ones.
     */
    fun voices(filters: AnimeFilterList): Set<String>? =
        filters.filterIsInstance<LanguageFilter>().firstOrNull()?.checked
            ?.takeIf { it.isNotEmpty() }
            ?.mapNotNull { LANGUAGE_VOICES[it] }
            ?.toSet()

    private val LANGUAGES = listOf("VOSTFR", "VF", "VASTFR", "VCN")

    private val LANGUAGE_VOICES = mapOf("VOSTFR" to "vostfr", "VF" to "vf", "VASTFR" to "va", "VCN" to "vostfr")

    /** The site's form takes 1900 to 2099: the years anime were released in, the latest first */
    private fun years(any: String): List<Pair<String, String>> =
        listOf(any to "") + (Calendar.getInstance().get(Calendar.YEAR) + 1 downTo 1950).map { "$it" to "$it" }

    private val GENRES = listOf(
        "Action", "Adolescence", "Aliens / Extra-terrestres", "Amitié", "Amour", "Apocalypse", "Art",
        "Arts martiaux", "Assassinat", "Autre monde", "Aventure", "Combats", "Comédie", "Crime", "Cyberpunk",
        "Danse", "Démons", "Détective", "Donghua", "Dragon", "Drame", "Ecchi", "Ecole", "Elfe", "Enquête",
        "Famille", "Fantastique", "Fantasy", "Fantômes", "Futur", "Gastronomie", "Ghibli", "Guerre",
        "Harcèlement", "Harem", "Harem inversé", "Histoire", "Historique", "Homosexualité", "Horreur", "Isekai",
        "Jeunesse", "Jeux", "Jeux vidéo", "Josei", "Journalisme", "Kaï", "LGBT+", "Mafia", "Magical girl",
        "Magie", "Maladie", "Mariage", "Mature", "Mechas", "Médiéval", "Militaire", "Monde virtuel", "Monstres",
        "Musique", "Mystère", "Nekketsu", "Ninjas", "Nostalgie", "Paranormal", "Philosophie", "Pirates", "Police",
        "Politique", "Post-apocalyptique", "Pouvoirs psychiques", "Préhistoire", "Prison", "Psychologique",
        "Quotidien", "Religion", "Réincarnation / Transmigration", "Romance", "Samouraïs", "School Life",
        "Science-Fantasy", "Science-fiction", "Scientifique", "Seinen", "Shôjo", "Shôjo-Ai", "Shônen", "Shônen-Ai",
        "Slice of Life", "Société", "Sport", "Super pouvoirs", "Super-héros", "Surnaturel", "Survie",
        "Survival game", "Technologies", "Thriller", "Tournois", "Travail", "Vampires", "Vengeance", "Voyage",
        "Voyage temporel", "Webcomic", "Yakuza", "Yaoi", "Yokai", "Yuri",
    )
}
