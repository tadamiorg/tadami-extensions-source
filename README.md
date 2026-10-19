# Tadami extensions

The source code of the extensions of [Tadami](https://github.com/tadamiorg/tadami). Their APKs are published
in [tadami-extensions](https://github.com/tadamiorg/tadami-extensions), where the app downloads them.

This repository is also the template of extension repositories: anyone can create their own from it, see
[Make your own extension repository](#make-your-own-extension-repository).

- [What an extension is](#what-an-extension-is)
- [The extension API: extensions-lib](#the-extension-api-extensions-lib)
- [Make your own extension repository](#make-your-own-extension-repository)
- [Create an extension](#create-an-extension)
- [Build and test](#build-and-test)
- [Release](#release)
- [Add a repository in Tadami](#add-a-repository-in-tadami)
- [Keep up with the lib](#keep-up-with-the-lib)

## What an extension is

An extension is a small Android APK that scrapes one anime site for the app: search, the latest episodes
(optional), details, episodes and video streams. The app loads its code, so an extension can only use what the
app gives it: the extension API.

```
core/            the manifest and default icon every extension gets
extensions/      the extensions, extensions/<lang>/<name>
lib/             video host extractors and helpers shared by extensions (lib/voe-extractor, lib/i18n...)
lib-multiexts/   base classes of site families (zoro, dooplay)
template/        the example extension of the template (see below)
common.gradle    the build of every extension: version, lib, source IDs, signing
```

Prerequisites: JDK 17, the Android SDK (`sdk.dir` in `local.properties`, or `ANDROID_HOME`).

## The extension API: extensions-lib

Extensions compile against **extensions-lib**, the API of the app published on JitPack from the app repo
([extensions-lib/README.md](https://github.com/tadamiorg/tadami/blob/develop/extensions-lib/README.md)):

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { compileOnly("com.github.tadamiorg:tadami:lib-1.6.0") }
```

It's `compileOnly`: the app provides the real classes at runtime. `gradle/libs.versions.toml` lists one entry
per lib (`tadami-lib-v1_6`), and `common.gradle` picks it from the extension's `apiVersion`:

| `apiVersion` | Compiles against | Accepted by |
|---|---|---|
| 1.5 (the default, `latestApiVersion` in `gradle/build.versions.toml`) | `lib-1.6.0` | Tadami with lib 1.5 or later |
| 1.6 | `lib-1.6.0` | Tadami with lib 1.6 or later |
| 1.7 | `lib-1.7.0` (not released yet) | Tadami with lib 1.7 or later |

An extension's version name is `<apiVersion>.<extVersionCode>`. An app accepts extensions up to its own lib
version only, so an extension using an API member of a later lib must declare that lib: `SAnime.language` and
`network.webViewResolver` need 1.6. The release reads the APK's code and refuses a missing declaration.
`lib/*` and `lib-multiexts/*` compile against the oldest lib, since every extension can use them.

## Make your own extension repository

1. On GitHub, **Use this template** > *Create a new repository*. The first push runs the **Template setup**
   workflow once, which:
   - sets `tadami.repoId` in `gradle.properties` to your repository (`<owner>/<repo>`),
   - replaces the official extensions with an example one, `extensions/en/example`,
   - replaces this README with a short one, and deletes the `.template` marker.

   If Actions were off when the repository was created, run *Template setup* from the Actions tab.
2. Create the repository your extensions are published to: `<owner>/tadami-extensions` by default, public,
   with a first commit.
3. Set up the release (secrets, see [Release](#release)), make the example your first extension, and release it.

`tadami.repoId` is part of every source ID generated in your repository: never change it once an extension is
released. The release refuses to run in another repository than the official one with the official `repoId`.

## Create an extension

Copy the closest extension (or `template/example`, the bare minimum) to `extensions/<lang>/<name>`, then set its
`build.gradle.kts` extras:

```kotlin
buildscript {
    extra.apply {
        set("extVersionCode", 1)              // goes up by one at every release
        set("extName", "MySite")              // the name the app shows
        set("pkgNameSuffix", "en.mysite")     // <lang>.<name>: the package is com.sf.tadami.extension.en.mysite
        set("extClass", ".MySite")            // the source class (";" between several)
        set("apiVersion", "1.6")              // the lib it needs, see above (1.5 by default)
    }
}
apply(from = "$rootDir/common.gradle")
```

The package `com.sf.tadami.extension.<lang>.<name>` is the extension's identity: it must be unique (the app
can't have two extensions with the same one), and never changes. A new package is a new extension. Its icon
goes in `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` (48 to 192 px), the release publishes the
xhdpi one.

**Source IDs.** The app keys everything on a source's ID: the library, the history, the downloads and the
source's settings. A new source takes the ID generated for it:

```kotlin
class MySite : ConfigurableParsedHttpAnimeSource<MySitePreferences>(
    sourceId = BuildConfig.SOURCE_ID,
    prefGroup = MySitePreferences,
)
// or, for an AnimeHttpSource
override val id: Long = BuildConfig.SOURCE_ID
```

`common.gradle` generates it from `tadami.repoId`, the package and the source class's simple name: the first
8 bytes of the MD5 of `tadami-source/v1/<repoId>/<package>/<class>/<versionId>`, without the sign bit (checked
by `./gradlew checkSourceIdFormula`). So it never changes by itself, and two repositories can't give the same
one. Never reuse an ID, never change one once released: the users' entries would be lost. Hence:
- renaming the source class changes its ID: keep the old name as its key with `set("extSourceKeys", "OldName")`;
- to give a source a new ID on purpose (the site changed so much its old entries are useless), raise
  `set("extSourceVersionId", 2)` (1 by default);
- with several sources (`extClass` ".A;.B"), each one has `BuildConfig.SOURCE_ID_<CLASS>`, e.g. `SOURCE_ID_A`;
- the official sources released before generated IDs keep the explicit ID written in their code
  (`sourceId = 7`): never change those either.

The build writes the ID each source really uses, read from its code, to
`build/outputs/tadami/sources.json` and to the manifest (`tadami.extension.repoId`, `tadami.extension.sourceIds`).

The release also reads two declarations from the code as written, keep them literal:
`override val name: String = "MySite"` and `const val DEFAULT_BASE_URL = "https://..."`.

`claude.md` is a guide to the API (sources, models, preferences, filters, extractors).

## Build and test

Always build the release, signed with your key, even to test an extension locally:

```bash
./gradlew :extensions:en:mysite:assembleRelease
# extensions/en/mysite/build/outputs/apk/release/tadami-en.mysite-v1.6.1.apk
```

Don't test with a debug build. Tadami reserves the IDs of the official sources to extensions signed with the
official key: a debug-signed build of one of them is refused, as a source ID conflict. And a build signed with
another key than the installed extension can't replace it (see below).

`common.gradle` reads the signing values (`KEY_FILE`, `KEY_STORE_PASSWORD`, `ALIAS`) at every build, from the
environment or from `.env`, and signs the release with them:

```bash
keytool -genkeypair -keystore extensions.jks -alias extensions -keyalg RSA -keysize 4096 -validity 36500
```

```properties
# .env, at the root (ignored by git: never commit it, nor the keystore)
KEY_FILE=/path/to/extensions.jks
KEY_STORE_PASSWORD=...
ALIAS=extensions
```

Keep the keystore safe: every update of an extension must be signed with the same key, or users can't install
it, and the key is what users trust (see below).

**Test on a device** with a debug build of Tadami (`com.sf.tadami.debug`, built from the app repo): it takes in
an extension installed with adb. Only the app is a debug build, the extension is the signed release.

```bash
adb install -r extensions/en/mysite/build/outputs/apk/release/tadami-en.mysite-v1.6.1.apk
adb shell am force-stop com.sf.tadami.debug   # then open the app again
```

At every launch, and when a package is installed or replaced, the debug app copies an installed extension
into its storage when its version code is higher, or the same with another content, and loads it. It refuses one
signed with another key than its copy: signed with your release key, your build replaces the extension
installed from your repository. Otherwise uninstall that one in the app first (`adb logcat -s ExtensionsMigration`
shows why it refused).
Without the package manager, copy the APK in yourself:

```bash
adb push ext.apk /data/local/tmp/
adb shell run-as com.sf.tadami.debug cp /data/local/tmp/ext.apk files/extensions/<package>.apk
adb shell run-as com.sf.tadami.debug chmod 444 files/extensions/<package>.apk   # then restart the app
```

**Against an unreleased lib**, publish it from the app repo to your local Maven repository, then build with
its version:

```bash
./gradlew :extensions-lib:publishToMavenLocal        # in the app repo: com.github.tadamiorg:tadami:lib-<version>
./gradlew -Ptadami.localLib=lib-1.7.0 :extensions:en:mysite:assembleRelease
```

Every module then compiles against that version, from `mavenLocal()` first. JitPack also builds any commit of
the app repo: `com.github.tadamiorg:tadami:<commit>`.

## Release

Run the **Release extensions** workflow from the Actions tab with the extensions to release, as
`<lang>.<name>` separated by spaces or commas (e.g. `en.animepahe fr.franime`). It builds only those, then
commits their APK, icon and index entry to the extensions repository (`index.json` and `index.min.json`, the
one the app reads), and writes its `repo.json`. The other extensions stay as they are, including the ones left
out of the index on purpose. Releasing an extension lists it again. Tick *dry run* to build and check
without pushing.

Bump `extVersionCode` first. The release refuses:
- a version code that isn't above the published one, or an APK signed with another key than the published one;
- an extension using an API member of a later lib than its `apiVersion`;
- a source whose ID isn't the published one any more, unless *allow_id_change* is ticked (its users lose their
  entries);
- the official `tadami.repoId` in another repository than the official one.

`repo.json` describes the repository to the app: `{"meta": {"name", "shortName", "website",
"signingKeyFingerprint"}}`, the fingerprint being the SHA-256 of the certificate the APKs are signed with.
A release signed with another key than the one it names is refused.

Repository secrets (Settings > Secrets and variables > Actions; how to create each one:
[CI_SETUP.md](https://github.com/tadamiorg/tadami/blob/develop/docs/CI_SETUP.md) in the app repo):
- `EXTENSIONS_KEYSTORE_BASE64`: the keystore (`KEY_FILE` in `.env`), base64-encoded
- `EXTENSIONS_KEY_STORE_PASSWORD`: its password (`KEY_STORE_PASSWORD`), also the key's
- `EXTENSIONS_KEY_ALIAS`: the key alias (`ALIAS`)
- `EXTENSIONS_REPO_TOKEN`: a fine-grained token with *Contents: Read and write* on the extensions repository

Variables (same page, *Variables* tab), all optional:
- `EXTENSIONS_REPO`: the repository to publish to, `<owner>/<repo>`: `tadamiorg/tadami-extensions` for
  tadamiorg, `<owner>/tadami-extensions` otherwise
- `EXTENSIONS_REPO_NAME`: its name in `repo.json`: Tadami for tadamiorg, the owner otherwise (the app doesn't
  show a name containing "tadami", "official" or "officiel", but the official repository's)
- `EXTENSIONS_REPO_SHORT_NAME`: its short name (the name by default)
- `EXTENSIONS_REPO_WEBSITE`: its website (this repository on GitHub by default)

## Add a repository in Tadami

The release of Tadami after 1.11.0 adds an **Extension repositories** screen (the menu of the Extensions tab,
or Settings > Advanced). *Add a repository* takes the GitHub URL of the extensions repository
(`https://github.com/<owner>/tadami-extensions`, its `main`, `master` or `repo` branch) or the URL its
`index.min.json` is under. The app reads its `repo.json` and asks to confirm, showing the fingerprint of the
repository's signing key with *Trust the extensions signed with this key*. The app runs the code of an
extension only once its key is trusted: an extension signed with an untrusted key is listed as *Untrusted*, and
can be trusted from its details, which trusts every extension signed with that key.

## Keep up with the lib

When a new extensions-lib is released (a `lib-X.Y.Z` tag of the app repo, see its
[releases](https://github.com/tadamiorg/tadami/releases)):
- add it to `gradle/libs.versions.toml` (`tadami-lib-vX_Y = "com.github.tadamiorg:tadami:lib-X.Y.Z"`) and map
  its `apiVersion` to it in `common.gradle`, or update the entry of a patch release;
- an extension moves to it only when it needs one of its new members, since older apps don't accept it: set
  its `apiVersion` to `X.Y` with that change;
- the libs never remove a member extensions use: an extension built against an older lib keeps working in the
  new apps.

In a repository created from the template, pull these changes from the template:

```bash
git remote add template https://github.com/tadamiorg/tadami-extensions-source.git
git fetch template
git merge template/main --allow-unrelated-histories   # the first time; keep your extensions/ and README.md
```
