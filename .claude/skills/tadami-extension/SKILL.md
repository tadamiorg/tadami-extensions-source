---
name: tadami-extension
description: Create a new Tadami extension (an anime site scraper APK) or update/fix an existing one in tadami-extensions-source, from probing the site to building, testing on a device and releasing. Use this whenever the user wants to add a site or source, or when an extension is broken in any way (no results, empty recents, 403/404, Cloudflare loops, NullPointerException, "could not scrape source", videos not loading or not playing, an extractor/video server that stopped working), when a site moved to a new domain/base URL, when a video server or extractor should be added, or when an extension needs a version bump or a release, even if the user doesn't say "extension".
---

# Creating and updating Tadami extensions

An extension is a small Android APK, package `com.sf.tadami.extension.<lang>.<name>`, that the Tadami app
loads to scrape one anime site: search, recents (optional), details, episodes, and video streams. It compiles
against stub classes in `:api` (the real ones live in the app), so it must only use what `:api` exposes.

`claude.md` at the repo root is a long API guide. Use it for concepts, but trust the real code when they
disagree: some of its snippets are outdated (`GET` is `com.sf.tadami.network.GET`, preferences implement
`setPrefs(newValue, preferences: MutablePreferences)`, there is no AnimeParasyte extension, and the APK
path below is the real one).

## The loop: scrape → fix → build → install → test

Every create and every fix runs this loop until the whole path works on a device:

1. **Scrape the live site.** Fetch the real pages and APIs the extension uses: curl with the app's
   mobile UA, or `scripts/chrome_probe.mjs` when Cloudflare or JavaScript gets in the way. Check
   selectors with `DOMParser` against the fetched HTML, compare JSON fields with the DTOs, and follow
   the player/embed chain down to a playable stream (`references/site-probing.md`).
2. **Fix** the code against what you just saw.
3. **Build** `./gradlew :extensions:<lang>:<name>:assembleRelease` (JDK 17) after every change, and fix
   compile errors before going further.
4. **Install/update**: `adb install -r` the new APK on the emulator or device, then restart the app
   (`adb shell am force-stop com.sf.tadami.debug` and launch it again). The app swaps an extension live
   only when its version code goes up, so after a same-code reinstall the old code runs until the app
   restarts. Keep `extVersionCode` at the version you'll ship and iterate at that code (see the
   version code rule).
5. **Test** the path end to end: recents (if the site has them) → search → details → episodes →
   playback. If a step still fails, read its logcat error and go back to 1 for that step.

Never guess selectors, endpoints or JSON fields from memory or from the old code: the site is the
source of truth, and the old code is what broke. Sites change silently. The device logs say *what*
failed; scraping the site says *why*.

## Pick a model to copy

Start every new extension, and most fixes, from the closest existing one. They encode patterns that
work with the app.

| Extension | Use it for |
|---|---|
| `extensions/fr/voiranime` | HTML site scraped with Jsoup selectors, several video hosts through `lib/` extractors |
| `extensions/fr/ianimes` | Plain HTML site with relative links, search by GET with a genre filter, `language` per entry, many hosts with small custom extractors (LuluStream, Vidara, Vidsonic, Odysee), VOE mirror fallback, the bare migration pattern |
| `extensions/en/animepahe` | JSON API, a custom extractor (`KwikExtractor`), cached lookups, strict referers |
| `extensions/fr/franime` | JSON API with obfuscated player URLs decoded in Kotlin, server priority with a normalized stored order, episode tooltip |
| `extensions/en/kickassanime` | JSON API with its own extractor |
| `extensions/fr/animesama` | Large HTML site with filters and many hosts: players routed by parsed host (`hostOf()`), codes retried on mirror domains when one is dead or DNS-blocked (`mirrorSources()`: VOE, VidHide, Vidmoly), and the complete server-list migration (merged keys, removed ones, untouched defaults moved to the new default) |

Video host extractors live in `lib/` (`ls lib`: dood, filemoon, streamwish, vidmoly, voe, uqload, vk,
sibnet, sendvid, mp4upload, okru, ... plus `unpacker` for packed JS and `playlist-utils` for HLS).
Reuse one before writing your own. `lib-multiexts/` holds base classes for site families (zoro, dooplay).

Each extension has the same files: `build.gradle.kts`, an empty-ish `AndroidManifest.xml`,
`res/mipmap-*/ic_launcher.png`, and under `src/com/sf/tadami/extension/<lang>/<name>/`: the source class,
`<Name>Preferences.kt`, `<Name>PreferencesScreen.kt`, `<Name>Translations.kt` (en + fr), plus DTOs or
extractors when needed.

## Rules that the app and the release depend on

These come from real breakages; each one explains what goes wrong when it's broken.

- **Search is mandatory, Recents is optional.** Tapping a source in the Sources tab opens its Search
  screen, and global search queries every source, so search has to work; if the site searches through
  JavaScript, a POST or an API, find that endpoint by scraping. A site without a usable "latest" list
  gets `override val supportRecent = false` in the source class (it defaults to true, from
  `AnimeCatalogueSource`): the app then hides the Recents button. Don't fake a recents page from
  something else. The `latest*` members are still abstract, so implement them with
  `throw UnsupportedOperationException("Not used")`; they're never called.

- **Keep the three declarations literal**: `sourceId = N` in the constructor call,
  `override val name: String = "Name"`, and `const val DEFAULT_BASE_URL = "https://..."` in the
  preferences. The release workflow generates the published index entry by reading exactly these.
  A computed value means a failed release or a broken entry.
- **`sourceId` is unique and never changes.** The user's library entries are keyed by it; changing it
  orphans them. Get a free one with `scripts/ext_check.py ids`.
- **`pkgNameSuffix` is `<lang>.<name>` and never changes.** It's the package name. A new one is a new
  extension for the app, and the old one stays installed.
- **`extVersionCode` goes up once per release, not once per test build.** It must be above the
  published code, or the release is refused: set it to the published code + 1 (1 for a new extension)
  and test at that code. The app ignores a reinstall with the same version code until it restarts, which
  looks exactly like "my fix doesn't work", so force-stop and reopen the app after each `install -r`.
  Bumping the code for test builds instead leaves the device on a higher code than the one you ship:
  going back down needs an uninstall (emulator only), and the source's stored `LAST_VERSION_CODE` stays
  at the test code, so the real version's migrations won't run on that device.
- **Stay inside the app's extension lib range.** The version name is `<apiVersion>.<extVersionCode>`.
  `apiVersion` defaults to `latestApiVersion` in `gradle/build.versions.toml` (1.5), and the app accepts
  1.3 to 1.6. Outside it, the extension shows as unusable with the cause. Set `apiVersion` only when the
  extension uses an API member added in a later lib (1.6: `SAnime.language`): older apps only accept
  lib versions up to their own max, which keeps them from loading code they'd crash on. The release
  workflow reads the APK's code and refuses an extension that uses such a member without the lib
  version it needs.
- **The language of an entry goes in `language`, not in the title.** When the site lists each language
  as its own anime (VF and VOSTFR entries), set `language = "VF"` (short and uppercase: `VF`, `VOSTFR`,
  `VO`) on the `SAnime` in every place it's built (recents, search, details), keep the title clean,
  and set `apiVersion` to `1.6`. The app shows the language as a badge on every screen, so a long
  title can't hide it. Strip any language marker the site puts in its own titles
  (`Overgeared (VF)` gives title `Overgeared` and language `VF`).
- **The season goes at the start of the title, the bare name in `rawTitle`.** "Saison 2 - Overgeared"
  (French sites) or "Season 2 - Overgeared" (English ones), never "Overgeared Saison 2" / "Overgeared
  2nd Season": grid cards, notifications and history cut long titles at the end, so a trailing season
  gets hidden. Build the same string in recents, search and details for the same url: the app stores the
  listing's title, then the details' one on open, so different formats flip the title back and forth.
  Move only an explicit season marker at the end ("Season 2", "2nd Season", "Saison 2", the site's own
  "S2"): a bare trailing number or roman numeral is often part of the name ("Mob Psycho 100", "Kaiju
  No. 8", "Lupin III", "86"). Set `rawTitle` to the name the site's search finds (the bare name, or the
  site's untouched title when its search needs it, as on AnimePahe): the app uses `rawTitle ?: title` for
  migration and for the search behind a tap on the title. Code that reads a stored title back must read
  `rawTitle ?: title`, since older entries keep their old title until a pull-to-refresh of their details.
- **Episodes come newest first.** The app stores each episode's position in the returned list as
  `sourceOrder` (0 = newest) and finds the "next episode" from it. Reverse a site that lists oldest
  first, and number episodes so the order still reads right across seasons and films.
- **Resolve relative links before `setUrlWithoutDomain`.** It keeps the path and query of what it's
  given, so a relative `liste.php?manga=x` comes out without its leading slash and `baseUrl + url`
  becomes `https://site.euliste.php?...`. Use Jsoup's `element.absUrl("href")` (the document needs its
  URL, which `response.asJsoup()` sets), then `setUrlWithoutDomain`.
- **Release through the workflow, never by hand.** The published repo (tadamiorg/tadami-extensions) is
  updated by the "Release extensions" GitHub workflow, run with `<lang>.<name>`; it has a dry-run
  option. Some extensions are kept out of `index.min.json` on purpose because they're broken or
  unmaintained. Releasing one lists it again, so only release what works. Don't commit, push or run
  the workflow unless the user asks.
- **Never print `.env`.** It holds the signing secrets; refer to keys by name only.

## Network rules

These come from sites that broke in practice.

- **Use `network.client`.** It carries the Cloudflare interceptor, which solves challenges in a
  hidden WebView with the app's mobile User-Agent. `network.cloudflareClient` is the same client under
  an older name (VoirAnime uses it), not a different one. Don't set a User-Agent: a desktop UA makes
  Cloudflare Turnstile loop forever inside the Android WebView. If a preference lets the user override
  the UA, apply it only when set (see FrAnime's `userAgentOverride`).
- **Not every 403 is a challenge.** `cf-mitigated: challenge` or a "Just a moment" page is one, and the
  interceptor handles it. "Request forbidden by administrative rules" or "Attention Required" is a
  firewall rule: no WebView can pass it, so find another endpoint or the missing header.
- **"Connection refused" to 127.0.0.1 is DNS blocking**, not a dead host: some ISPs resolve streaming
  hosts to 127.0.0.1 or 0.0.0.0 (here: voe.sx, doods.pro, vidmoly.biz). Users on such networks lose those
  hosts too. Look for mirror domains that serve the same codes and fall back to them: VOE codes play on
  any VOE domain, vidmoly.biz codes play on vidmoly.org (`references/site-probing.md`, section 2).
- **Referers matter, down to the trailing slash.** Kwik accepts `https://animepahe.pw/`, refuses
  `https://animepahe.pw`, and its CDN wants `https://kwik.cx/`. Set the referer a browser would send
  (usually the page origin with a slash) and put the player's referer in `StreamSource.headers`.
- **Some video hosts reject HTTP/1.1.** Kwik and its CDN return 403 to HTTP/1.1. OkHttp speaks HTTP/2,
  and the app's player now plays through OkHttp. When a stream 403s, compare
  `curl --http1.1` and `curl --http2` before blaming headers.
- **Respect rate limits.** A 429 from Cloudflare blocks for a minute or two. Cache what you can (AnimePahe
  caches anime sessions and takes them from list results) and show a readable "wait a minute" error.
- **Decode before reaching for a WebView.** Obfuscated player URLs are usually reversible in a few lines
  of Kotlin (FrAnime: base64 → hex → XOR). `network.webViewResolver` loads a page in a hidden WebView;
  it's slow and fragile, so keep it as a last resort.
- **Fail one stream, not the episode.** Wrap each server or quality in `runCatching` and log failures,
  so one dead host doesn't hide the working ones. Throw only when nothing works. Some `lib/` extractors
  fail silently (`VoeExtractor` returns an empty list, `DoodExtractor` swallows its errors), so also log
  a line when an extractor returns nothing (`Log.w(TAG, "No stream from $server: $url")`), or a missing
  host leaves no trace in logcat.
- **Readable errors, closed responses.** No `!!` on scraped data: a missing element should throw an
  `IOException("Kwik player script not found")`, not a bare NullPointerException the user can't act on.
  Close responses you don't return (`execute().use { }`).
- **HLS with AES segments** whose IV is the segment file number (Kwik) is handled by the app; nothing to
  do in the extension.

## Preferences and migrations

Preferences live in the app's DataStore, one store per source, and a stored value always wins over
the code's default. Changing only `DEFAULT_BASE_URL` or `DEFAULT_PLAYER_STREAMS_ORDER` does nothing for
existing users: they keep the old domain or the old server list. Whenever a change touches what the
preferences hold (base URL, video servers, any default), update the preferences and migrate stored
values in the same change.

The pieces, as in AnimePahe and FrAnime:
- `<Name>Preferences` (a data class plus a `CustomPreferences<T>` companion): the keys
  (`stringPreferencesKey("base_url")`, ...), the defaults, `transform(preferences)` to read the store,
  and `setPrefs(newValue, preferences: MutablePreferences)` to write it.
- `LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))`: the
  version the stored values were last migrated to.
- `preferencesMigrations()` in the source class, called from `init` with `runBlocking`. Read the old
  version from the store itself (`dataStore.data.first()[LAST_VERSION_CODE] ?: 0`; `preferences` may
  still hold defaults this early), migrate, then store `BuildConfig.VERSION_CODE` with
  `dataStore.editPreference(value, key)`. IAnimes shows the bare pattern; FrAnime adds the server-order
  normalization below. Have it return true only when a migration actually ran (not on a fresh install,
  not when the stored version is already current), since the caller logs "migrated" when it does.

**Gate each migration on the version it shipped in** (`if (oldVersion in 1 until N)`, with N the new
`extVersionCode`), so it runs once per user, and so later releases still apply it to users who skipped
versions. `oldVersion == 0` is a fresh install: nothing to migrate.

- **Base URL changed**: update `DEFAULT_BASE_URL`; the release also publishes this literal as the index's
  baseUrl. Migrate the stored value only if it is an old default, and keep a URL the user typed:
  ```kotlin
  if (oldVersion in 1 until 8) {
      val stored = dataStore.data.first()[AnimePahePreferences.BASE_URL]
      if (stored == null || stored.trimEnd('/') in OLD_DEFAULT_BASE_URLS) {   // e.g. setOf("https://animepahe.com")
          dataStore.editPreference(AnimePahePreferences.DEFAULT_BASE_URL, AnimePahePreferences.BASE_URL)
      }
  }
  ```
- **Video servers added, removed, renamed or merged**: never change the server list without migrating
  the stored order in the same release. Users who never touched the setting have it stored too (an
  earlier migration or the preference screen wrote it), so a new server would never show up for them
  and a removed one would stay listed. Update every place that knows the list, or they drift apart:
  - the default order map (`DEFAULT_PLAYER_STREAMS_ORDER`, keyed by the lowercased `server` the
    extractors produce: check each extractor's `server =`, e.g. `VidmolyExtractor(..., server = "AnsEmbed")`)
  - the preference screen entry built from it (`ReorderStringPreference` with `items`/`defaultValue`)
    and any labels in the translations
  - the code mapping player URLs or names to servers and extractors (FrAnime's `serverOf()` and
    `SUPPORTED_PLAYERS`, AnimeSama's and IAnimes' `hostOf()`)
  - the `sort()` override, which must put a server missing from the order last:
    `order.indexOf(server).takeIf { it >= 0 } ?: Int.MAX_VALUE` (a bare `indexOf` gives -1, so first)

  Then normalize the stored order inside `transform()`, as AnimeSama's `normalizedStreamsOrder()` does:
  map merged or renamed keys to their new key first (`MERGED_PLAYERS = mapOf("callistanise" to "vidhide",
  "smoothpre" to "vidhide")`), keep the user's order of the servers that still exist (`distinct()` puts a
  merged server where the first of its old keys was), drop removed ones, append new ones. Without the
  map a rename reads as a removal plus an addition and loses its position.

  The migration, in `preferencesMigrations()` (AnimeSama v31 is the complete example):
  1. Read the old version and the stored order from `dataStore.data.first()`, not from `preferences`.
  2. When the default order itself changes, move users who never changed it to the new default: keep the
     earlier defaults, including the forms earlier migrations completed them to, in a
     `LEGACY_DEFAULT_PLAYER_STREAMS_ORDERS` set, and gate it with `oldVersion in 1 until N`. A custom
     order is never reset.
  3. Write the normalized order back (`dataStore.editPreference(transform(prefs).playerStreamsOrder,
     PLAYER_STREAMS_ORDER)`), so the preference screen shows the merged, removed and new servers. Writing
     only this key is safer than `editPreferences(transform(prefs), ...)` (FrAnime), which also stores
     every other default (base URL, user agent) and pins it for later releases.

  Check the migration on the order strings before building: an old default becomes the new default, and
  a custom order such as `smoothpre,uqload,sibnet` becomes `vidhide,uqload,sibnet,` followed by the new
  servers.
- **Test the migration like a user upgrading**: install the old published version, set the preference
  (or leave the old default), install the new build over it, and check the value was migrated, not
  reset. For the base URL, a custom URL must survive; for servers, a custom order must keep its
  relative order.

## Workflow A: create an extension

1. **Scrape the site first** (`references/site-probing.md`). Fetch its search, its recents if it has a
   latest list (otherwise the extension is search-only, see the rules), an anime page, an episode page
   and its players, live, and work out how each is served: HTML or JSON API, Cloudflare, rate limits,
   obfuscation. This decides the model to copy; selectors and endpoints come from these responses, not
   from another extension.
2. **Survey the video hosts across several anime of different eras.** Sites switch players over the
   years, so one episode shows only part of them: a show from 2010 often streams from other hosts than
   this season's. Scrape the episode/player pages of a currently airing anime, one from a few years ago,
   an old one (10+ years), a movie, and a dub (VF/English dub) if the site separates them. List every
   host or server name you see, follow each one down to a playable stream, and map it to a `lib/`
   extractor, or note that none exists. Support as many hosts as the existing extractors allow
   (`references/site-probing.md`, "Survey the video hosts"). `scripts/host_survey.py` does the
   counting for plain HTML sites: give it anime pages and an episode-link regex, and it lists the hosts
   of the first, middle and last episodes with an extractor guess and DNS blocks.
3. **Choose identity**: `<lang>` (en/fr), `<name>` (lowercase), display name, `sourceId` from
   `scripts/ext_check.py ids`, `DEFAULT_BASE_URL`.
4. **Scaffold by copying the closest model**, renaming packages, classes and keys. Start at
   `extVersionCode` 1 and keep it there through testing. Add `implementation(project(":lib:<extractor>"))` for each host and `:lib:i18n`.
   Give every user-facing string an en and fr translation. Expose at least the base URL override in
   preferences, so users can follow a domain change without an update, and include the
   `LAST_VERSION_CODE` key and `preferencesMigrations()` from the start, so later updates can migrate
   stored values.
5. **Icon**: `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` (48/72/96/144/192 px). The
   release publishes the xhdpi one. Use the site's own square icon: the `apple-touch-icon`, the icons in
   its web manifest or `<link rel="icon" sizes=...>`, or `/apple-touch-icon.png`. Declared icons
   sometimes 404, so try them all and take the largest square one, then resize it
   (`references/site-probing.md`, section 10). Ask the user only if the site has none; don't invent a logo.
6. **Build** with `assembleRelease` and fix compile errors, then run `scripts/ext_check.py check`.
7. **Install** the APK on the emulator and **test** the full path: recents (if supported) → search →
   details → episodes → playback. Whenever a step fails, **re-scrape the site for that step** and compare it with
   what the code parses, then fix, rebuild, `install -r`, restart the app and retest.
   Repeat until the whole checklist passes.

## Workflow B: update or fix an extension

1. **Reproduce and read the error.** Run the failing step in the app and read `adb logcat` for the app's
   pid (`references/device-testing.md`). The failing step (recents, search, details, episodes, sources,
   playback) and its stack trace tell you *what* broke.
2. **Scrape the live site for that step** to learn *why*: fetch the exact URL or API the code requests,
   check its selectors with `DOMParser` against the fetched HTML, compare the JSON with the DTOs, and
   follow the player chain to the stream, testing referers and HTTP/2 (`references/site-probing.md`).
   Expect a new domain, new markup, a renamed field, or a host that moved or now needs a header. Don't
   reason from the old code alone: it encodes what the site used to be. When the problem is in video
   sources or playback, or you touch the server list, redo the host survey across anime of different
   eras (Workflow A, step 2): the site may have added hosts the extension doesn't know yet.
3. **Make the smallest fix** matching what you saw, in the extension's existing style. Keep `sourceId`,
   `pkgNameSuffix` and preference keys stable.
4. **Update the preferences when the fix touches what they hold**: a new domain, a server added,
   removed or renamed, a changed default. Change the default, the preference screen, the translations
   and the server mapping/sorting together, and add a version-gated migration for stored values
   (see "Preferences and migrations"), or existing users keep the broken value.
5. **Build** with `assembleRelease` and fix compile errors before anything else.
6. **Update the extension**: set `extVersionCode` to the published code + 1 (once), then
   `adb install -r` the new APK. The first install at the new code is picked up live; later rebuilds at
   the same code need a force-stop and relaunch of the app. If you added a migration, install the old
   published version first and upgrade over it.
7. **Re-test end to end**, not only the step that was broken: recents (if supported) → search → details → episodes →
   playback, plus the migrated preference if any. A fix in one step often exposes the next breakage.
   If a step still fails, go back to step 2 for it.

## Build and check

```bash
# JDK 17 (on this machine: C:\Users\sebas\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2)
export JAVA_HOME=<jdk17>
./gradlew :extensions:<lang>:<name>:assembleRelease     # signed with the key from .env
# -> extensions/<lang>/<name>/build/outputs/apk/release/tadami-<lang>.<name>-v<api>.<code>.apk
python .claude/skills/tadami-extension/scripts/ext_check.py check <lang>.<name>
```

Without the signing key, `assembleDebug` builds an APK for local testing only. It's signed with the debug
key, so it can't replace a release-signed install. `ext_check.py check` verifies what the release relies
on: the three declarations, a unique id, the package suffix, the icon, and a version code above the
published one. It also notes a published source id that differs from the code's: the code's id is the
real one (the app and the users' libraries use it) and the release republishes it, so never change
`sourceId` to match the index.

## Test on a device

Read `references/device-testing.md` for commands. In short: prefer the emulator. Install the debug app
(`com.sf.tadami.debug`, built from the sibling `tadami` repo) and the extension APK with
`adb install -r`, then drive the app with `scripts/adb_ui.py` and watch logcat. The user's phone is
theirs. Don't uninstall or wipe anything there, and don't tap while they're using it.

Two things look like failures and aren't:
- **Old titles or episode names after an update.** The app stores an anime's details the first time
  its page opens and re-fetches them only on a pull-down refresh of that page (library updates don't),
  so anime opened with the old code keep their old title, in Library and History too, until you pull
  down on their details page. Screens already loaded keep old values until the app restarts. Test fixes
  on an anime you haven't opened yet, or pull down first.
- **"OBSOLETE" on your extension.** The app labels any installed extension that isn't in the published
  index that way, so a new or locally built one shows it until it's released. It still works.

Checklist, each verified on screen or in logs and not assumed:
- Search returns results, and a result opens (always required; tapping the source opens Search)
- Recents list loads, if the extension supports it (the first load may take ~15 s while Cloudflare is
  solved); with `supportRecent = false`, check the Recents button is hidden instead
- Details page shows title, status, synopsis, genres; the episode list loads
- An episode plays: the position advances, not just a player on screen. Play at least two anime from
  different eras, and check their source lists show the hosts the survey found
- For HLS, seek forward and leave and reopen the episode to resume mid-way; that's where segment
  problems show up
- No new errors for the app's pid in logcat

## Report back

Tell the user what you changed and why, the new version (`<api>.<code>`), what you tested and how, and
what you couldn't test, such as casting, a host you couldn't reach, or the release itself. Point out
anything they need to do: an icon to supply, the release workflow to run, a secret to set.
