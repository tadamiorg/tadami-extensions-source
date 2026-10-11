# Testing an extension on a device

Extensions only run inside the app, so a build that compiles proves little. Test on a device: the
emulator first, the user's phone only when they want it.

## Contents
1. adb and devices
2. The emulator
3. Installing the app and the extension
4. Driving the app (`adb_ui.py`)
5. Logs
6. What to verify
7. Digging into playback
8. The user's phone

## 1. adb and devices

adb is in `<Android SDK>/platform-tools` (Windows: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`).
`adb devices -l` lists what's connected. With more than one device, every command needs `-s <serial>`;
`adb_ui.py` reads `ANDROID_SERIAL` or `--serial`.

In Git Bash, prefix adb commands that contain device paths with `MSYS_NO_PATHCONV=1`, or Bash rewrites
`/sdcard/...` into a Windows path. Python and PowerShell don't have this problem.

## 2. The emulator

```bash
EMU="$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe"     # or $ANDROID_HOME/emulator/emulator
"$EMU" -list-avds
"$EMU" -avd Pixel_8_Pro_API_34 -no-snapshot -no-boot-anim -memory 3072 &   # run it in the background
until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 5; done
```

It needs about 3 GB of free RAM: check first, since it has been killed for low memory before. Stop it
with `adb -s emulator-5554 emu kill` when done, or leave it running if the user is still testing.
The emulator's Chrome WebView passes Cloudflare like a phone does.

## 3. Installing the app and the extension

The test app is the debug build of the sibling repo `../tadami` (`com.sf.tadami.debug`), so it doesn't
collide with the user's release app (`com.sf.tadami`):

```bash
adb -s emulator-5554 install -r -t ../tadami/app/build/outputs/apk/debug/tadami.apk   # build it there if missing
adb -s emulator-5554 install -r extensions/<lang>/<name>/build/outputs/apk/release/tadami-<lang>.<name>-v<api>.<code>.apk
adb -s emulator-5554 shell dumpsys package com.sf.tadami.extension.<lang>.<name> | grep versionName
```

- **Fresh app install** (or after `connectedAndroidTest`, which uninstalls the app): onboarding comes
  first. Next → Select a folder → USE THIS FOLDER → ALLOW → Next → Next → Get started.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: the installed extension was signed with another key (e.g. a
  debug build). On the emulator, `adb uninstall com.sf.tadami.extension.<lang>.<name>` and install
  again. Never do that on the user's phone without asking.
- **The app keeps the old code after a reinstall**: the version code didn't go up. The app only swaps an
  extension live when its version code increases; logcat then shows
  `ExtensionManager: Registering updated extension ...`. For test builds, keep the version code you'll
  ship and restart the app after each install instead of bumping it:
  ```bash
  adb install -r <apk> && adb shell am force-stop com.sf.tadami.debug
  adb shell monkey -p com.sf.tadami.debug -c android.intent.category.LAUNCHER 1
  ```
  Codes bumped for testing stay behind: the device can't go back down without an uninstall, and the
  source's stored `LAST_VERSION_CODE` keeps the highest code that ran, so migrations gated below it
  won't run there again. On the emulator, uninstall the extension to reset both.
- **Old titles and episode names after an update**: the app stores an anime's details the first time
  its page opens and re-fetches them only on a pull-down refresh of that page. Anime opened with the old
  code keep their old title (in Library and History too) until you pull down on their details page, and
  already loaded screens keep old values until the app restarts. Judge a fix on an anime you haven't
  opened yet, or pull down first.
- **"OBSOLETE"** in the Extensions tab means the installed extension isn't in the published index. Every
  new or locally built extension shows it until it's released; it still works.

## 4. Driving the app

```bash
S=.claude/skills/tadami-extension/scripts
export ANDROID_SERIAL=emulator-5554
adb shell monkey -p com.sf.tadami.debug -c android.intent.category.LAUNCHER 1    # launch
python $S/adb_ui.py tap '^Browse$'
python $S/adb_ui.py texts                  # what's on screen: text, desc, clickable, enabled, bounds
python $S/adb_ui.py tap '^Recents$'        # first match wins: the first source's Recents
python $S/adb_ui.py wait 'Episode 1' --timeout 60
python $S/adb_ui.py shot recents.png       # look at it to judge layout
python $S/adb_ui.py tap-xy 972 247         # an icon without a label, at its screenshot position
python $S/adb_ui.py type 'death note'      # into the focused field; clear empties it
python $S/adb_ui.py player --advance 5     # position / duration, then again 5 s later (exit 1 if stuck)
```

Navigation: Browse → Sources lists each source with Recents (and settings); tapping a source opens its
search. The Extensions tab lists installed extensions with their version and any problem label.

Icons in the top bars (search, filter, settings) have no usable label: their description is `stub` or
empty, so `tap` can't find them by text. Take their bounds from `texts` (the `desc='stub'` lines, left to
right) or a screenshot, and use `tap-xy`. Where things are:
- **Source settings**: the gear to the right of a source's "Recents" in Browse → Sources.
- **Search**: tapping a source opens its search screen; the magnifier at the top right opens the field.
  Search filters are behind the floating button.
- **Details**: the episode list is below the synopsis and genres. The floating button can say "Start"
  for an anime outside the library even when it resumes.
- **Player**: a single tap shows or hides the controls; a double tap on either side seeks. The player's
  buttons have descriptions, so `tap 'Video Settings'` opens the source list. With that dialog open,
  Back closes the player, not the dialog: tap outside the dialog instead.

`player` reads the position and duration labels (`'00:34'` / `'23:36'`), tapping once to show the
controls when they're hidden. To seek, tap the seek bar between the two time labels with `tap-xy`.

When `tap` finds nothing, run `texts`: the label may differ (case, translation: a phone in French shows
"Récents"), or the screen hasn't loaded yet (`wait`).

## 5. Logs

```bash
PID=$(adb shell pidof com.sf.tadami.debug | tr -d '\r')
adb logcat -c                                   # before reproducing, for a clean read
adb logcat -d -v time --pid=$PID | grep -E "Exception|Caused by|System.err|ExoPlayerImplInternal|Playback error|<ExtName>|LoadExtension|ExtensionManager"
```

- Extension exceptions appear as `System.err` stack traces with the extension's file and line.
- Playback failures appear as `ExoPlayerImplInternal: Playback error` with the cause (`Response code: 403`
  points to headers or HTTP version; `ParserException ... Loading finished before preparation is
  complete` means the media couldn't be parsed).
- Phones rotate logs fast. To catch an intermittent issue, stream to a file while reproducing:
  `adb logcat -v time --pid=$PID > run.log`.
- `java.net.ConnectException: Failed to connect to <host>/127.0.0.1` means the device's DNS blocks that
  host (site-probing section 2). Confirm with `adb shell ping -c 1 <host>`: an answer from 127.0.0.1 or
  0.0.0.0 is a block, not a dead host.
- A host that never shows up in the source list, with nothing in the log: some `lib/` extractors return
  nothing without logging (VOE, Dood). The extension should log a line when an extractor returns no
  stream; if it doesn't, add one before digging further.

## 6. What to verify

Check each step on screen or in logs rather than assuming it worked:

1. **Search** (required): tapping the source opens Search; a real query returns results, and a result
   opens.
2. **Recents** (only if the extension supports them): the grid fills. The first load may take ~15 s
   while Cloudflare is solved in the background. With `supportRecent = false`, the source shows no
   Recents button instead.
3. **Details**: title, status, synopsis and genres show; the episode list loads with sensible numbers.
4. **Playback**: a source plays and the position advances. Open the source list (video settings) to
   see which servers were found.
5. **HLS**: seek forward, then leave and reopen the episode so it resumes mid-way. Problems with
   segments or keys only show up there.
6. **Errors**: no new exceptions for the app's pid. Error screens should show readable messages, not a
   bare exception name.

## 7. Digging into playback

The debug app's private data is readable with `run-as`, which helps when a stream downloads but won't
play:

```bash
adb shell run-as com.sf.tadami.debug ls cache/player_network_cache          # cached media spans
adb exec-out run-as com.sf.tadami.debug cat databases/exoplayer_internal.db > exo.db
# exo.db table ExoPlayerCacheIndex<uid>: id -> URL; cache files are <id>.<position>.<time>.v3.exo
```

Compare a cached segment with a fresh download (see site-probing section 7) to tell network problems
from parsing problems.

## 8. The user's phone

The user may test on their own phone over wireless debugging. Its port changes whenever wireless
debugging restarts; find it with `adb mdns services` and use the listed serial
(`adb-<id>._adb-tls-connect._tcp`). It's the user's device:
- Install with `adb install -r`, which keeps data. Don't uninstall, clear data or downgrade without
  asking.
- Don't tap while they're using it: check `adb shell dumpsys power | grep mWakefulness` and the
  foreground app first, and prefer reading logcat.
- A locked phone can't be driven; ask them to test, and read the logs.
