# Tadami Extensions Development Guide

This guide provides comprehensive instructions for creating and updating Tadami extensions used to scrape anime websites for anime information and video sources.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Core Concepts](#core-concepts)
4. [Creating a New Extension](#creating-a-new-extension)
5. [Extension Types](#extension-types)
6. [Data Models](#data-models)
7. [Video Extractors](#video-extractors)
8. [Preferences System](#preferences-system)
9. [Filters and Search](#filters-and-search)
10. [Internationalization](#internationalization)
11. [Best Practices](#best-practices)
12. [Updating Extensions](#updating-extensions)

---

## Architecture Overview

Tadami extensions are standalone Android APKs that implement scraping logic for specific anime streaming websites. Each extension:

- Scrapes anime metadata (title, description, genres, etc.)
- Extracts episode lists
- Retrieves video streaming sources
- Provides search and filtering capabilities
- Can be independently updated without modifying the main app

### Key Components

```
tadami-extensions-source/
├── api/              # Stub interfaces mimicking main app
├── core/             # Core library module
├── extensions/       # Individual extension implementations
│   ├── en/          # English language extensions
│   └── fr/          # French language extensions
├── lib/              # Reusable video extractor libraries (30+ extractors)
└── lib-multiexts/   # Multi-extension base classes (Zoro, DooPlay)
```

---

## Project Structure

### Extension Directory Structure

```
extensions/{lang}/{source-name}/
├── AndroidManifest.xml                    # Empty (metadata in core)
├── build.gradle.kts                       # Build config with extension metadata
├── res/
│   ├── mipmap-hdpi/ic_launcher.png       # 72x72
│   ├── mipmap-mdpi/ic_launcher.png       # 48x48
│   ├── mipmap-xhdpi/ic_launcher.png      # 96x96
│   ├── mipmap-xxhdpi/ic_launcher.png     # 144x144
│   └── mipmap-xxxhdpi/ic_launcher.png    # 192x192
└── src/com/sf/tadami/extension/{lang}/{source-name}/
    ├── {SourceName}.kt                    # Main source class
    ├── {SourceName}Preferences.kt         # Preferences data class
    ├── {SourceName}PreferencesScreen.kt   # UI preferences
    ├── {SourceName}Translations.kt        # i18n strings
    ├── filters/                           # Optional filters
    │   ├── {SourceName}Filters.kt
    │   └── {SourceName}FiltersData.kt
    └── extractors/                        # Optional custom extractors
        └── CustomExtractor.kt
```

---

## Core Concepts

### Source Interface Hierarchy

Extensions inherit from a chain of increasingly specialized interfaces/classes:

1. **`Source`** - Base interface with minimal requirements
2. **`AnimeCatalogueSource`** - Adds search and browsing
3. **`AnimeHttpSource`** - Abstract class for HTTP-based sources
4. **`ParsedAnimeHttpSource`** - Adds Jsoup/CSS selector support
5. **`ConfigurableParsedHttpAnimeSource<T>`** - Adds preferences support

### Base Interface: `Source`

```kotlin
interface Source {
    val id: Long                  // Unique extension ID
    val name: String             // Display name
    val lang: Lang               // Language (ENGLISH, FRENCH, etc.)

    fun fetchAnimeDetails(anime: Anime): Observable<SAnime>
    fun fetchEpisodesList(anime: Anime): Observable<List<SEpisode>>
    fun fetchEpisodeSources(url: String): Observable<List<StreamSource>>
}
```

### Abstract Class: `AnimeHttpSource`

```kotlin
abstract class AnimeHttpSource : AnimeCatalogueSource {
    abstract val baseUrl: String
    open val headers: Headers
    open val client: OkHttpClient

    // Search
    protected abstract fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList, noToasts: Boolean): Request
    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    // Latest
    protected abstract fun latestAnimesRequest(page: Int): Request
    protected abstract fun latestAnimeParse(response: Response): AnimesPage

    // Details
    protected abstract fun animeDetailsParse(response: Response): SAnime

    // Episodes
    protected abstract fun episodesListParse(response: Response): List<SEpisode>

    // Video Sources
    protected abstract fun episodeSourcesParse(response: Response): List<StreamSource>
}
```

### Abstract Class: `ParsedAnimeHttpSource`

For sources that use CSS selectors (most common):

```kotlin
abstract class ParsedAnimeHttpSource : AnimeHttpSource() {
    // Search with CSS selectors
    protected abstract fun searchSelector(): String
    protected abstract fun searchAnimeFromElement(element: Element): SAnime?
    protected abstract fun searchAnimeNextPageSelector(): String?

    // Latest with CSS selectors
    protected abstract fun latestSelector(): String
    protected abstract fun latestAnimeFromElement(element: Element): SAnime
    protected abstract fun latestAnimeNextPageSelector(): String?

    // Episode selectors
    protected abstract fun episodesListSelector(): String
    protected abstract fun episodeFromElement(element: Element): SEpisode

    // Video source selectors
    protected abstract fun episodeSourcesSelector(): String
    protected abstract fun episodeSourcesFromElement(element: Element): List<StreamSource>
}
```

---

## Creating a New Extension

### Step 1: Create Directory Structure

```bash
# Create extension directory
mkdir -p extensions/{lang}/{source-name}/src/com/sf/tadami/extension/{lang}/{source-name}
mkdir -p extensions/{lang}/{source-name}/res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}

# Create empty AndroidManifest.xml
touch extensions/{lang}/{source-name}/AndroidManifest.xml
```

### Step 2: Create `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    extra.apply {
        set("extVersionCode", 1)                    // Start at 1, increment on updates
        set("extName", "AnimeSite")                 // Display name
        set("pkgNameSuffix", "en.animesite")        // Unique package identifier
        set("extClass", ".AnimeSite")               // Main class name with dot prefix
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    // Add required video extractors
    implementation(project(":lib:dood-extractor"))
    implementation(project(":lib:streamwish-extractor"))
    implementation(project(":lib:i18n"))
    // Add more extractors as needed
}
```

### Step 3: Create Main Source Class

**Simple Example (ParsedAnimeHttpSource):**

```kotlin
package com.sf.tadami.extension.en.animesite

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.source.model.*
import com.sf.tadami.source.online.ConfigurableParsedHttpAnimeSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class AnimeSite : ConfigurableParsedHttpAnimeSource<AnimeSitePreferences>(
    sourceId = 100,  // Choose unique ID (check existing extensions)
    prefGroup = AnimeSitePreferences
) {
    override val name = "AnimeSite"
    override val baseUrl get() = preferences.baseUrl
    override val lang = Lang.ENGLISH

    // Use cloudflare client if site has protection
    override val client = network.cloudflareClient

    // ============= LATEST ANIME =============
    override fun latestAnimesRequest(page: Int): Request {
        return GET("$baseUrl/latest?page=$page", headers)
    }

    override fun latestSelector() = "div.anime-card"

    override fun latestAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            title = element.selectFirst("h2.title")?.text() ?: ""
            thumbnailUrl = element.selectFirst("img")?.attr("abs:src")
            setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
        }
    }

    override fun latestAnimeNextPageSelector() = "a.next-page"

    // ============= SEARCH =============
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList, noToasts: Boolean): Request {
        return GET("$baseUrl/search?q=$query&page=$page", headers)
    }

    override fun searchSelector() = latestSelector()

    override fun searchAnimeFromElement(element: Element) = latestAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = latestAnimeNextPageSelector()

    // ============= ANIME DETAILS =============
    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        return SAnime.create().apply {
            title = document.selectFirst("h1.anime-title")?.text() ?: ""
            thumbnailUrl = document.selectFirst("img.anime-poster")?.attr("abs:src")
            description = document.selectFirst("div.synopsis")?.text()
            genres = document.select("div.genres a").map { it.text() }
            status = document.selectFirst("span.status")?.text()
            release = document.selectFirst("span.year")?.text()
        }
    }

    // ============= EPISODES =============
    override fun episodesListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodesListSelector()).map { episodeFromElement(it) }
    }

    override fun episodesListSelector() = "div.episode-item"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
            name = element.selectFirst("span.episode-name")?.text() ?: ""
            episodeNumber = element.selectFirst("span.episode-number")?.text()?.toFloatOrNull() ?: 0f
        }
    }

    // ============= VIDEO SOURCES =============
    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val document = response.asJsoup()
        val videoUrls = document.select("div.player-source").map {
            it.attr("data-src")
        }

        // Use extractors to get video sources
        return videoUrls.parallelMap { url ->
            when {
                "dood" in url -> DoodExtractor(client).videosFromUrl(url)
                "streamwish" in url -> StreamWishExtractor(client).videosFromUrl(url)
                else -> null
            }
        }.filterNotNull().flatten()
    }

    override fun episodeSourcesSelector() = throw UnsupportedOperationException()

    override fun episodeSourcesFromElement(element: Element) = throw UnsupportedOperationException()

    // ============= PREFERENCES =============
    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getAnimeSitePreferencesContent(i18n)
    }

    private val i18n = i18n(AnimeSiteTranslations)
}
```

### Step 4: Create Preferences

**Preferences Data Class:**

```kotlin
package com.sf.tadami.extension.en.animesite

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

data class AnimeSitePreferences(
    val baseUrl: String
) : CustomPreferencesIdentifier {
    companion object : CustomPreferences<AnimeSitePreferences> {
        const val DEFAULT_BASE_URL = "https://animesite.com"

        val BASE_URL = stringPreferencesKey("base_url")

        override fun transform(preferences: Preferences): AnimeSitePreferences {
            return AnimeSitePreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL
            )
        }

        override fun setPreferences(customPreferences: AnimeSitePreferences, preferences: Preferences): Map<Preferences.Key<*>, Any> {
            return mapOf(
                BASE_URL to customPreferences.baseUrl
            )
        }
    }
}
```

**Preferences Screen:**

```kotlin
package com.sf.tadami.extension.en.animesite

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.*

fun getAnimeSitePreferencesContent(i18n: i18n): SourcesPreferencesContent {
    return SourcesPreferencesContent(
        title = "AnimeSite",
        preferences = listOf(
            PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    EditTextPreference(
                        key = AnimeSitePreferences.BASE_URL,
                        title = i18n.getString("pref_base_url_title"),
                        defaultValue = AnimeSitePreferences.DEFAULT_BASE_URL
                    )
                )
            )
        )
    )
}
```

### Step 5: Create Translations

```kotlin
package com.sf.tadami.extension.en.animesite

import com.sf.tadami.lib.i18n.Language
import com.sf.tadami.lib.i18n.Translations

val AnimeSiteTranslations: Translations = mapOf(
    "category_network" to mapOf(
        Language.en to "Network Settings",
        Language.fr to "Paramètres réseau"
    ),
    "pref_base_url_title" to mapOf(
        Language.en to "Override base URL",
        Language.fr to "Remplacer l'URL de base"
    )
)
```

### Step 6: Add Extension Icon

Add launcher icons in `res/mipmap-*` directories (PNG format):
- `mipmap-mdpi`: 48x48px
- `mipmap-hdpi`: 72x72px
- `mipmap-xhdpi`: 96x96px
- `mipmap-xxhdpi`: 144x144px
- `mipmap-xxxhdpi`: 192x192px

---

## Extension Types

### 1. Simple Extension (ParsedAnimeHttpSource)

Use when the website has a straightforward HTML structure and uses CSS selectors.

**Example:** See Step 3 above

### 2. Complex Extension (AnimeHttpSource)

Use when you need more control over parsing or the website uses non-HTML responses (JSON, etc.).

```kotlin
class AnimeSite : ConfigurableParsedHttpAnimeSource<AnimeSitePreferences>(
    sourceId = 100,
    prefGroup = AnimeSitePreferences
) {
    override fun searchAnimeParse(response: Response): AnimesPage {
        val json = response.body.string()
        val data = JSONObject(json)
        val animes = data.getJSONArray("results").map { item ->
            SAnime.create().apply {
                title = item.getString("title")
                setUrlWithoutDomain(item.getString("url"))
            }
        }
        return AnimesPage(animes, hasNextPage = data.getBoolean("has_next"))
    }
}
```

### 3. Multi-Extension (Using Base Classes)

Use when multiple sites share similar structure (e.g., Zoro-based sites, DooPlay-based sites).

**Example - Zoro Base:**

```kotlin
// Base class in lib-multiexts/zoro/
abstract class Zoro<T : ZoroPreferences> : ConfigurableParsedHttpAnimeSource<T>() {
    override fun latestSelector() = "div.flw-item"
    override fun searchSelector() = latestSelector()

    protected abstract fun extractVideo(server: VideoData): List<StreamSource>

    // Common implementations...
}

// Extension using Zoro base
class HiAnime : Zoro<HiAnimePreferences>(
    sourceId = 8,
    prefGroup = HiAnimePreferences
) {
    override val name = "HiAnime"
    override val lang = Lang.ENGLISH

    override fun extractVideo(server: VideoData): List<StreamSource> {
        return when (server.name) {
            "HD-1", "HD-2" -> megaCloudExtractor.getVideosFromUrl(server.link)
            else -> emptyList()
        }
    }
}
```

---

## Data Models

### SAnime (Source Anime)

Represents anime metadata:

```kotlin
SAnime.create().apply {
    url = "/anime/naruto"                    // Relative URL to anime details page
    title = "Naruto"                         // Anime title
    thumbnailUrl = "https://..."             // Poster/thumbnail image
    description = "A young ninja..."         // Synopsis
    genres = listOf("Action", "Adventure")   // Genre list
    status = "Finished Airing"               // Airing status
    release = "2002"                         // Release year
    initialized = true                       // Set to true when all fields populated
}
```

### SEpisode (Source Episode)

Represents an episode:

```kotlin
SEpisode.create().apply {
    url = "/episode/naruto-1"                // Relative URL to episode video page
    name = "Episode 1: Enter Naruto"         // Episode name
    episodeNumber = 1f                       // Episode number (Float for decimals)
    dateUpload = System.currentTimeMillis()  // Upload timestamp (optional)
    languages = "sub,dub"                    // Available languages (optional)
}
```

### StreamSource (Video Source)

Represents a video stream:

```kotlin
StreamSource(
    url = "https://video.com/stream.m3u8",   // Direct video URL
    fullName = "Doodstream - 1080p",         // Display name with quality
    quality = "1080p",                       // Quality label
    server = "Doodstream",                   // Server/host name
    headers = Headers.headersOf(             // Optional headers for video requests
        "Referer", "https://embedder.com"
    ),
    subtitleTracks = listOf(                 // Optional subtitle tracks
        Track.SubtitleTrack(
            url = "https://subs.com/en.vtt",
            lang = "English",
            label = "English"
        )
    ),
    audioTracks = emptyList()                // Optional audio tracks
)
```

### AnimeFilterList

For advanced search filtering:

```kotlin
sealed class AnimeFilter(val name: String) {
    class Header(name: String) : AnimeFilter(name)
    abstract class CheckBox(name: String, var state: Boolean = false) : AnimeFilter(name)
    abstract class Select(name: String, val values: Array<String>, var state: Int = 0) : AnimeFilter(name)
    abstract class CheckBoxGroup(name: String, var state: List<CheckBox>) : AnimeFilter(name)
    abstract class Text(name: String, var state: String = "") : AnimeFilter(name)
}
```

---

## Video Extractors

Tadami includes 30+ pre-built video extractors in the `/lib` directory.

### Available Extractors

Common extractors include:
- `dood-extractor` - Doodstream
- `streamwish-extractor` - StreamWish
- `megacloud-extractor` - MegaCloud
- `gogostream-extractor` - GogoStream
- `mp4upload-extractor` - Mp4Upload
- `voe-extractor` - VoE
- `filemoon-extractor` - FileMoon
- `streamtape-extractor` - StreamTape
- `sendvid-extractor` - Sendvid
- `sibnet-extractor` - Sibnet
- `vk-extractor` - VK
- `playlist-utils` - M3U8/HLS playlist parsing
- `unpacker` - JavaScript unpacking utilities
- `cryptoaes` - AES decryption
- And many more...

### Using Extractors

**1. Add dependency in `build.gradle.kts`:**

```kotlin
dependencies {
    implementation(project(":lib:dood-extractor"))
    implementation(project(":lib:streamwish-extractor"))
}
```

**2. Import and use in source class:**

```kotlin
import com.sf.tadami.lib.doodextractor.DoodExtractor
import com.sf.tadami.lib.streamwishextractor.StreamWishExtractor

override fun episodeSourcesParse(response: Response): List<StreamSource> {
    val document = response.asJsoup()
    val videoUrls = document.select("iframe").map { it.attr("src") }

    return videoUrls.parallelMap { url ->
        when {
            "dood" in url -> DoodExtractor(client).videosFromUrl(url)
            "streamwish" in url -> StreamWishExtractor(client).videosFromUrl(url)
            else -> null
        }
    }.filterNotNull().flatten()
}
```

### Parallel Processing

Use `parallelMap` for efficient concurrent extraction:

```kotlin
videoUrls.parallelMap { url ->
    runCatching {
        extractor.videosFromUrl(url)
    }.getOrNull()
}.filterNotNull().flatten()
```

### Custom Extractor

Create a custom extractor when no library exists:

```kotlin
class CustomExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String): List<StreamSource> {
        return runCatching {
            val response = client.newCall(GET(url)).execute()
            val document = response.asJsoup()

            val videoUrl = document.selectFirst("video")?.attr("src") ?: return emptyList()

            StreamSource(
                url = videoUrl,
                fullName = "Custom Server",
                quality = "720p",
                server = "Custom",
                headers = Headers.headersOf("Referer", url)
            )
        }.getOrElse { emptyList() }
    }
}
```

---

## Preferences System

### Preference Types

**1. EditTextPreference** - Text input

```kotlin
EditTextPreference(
    key = AnimeSitePreferences.BASE_URL,
    title = "Base URL",
    defaultValue = "https://animesite.com"
)
```

**2. TogglePreference** - Boolean switch

```kotlin
TogglePreference(
    key = AnimeSitePreferences.USE_CLOUDFLARE,
    title = "Use Cloudflare bypass",
    defaultValue = false
)
```

**3. SelectPreference** - Dropdown selection

```kotlin
SelectPreference(
    key = AnimeSitePreferences.QUALITY,
    title = "Preferred quality",
    items = listOf("1080p", "720p", "480p"),
    defaultValue = "1080p"
)
```

**4. MultiSelectPreference** - Multiple selection

```kotlin
MultiSelectPreference(
    key = AnimeSitePreferences.SERVERS,
    title = "Enabled servers",
    items = listOf("Server1", "Server2", "Server3"),
    defaultValues = listOf("Server1", "Server2")
)
```

**5. ReorderStringPreference** - Drag-to-reorder list

```kotlin
ReorderStringPreference(
    key = AnimeSitePreferences.SERVER_ORDER,
    title = "Server priority order",
    defaultValue = "server1,server2,server3"
)
```

### Accessing Preferences

In your source class:

```kotlin
override val baseUrl get() = preferences.baseUrl
override val client get() = if (preferences.useCloudflare) network.cloudflareClient else network.client
```

### Preference Migrations

Handle preference updates between extension versions:

```kotlin
private suspend fun preferencesMigrations(): Boolean {
    val oldVersion = preferences.lastVersionCode
    if (oldVersion < BuildConfig.VERSION_CODE) {
        if (oldVersion < 5) {
            // Migrate old preference format
            dataStore.editPreference(newValue, PREFERENCE_KEY)
        }
        dataStore.editPreference(BuildConfig.VERSION_CODE, AnimeSitePreferences.LAST_VERSION_CODE)
        return true
    }
    return false
}

override fun fetchLatestAnimes(page: Int): Observable<AnimesPage> {
    return runBlocking { preferencesMigrations() }
        .let { super.fetchLatestAnimes(page) }
}
```

---

## Filters and Search

### Creating Filters

```kotlin
package com.sf.tadami.extension.en.animesite.filters

import com.sf.tadami.source.model.AnimeFilter
import com.sf.tadami.source.model.AnimeFilterList

object AnimeSiteFilters {

    private class GenreFilter : QueryPartFilter(
        "Genre",
        arrayOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy")
        )
    )

    private class StatusFilter : CheckBoxFilterList(
        "Status",
        listOf(
            CheckBox("Ongoing"),
            CheckBox("Completed"),
            CheckBox("Upcoming")
        )
    )

    private class SortFilter : Select<String>(
        "Sort by",
        arrayOf("Latest", "Popular", "Rating")
    )

    fun getSearchFilters(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("Use filters for advanced search"),
            GenreFilter(),
            StatusFilter(),
            SortFilter()
        )
    }

    data class FilterSearchParams(
        val genre: String = "",
        val status: List<String> = emptyList(),
        val sort: String = "latest"
    )

    fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        val params = FilterSearchParams()

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> params.copy(genre = filter.toUriPart())
                is StatusFilter -> params.copy(status = filter.state.filter { it.state }.map { it.name })
                is SortFilter -> params.copy(sort = filter.values[filter.state].lowercase())
            }
        }

        return params
    }
}
```

### Using Filters in Search

```kotlin
override fun getFilterList(): AnimeFilterList {
    return AnimeSiteFilters.getSearchFilters()
}

override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList, noToasts: Boolean): Request {
    val params = AnimeSiteFilters.getSearchParameters(filters)

    val url = "$baseUrl/search".toHttpUrl().newBuilder()
        .addQueryParameter("q", query)
        .addQueryParameter("page", page.toString())
        .apply {
            if (params.genre.isNotEmpty()) addQueryParameter("genre", params.genre)
            if (params.status.isNotEmpty()) addQueryParameter("status", params.status.joinToString(","))
            addQueryParameter("sort", params.sort)
        }
        .build()

    return GET(url, headers)
}
```

---

## Internationalization

### Creating Translations

```kotlin
package com.sf.tadami.extension.en.animesite

import com.sf.tadami.lib.i18n.Language
import com.sf.tadami.lib.i18n.Translations

val AnimeSiteTranslations: Translations = mapOf(
    "category_network" to mapOf(
        Language.en to "Network Settings",
        Language.fr to "Paramètres réseau",
        Language.es to "Configuración de red"
    ),
    "pref_base_url_title" to mapOf(
        Language.en to "Override base URL",
        Language.fr to "Remplacer l'URL de base"
    ),
    "error_video_not_found" to mapOf(
        Language.en to "Video not found",
        Language.fr to "Vidéo introuvable"
    )
)
```

### Using Translations

```kotlin
import com.sf.tadami.lib.i18n.i18n

class AnimeSite : ConfigurableParsedHttpAnimeSource<AnimeSitePreferences>(...) {

    private val i18n = i18n(AnimeSiteTranslations)

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return SourcesPreferencesContent(
            title = name,
            preferences = listOf(
                PreferenceCategory(
                    title = i18n.getString("category_network"),
                    // ...
                )
            )
        )
    }

    fun showError() {
        val errorMsg = i18n.getString("error_video_not_found")
        // Use errorMsg
    }
}
```

---

## Best Practices

### 1. Network Requests

**Use appropriate HTTP methods:**

```kotlin
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST

// GET request
val request = GET("$baseUrl/anime", headers)

// POST request with form body
val formBody = FormBody.Builder()
    .add("query", "naruto")
    .build()
val request = POST("$baseUrl/search", headers, formBody)
```

**Handle Cloudflare protection:**

```kotlin
override val client = network.cloudflareClient
```

**Custom headers:**

```kotlin
override val headers = Headers.Builder()
    .add("Referer", baseUrl)
    .add("User-Agent", "Mozilla/5.0...")
    .build()
```

### 2. Error Handling

```kotlin
override fun episodeSourcesParse(response: Response): List<StreamSource> {
    return runCatching {
        val document = response.asJsoup()
        // Parsing logic...
    }.getOrElse { exception ->
        // Log error or return empty list
        emptyList()
    }
}
```

### 3. Parsing HTML

**Using Jsoup:**

```kotlin
val document = response.asJsoup()

// Select single element
val title = document.selectFirst("h1.title")?.text() ?: ""

// Select multiple elements
val episodes = document.select("div.episode").map { element ->
    element.text()
}

// Get attributes
val imageUrl = element.attr("abs:src")  // Absolute URL
val relativeUrl = element.attr("href")  // Relative URL
```

### 4. URL Handling

```kotlin
// Set relative URL (will be combined with baseUrl)
anime.setUrlWithoutDomain("/anime/naruto")

// Get absolute URL
val absoluteUrl = element.attr("abs:src")

// Build URL with query parameters
val url = "$baseUrl/search".toHttpUrl().newBuilder()
    .addQueryParameter("q", query)
    .addQueryParameter("page", page.toString())
    .build()
```

### 5. Video Source Sorting

Implement custom sorting based on user preferences:

```kotlin
override fun List<StreamSource>.sort(): List<StreamSource> {
    val serverOrder = preferences.serverOrder.split(",")

    return groupBy { it.server.lowercase() }
        .entries.sortedWith(compareBy { (server, _) ->
            serverOrder.indexOf(server).takeIf { it >= 0 } ?: Int.MAX_VALUE
        })
        .flatMap { it.value.sortedByQuality().reversed() }
}
```

### 6. Parallel Processing

Use `parallelMap` for concurrent operations:

```kotlin
import com.sf.tadami.utils.parallelMap

val sources = videoUrls.parallelMap { url ->
    runCatching { extractor.videosFromUrl(url) }.getOrNull()
}.filterNotNull().flatten()
```

### 7. Observable Patterns

Use RxJava Observables for async operations:

```kotlin
override fun fetchEpisodesList(anime: Anime): Observable<List<SEpisode>> {
    return client.newCall(GET("$baseUrl${anime.url}"))
        .asObservable()
        .map { response ->
            episodesListParse(response)
        }
}
```

### 8. Avoid Over-Engineering

- Don't add features not requested by users
- Keep parsing logic simple and focused
- Only add error handling for realistic scenarios
- Don't create abstractions for single-use cases

---

## Updating Extensions

### When to Update

Update an extension when:
1. Website structure changes (selectors break)
2. New video servers are added
3. New features are requested
4. Base URL changes
5. Bug fixes are needed

### Update Process

**1. Increment version code in `build.gradle.kts`:**

```kotlin
buildscript {
    extra.apply {
        set("extVersionCode", 2)  // Increment from 1 to 2
        // ...
    }
}
```

**2. Update source code:**

Make necessary changes to selectors, parsers, or logic.

**3. Test changes:**

Build and test the extension thoroughly:

```bash
./gradlew :extensions:en:animesite:assembleDebug
```

**4. Add migration if needed:**

If preferences changed, add migration logic:

```kotlin
private suspend fun preferencesMigrations(): Boolean {
    val oldVersion = preferences.lastVersionCode
    if (oldVersion < BuildConfig.VERSION_CODE) {
        if (oldVersion < 2) {
            // Migrate from version 1 to 2
            dataStore.editPreference(newDefault, NEW_PREFERENCE_KEY)
        }
        dataStore.editPreference(BuildConfig.VERSION_CODE, LAST_VERSION_CODE)
        return true
    }
    return false
}
```

### Common Update Scenarios

**Selector changed:**

```kotlin
// Before
override fun latestSelector() = "div.anime-card"

// After (website changed structure)
override fun latestSelector() = "article.anime-item"
```

**New video server added:**

```kotlin
// Add dependency in build.gradle.kts
dependencies {
    implementation(project(":lib:newserver-extractor"))
}

// Update episodeSourcesParse
override fun episodeSourcesParse(response: Response): List<StreamSource> {
    return videoUrls.parallelMap { url ->
        when {
            "dood" in url -> DoodExtractor(client).videosFromUrl(url)
            "newserver" in url -> NewServerExtractor(client).videosFromUrl(url)  // NEW
            else -> null
        }
    }.filterNotNull().flatten()
}
```

**Base URL changed:**

Update default in preferences:

```kotlin
companion object : CustomPreferences<AnimeSitePreferences> {
    const val DEFAULT_BASE_URL = "https://newdomain.com"  // Changed from old domain
    // ...
}
```

---

## Building and Testing

### Build Extension

```bash
# Build specific extension
./gradlew :extensions:en:animesite:assembleDebug

# Build all extensions
./gradlew assembleDebug

# Build release version
./gradlew :extensions:en:animesite:assembleRelease
```

### Output Location

Built APKs are located at:
```
extensions/{lang}/{source-name}/build/outputs/apk/debug/{source-name}-debug.apk
```

### Installing Extension

1. Build the APK
2. Install the APK in the Tadami app
3. The app will detect and load the extension automatically

### Testing Checklist

- [ ] Latest anime list loads correctly
- [ ] Search returns results
- [ ] Filters work as expected
- [ ] Anime details page displays all information
- [ ] Episode list loads completely
- [ ] Video sources extract successfully
- [ ] Preferences save and apply correctly
- [ ] No crashes or errors in logs

---

## Troubleshooting

### Common Issues

**1. Selectors not matching:**
- Inspect the actual HTML structure
- Use browser DevTools to test CSS selectors
- Check if site uses dynamic content (JavaScript)

**2. Cloudflare blocking requests:**
```kotlin
override val client = network.cloudflareClient
```

**3. Video extraction failing:**
- Check if video host changed their embed structure
- Verify extractor library is up-to-date
- Add error logging to identify issues

**4. Build errors:**
- Ensure all dependencies are correct
- Check for syntax errors
- Verify source ID is unique

**5. Extension not appearing in app:**
- Check AndroidManifest.xml is present (even if empty)
- Verify metadata in build.gradle.kts
- Ensure APK is properly installed

---

## Examples Reference

### Full Extension Examples

Study these well-implemented extensions:

1. **AnimeSama** (`extensions/fr/animesama/`) - Comprehensive example with preferences, filters, multiple extractors
2. **HiAnime** (`extensions/en/hianime/`) - Zoro-based multi-extension
3. **GogoAnime** (`extensions/en/gogoanime/`) - Simple ParsedAnimeHttpSource
4. **AnimeParasyte** (`extensions/fr/animeparasyte/`) - DooPlay-based multi-extension

### Extractor Examples

See `/lib` directory for 30+ extractor implementations.

---

## Resources

### Key Files to Reference

- `api/src/main/java/com/sf/tadami/source/online/` - Base classes
- `api/src/main/java/com/sf/tadami/source/model/` - Data models
- `api/src/main/java/com/sf/tadami/network/` - Network utilities
- `lib/i18n/` - Internationalization
- `common.gradle` - Common build configuration

### Development Tips

1. Start with a simple extension, add complexity as needed
2. Reuse existing extractors whenever possible
3. Test incrementally during development
4. Study similar extensions for patterns
5. Keep code clean and maintainable

---

## Summary

Creating a Tadami extension involves:

1. **Structure**: Create directory with proper file organization
2. **Configuration**: Set up build.gradle.kts with metadata
3. **Implementation**: Extend base classes and implement abstract methods
4. **Extractors**: Use existing video extractor libraries
5. **Preferences**: Add configurable settings (optional)
6. **Filters**: Implement search filters (optional)
7. **i18n**: Add translations for multi-language support (optional)
8. **Testing**: Build, install, and thoroughly test
9. **Updating**: Increment version code and add migrations as needed

The extension architecture provides a powerful, flexible framework for scraping anime websites while maintaining code reuse and consistency across extensions.
