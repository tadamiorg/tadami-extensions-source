package com.sf.tadami.extensions.en.gogoanime.filters

import com.sf.tadami.App
import com.sf.tadami.extensions.en.gogoanime.R

object GogoAnimeFiltersData {
    val GENRE_SEARCH_LIST = arrayOf(
        Pair("Action", "action"),
        Pair("Adult Cast", "adult-cast"),
        Pair("Adventure", "adventure"),
        Pair("Anthropomorphic", "anthropomorphic"),
        Pair("Avant Garde", "avant-garde"),
        Pair("Boys Love", "shounen-ai"),
        Pair("Cars", "cars"),
        Pair("CGDCT", "cgdct"),
        Pair("Childcare", "childcare"),
        Pair("Comedy", "comedy"),
        Pair("Comic", "comic"),
        Pair("Crime", "crime"),
        Pair("Crossdressing", "crossdressing"),
        Pair("Delinquents", "delinquents"),
        Pair("Dementia", "dementia"),
        Pair("Demons", "demons"),
        Pair("Detective", "detective"),
        Pair("Drama", "drama"),
        Pair("Dub", "dub"),
        Pair("Ecchi", "ecchi"),
        Pair("Erotica", "erotica"),
        Pair("Family", "family"),
        Pair("Fantasy", "fantasy"),
        Pair("Gag Humor", "gag-humor"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Gore", "gore"),
        Pair("Gourmet", "gourmet"),
        Pair("Harem", "harem"),
        Pair("Hentai", "hentai"),
        Pair("High Stakes Game", "high-stakes-game"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Iyashikei", "iyashikei"),
        Pair("Josei", "josei"),
        Pair("Kids", "kids"),
        Pair("Magic", "magic"),
        Pair("Magical Sex Shift", "magical-sex-shift"),
        Pair("Mahou Shoujo", "mahou-shoujo"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Military", "military"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Mythology", "mythology"),
        Pair("Organized Crime", "organized-crime"),
        Pair("Parody", "parody"),
        Pair("Performing Arts", "performing-arts"),
        Pair("Pets", "pets"),
        Pair("Police", "police"),
        Pair("Psychological", "psychological"),
        Pair("Racing", "racing"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Romance", "romance"),
        Pair("Romantic Subtext", "romantic-subtext"),
        Pair("Samurai", "samurai"),
        Pair("School", "school"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Showbiz", "showbiz"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Space", "space"),
        Pair("Sports", "sports"),
        Pair("Strategy Game", "strategy-game"),
        Pair("Super Power", "super-power"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Suspense", "suspense"),
        Pair("Team Sports", "team-sports"),
        Pair("Thriller", "thriller"),
        Pair("Time Travel", "time-travel"),
        Pair("Vampire", "vampire"),
        Pair("Visual Arts", "visual-arts"),
        Pair("Work Life", "work-life"),
        Pair("Workplace", "workplace"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
    )

    // copy($("div.cls_genre ul.dropdown-menu li").map((i,el) => `Pair("${$(el).text().trim()}", "${$(el).find("input").first().attr('value')}")`).get().join(',\n'))
    // on /filter.html
    val COUNTRY_SEARCH_LIST = arrayOf(
        Pair("China", "5"),
        Pair("Japan", "2"),
    )

    // copy($("div.cls_season ul.dropdown-menu li").map((i,el) => `Pair("${$(el).text().trim()}", "${$(el).find("input").first().attr('value')}")`).get().join(',\n'))
    // on /filter.html
    val SEASON_SEARCH_LIST = arrayOf(
        Pair("Fall", "fall"),
        Pair("Summer", "summer"),
        Pair("Spring", "spring"),
        Pair("Winter", "winter"),
    )

    // copy($("div.cls_year ul.dropdown-menu li").map((i,el) => `Pair("${$(el).text().trim()}", "${$(el).find("input").first().attr('value')}")`).get().join(',\n'))
    // on /filter.html
    val YEAR_SEARCH_LIST = arrayOf(
        Pair("2023", "2023"),
        Pair("2022", "2022"),
        Pair("2021", "2021"),
        Pair("2020", "2020"),
        Pair("2019", "2019"),
        Pair("2018", "2018"),
        Pair("2017", "2017"),
        Pair("2016", "2016"),
        Pair("2015", "2015"),
        Pair("2014", "2014"),
        Pair("2013", "2013"),
        Pair("2012", "2012"),
        Pair("2011", "2011"),
        Pair("2010", "2010"),
        Pair("2009", "2009"),
        Pair("2008", "2008"),
        Pair("2007", "2007"),
        Pair("2006", "2006"),
        Pair("2005", "2005"),
        Pair("2004", "2004"),
        Pair("2003", "2003"),
        Pair("2002", "2002"),
        Pair("2001", "2001"),
        Pair("2000", "2000"),
        Pair("1999", "1999"),
    )

    // copy($("div.cls_lang ul.dropdown-menu li").map((i,el) => `Pair("${$(el).text().trim()}", "${$(el).find("input").first().attr('value')}")`).get().join(',\n'))
    // on /filter.html
    val LANGUAGE_SEARCH_LIST = arrayOf(
        Pair("Sub & Dub", "subdub"),
        Pair("Sub", "sub"),
        Pair("Dub", "dub"),
    )

    // copy($("div.cls_type ul.dropdown-menu li").map((i,el) => `Pair("${$(el).text().trim()}", "${$(el).find("input").first().attr('value')}")`).get().join(',\n'))
    // on /filter.html
    val TYPE_SEARCH_LIST = arrayOf(
        Pair("Movie", "3"),
        Pair("TV", "1"),
        Pair("OVA", "26"),
        Pair("ONA", "30"),
        Pair("Special", "2"),
        Pair("Music", "32"),
    )

    // copy($("div.cls_status ul.dropdown-menu li").map((i,el) => `Pair("${$(el).text().trim()}", "${$(el).find("input").first().attr('value')}")`).get().join(',\n'))
    // on /filter.html
    val STATUS_SEARCH_LIST = arrayOf(
        Pair("Not Yet Aired", "Upcoming"),
        Pair("Ongoing", "Ongoing"),
        Pair("Completed", "Completed"),
    )

    // copy($("div.cls_sort ul.dropdown-menu li").map((i,el) => `Pair("${$(el).text().trim()}", "${$(el).find("input").first().attr('value')}")`).get().join(',\n'))
    // on /filter.html
    val SORT_SEARCH_LIST = arrayOf(
        Pair("Name A-Z", "title_az"),
        Pair("Recently updated", "recently_updated"),
        Pair("Recently added", "recently_added"),
        Pair("Release date", "release_date"),
    )

    // copy($("div.dropdown-menu > a.dropdown-item").map((i,el) => `Pair("${$(el).text().trim()}", "${$(el).attr('href').trim().slice(18)}")`).get().join(',\n'))
    // on /
    val GENRE_LIST = arrayOf(
        Pair(App.getAppContext()?.getString(R.string.discover_search_screen_filters_group_selected_text) ?: "select",""),
        Pair("Action", "action"),
        Pair("Adult Cast", "adult-cast"),
        Pair("Adventure", "adventure"),
        Pair("Anthropomorphic", "anthropomorphic"),
        Pair("Avant Garde", "avant-garde"),
        Pair("Boys Love", "shounen-ai"),
        Pair("Cars", "cars"),
        Pair("CGDCT", "cgdct"),
        Pair("Childcare", "childcare"),
        Pair("Comedy", "comedy"),
        Pair("Comic", "comic"),
        Pair("Crime", "crime"),
        Pair("Crossdressing", "crossdressing"),
        Pair("Delinquents", "delinquents"),
        Pair("Dementia", "dementia"),
        Pair("Demons", "demons"),
        Pair("Detective", "detective"),
        Pair("Drama", "drama"),
        Pair("Dub", "dub"),
        Pair("Ecchi", "ecchi"),
        Pair("Erotica", "erotica"),
        Pair("Family", "family"),
        Pair("Fantasy", "fantasy"),
        Pair("Gag Humor", "gag-humor"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Gore", "gore"),
        Pair("Gourmet", "gourmet"),
        Pair("Harem", "harem"),
        Pair("Hentai", "hentai"),
        Pair("High Stakes Game", "high-stakes-game"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Iyashikei", "iyashikei"),
        Pair("Josei", "josei"),
        Pair("Kids", "kids"),
        Pair("Magic", "magic"),
        Pair("Magical Sex Shift", "magical-sex-shift"),
        Pair("Mahou Shoujo", "mahou-shoujo"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Military", "military"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Mythology", "mythology"),
        Pair("Organized Crime", "organized-crime"),
        Pair("Parody", "parody"),
        Pair("Performing Arts", "performing-arts"),
        Pair("Pets", "pets"),
        Pair("Police", "police"),
        Pair("Psychological", "psychological"),
        Pair("Racing", "racing"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Romance", "romance"),
        Pair("Romantic Subtext", "romantic-subtext"),
        Pair("Samurai", "samurai"),
        Pair("School", "school"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Showbiz", "showbiz"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Space", "space"),
        Pair("Sports", "sports"),
        Pair("Strategy Game", "strategy-game"),
        Pair("Super Power", "super-power"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Suspense", "suspense"),
        Pair("Team Sports", "team-sports"),
        Pair("Thriller", "thriller"),
        Pair("Time Travel", "time-travel"),
        Pair("Vampire", "vampire"),
        Pair("Visual Arts", "visual-arts"),
        Pair("Work Life", "work-life"),
        Pair("Workplace", "workplace"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
    )

    val RECENT_LIST = arrayOf(
        Pair(App.getAppContext()?.getString(R.string.discover_search_screen_filters_group_selected_text) ?: "select",""),
        Pair("Recent Release", "1"),
        Pair("Recent Dub", "2"),
        Pair("Recent Chinese", "3"),
    )

    val SEASON_LIST = arrayOf(
        Pair(App.getAppContext()?.getString(R.string.discover_search_screen_filters_group_selected_text) ?: "select",""),
        Pair("Latest season", "new-season.html"),
        Pair("Summer 2023", "sub-category/summer-2023-anime"),
        Pair("Spring 2023", "sub-category/spring-2023-anime"),
        Pair("Winter 2023", "sub-category/winter-2023-anime"),
        Pair("Fall 2022", "sub-category/fall-2022-anime"),
        Pair("Summer 2022", "sub-category/summer-2022-anime"),
        Pair("Spring 2022", "sub-category/spring-2022-anime"),
        Pair("Winter 2022", "sub-category/winter-2022-anime"),
        Pair("Fall 2021", "sub-category/fall-2021-anime"),
        Pair("Summer 2021", "sub-category/summer-2021-anime"),
        Pair("Spring 2021", "sub-category/spring-2021-anime"),
        Pair("Winter 2021", "sub-category/winter-2021-anime"),
        Pair("Fall 2020", "sub-category/fall-2020-anime"),
        Pair("Summer 2020", "sub-category/summer-2020-anime"),
        Pair("Spring 2020", "sub-category/spring-2020-anime"),
        Pair("Winter 2020", "sub-category/winter-2020-anime"),
        Pair("Fall 2019", "sub-category/fall-2019-anime"),
        Pair("Summer 2019", "sub-category/summer-2019-anime"),
        Pair("Spring 2019", "sub-category/spring-2019-anime"),
        Pair("Winter 2019", "sub-category/winter-2019-anime"),
        Pair("Fall 2018", "sub-category/fall-2018-anime"),
        Pair("Summer 2018", "sub-category/summer-2018-anime"),
        Pair("Spring 2018", "sub-category/spring-2018-anime"),
        Pair("Winter 2018", "sub-category/winter-2018-anime"),
        Pair("Fall 2017", "sub-category/fall-2017-anime"),
        Pair("Summer 2017", "sub-category/summer-2017-anime"),
        Pair("Spring 2017", "sub-category/spring-2017-anime"),
        Pair("Winter 2017", "sub-category/winter-2017-anime"),
        Pair("Fall 2016", "sub-category/fall-2016-anime"),
        Pair("Summer 2016", "sub-category/summer-2016-anime"),
        Pair("Spring 2016", "sub-category/spring-2016-anime"),
        Pair("Winter 2016", "sub-category/winter-2016-anime"),
        Pair("Fall 2015", "sub-category/fall-2015-anime"),
        Pair("Summer 2015", "sub-category/summer-2015-anime"),
        Pair("Spring 2015", "sub-category/spring-2015-anime"),
        Pair("Winter 2015", "sub-category/winter-2015-anime"),
        Pair("Fall 2014", "sub-category/fall-2014-anime"),
        Pair("Summer 2014", "sub-category/summer-2014-anime"),
        Pair("Spring 2014", "sub-category/spring-2014-anime"),
        Pair("Winter 2014", "sub-category/winter-2014-anime"),
    )
}