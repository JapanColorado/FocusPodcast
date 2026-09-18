# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

FocusPodcast is an Android podcast app (app id `allen.town.focus.podcast`, packages `allen.town.podcast.*`). This repository is a **hard fork** of [allentown521/FocusPodcast](https://github.com/allentown521/FocusPodcast), which is itself a heavily modified fork of AntennaPod. Upstream mergeability was abandoned in the September 2026 refactor (see `docs/REFACTOR_BASELINE.md` for the before/after numbers), so do not try to keep diffs upstream-friendly. The structure and most core classes still match AntennaPod, but several were renamed (`PodDBAdapter` → `Db`, `UserPreferences` → `Prefs`, `de.danoeh.antennapod` → `allen.town.podcast`), so upstream docs do not map 1:1. Mixed Kotlin/Java: `app` is Kotlin-leaning, `core`/`model`/`parser`/`storage` are almost all Java. All comments and configuration are in English; only the `values-zh-rCN`, `values-zh-rTW` and `values-ja` translation resources contain non-Latin text.

The app is F-Droid-only: there are no build flavors, no Google Play / Firebase / ads / in-app-purchase code, and no pro gating. Monetisation, analytics, crash reporting, LeanCloud, Cast, cloud backup and the GoRouter service locator were all deleted rather than stubbed; do not reintroduce them.

## Build prerequisites

1. **JDK 17** (`compileSdk 35`, Gradle 8.7, AGP 8.4.1, Kotlin 1.9.23). Use pixi: `pixi install` fetches JDK 17 from conda-forge and `scripts/env.sh` exports `JAVA_HOME`/`ANDROID_HOME` on activation, so run every Gradle command as `pixi run <task>` (or `pixi shell` then `./gradlew ...`).
2. `scripts/check-env.sh` (a `depends-on` of most pixi tasks) verifies JDK 17 and the SDK and writes `local.properties` (gitignored) pointing at `$ANDROID_HOME` (default `~/Android/Sdk`, which must have platform 35 and build-tools 34.0.0). The Debian SDK at `/usr/lib/android-sdk` has no platforms and is unusable.
3. **Debug builds need no secrets.** `app/build.gradle` reads `secrets.properties` if it exists; if it does not, it prints a notice, uses placeholder PodcastIndex API keys, and falls back to Gradle's default debug signing config. **Release builds need a real keystore**: copy `secrets.properties.sample` to `secrets.properties` and fill in `storeFile`/`storePassword`/`keyAlias`/`keyPassword` (the same entry drives both the release and debug signing configs when present).
4. There are no git submodules. The three vendored libraries live in-tree under `lib/`.
5. Dependency resolution uses `google()`, `mavenCentral()`, `gradlePluginPortal()` and JitPack, plus one Aliyun mirror (`maven.aliyun.com/repository/public`) that exists solely because `com.beloo.widget:ChipsLayoutManager:0.3.7` (an ex-jcenter artifact) is not on Maven Central. Do not remove that entry without finding another source for it.

## Commands

There is no flavor dimension, so task names are just `<buildType>`, e.g. `assembleDebug`.

```bash
pixi run build      # assembleDebug
pixi run test       # testDebugUnitTest
pixi run lint       # ./gradlew lint
pixi run nn         # !! ratchet (scripts/count-bangbang.sh)
pixi run detekt     # detekt static analysis (config/detekt/detekt.yml)
pixi run check      # build + test + lint + nn + detekt
pixi run release    # assembleRelease (needs secrets.properties)
pixi run install    # adb install the debug APK
pixi run clean

# Raw Gradle equivalents (inside `pixi shell`):
./gradlew assembleDebug
./gradlew assembleRelease

./gradlew test                           # all JVM unit tests (JUnit4 + Robolectric)
./gradlew :parser:feed:testDebugUnitTest
./gradlew :parser:feed:testDebugUnitTest --tests "allen.town.podcast.parser.feed.element.namespace.RssParserTest"
./gradlew :playback:base:testDebugUnitTest --tests "*RewindAfterPauseUtilTest"
./gradlew :core:testDebugUnitTest

./gradlew lint                           # per-module lint.xml in app/, core/, ui/i18n/; abortOnError=true but ignoreWarnings=true
./gradlew detekt                         # detekt over every Kotlin module; also runs detektDebug (type resolution)
./gradlew :app:detektDebug               # just the type-resolved pass for one module
./gradlew :app:connectedDebugAndroidTest # Espresso/Robotium instrumentation tests (device required)
```

JVM unit tests exist in `parser/feed`, `playback/base`, and `core` (`core/src/test`). Robolectric 4.12.2 works under JDK 17 but only emulates up to API 34, so every module with Robolectric tests has `src/test/resources/robolectric.properties` pinning `sdk=34`. Prefer plain JUnit4 for pure logic; use `@RunWith(RobolectricTestRunner.class)` when the code under test needs real Android classes (SQLite, `TextUtils`, resources).

## Module architecture

`settings.gradle` includes 18 modules: 15 app modules plus the three vendored libraries under `lib/`. Every module applies `common.gradle`, which supplies the Kotlin/kapt/parcelize plugins, Java 17, viewBinding and the shared `packagingOptions`/`lintOptions`. All shared versions (SDK levels, `versionCode`/`versionName`, library versions) live in `project.ext` in the root `build.gradle`.

Dependency direction (top depends on bottom):

- **app** — Activities/fragments/adapters, `MyApp` Application. Depends on core and nearly everything else.
- **core** — the workhorse: `DBReader`/`DBWriter`/`DBTasks`, `PlaybackService` + `LocalPSMP` + `ExoPlayerWrapper`, `DownloadService`, `FeedUpdateWorker`, `SyncService`, `Prefs`, widgets, Glide setup, backup/OPML.
- **playback/base** — abstract `PlaybackServiceMediaPlayer`, `PlayerStatus`.
- **storage/database** — raw SQLite `Db.java` (package `allen.town.podcast.storage.db`, `focusPodcastApp.db`, `VERSION = 4`, renumbered from AntennaPod), `DBUpgrade.java` hand-written ALTER ladder, `mapper/*CursorMapper`. No Room.
- **parser/feed** (RSS/Atom), **parser/media** (ID3/Vorbis chapters), **net/ssl**, **net/sync/model** + **net/sync/gpoddernet**
- **event** — EventBus payload classes only. **model** — POJOs (`Feed`, `FeedItem`, `FeedMedia`, ...).
- **ui/common** (shared views), **ui/app-start-intent** (typed Intent builders so lower modules can launch app Activities without depending on `app`), **ui/i18n** and **ui/png-icons** (resources only).
- **lib/common** (`allen.town.podcast.common.*` — base Activity/Application, dialogs, utils), **lib/theme** (`allen.town.podcast.theme.*`), **lib/searchpreference** (`allen.town.podcast.searchpreference.*`). These were git submodules until Phase 1; they are now ordinary in-tree modules and may be edited freely. Their packages were renamed out of their upstream namespaces into `allen.town.podcast.common.*`, `allen.town.podcast.theme.*` and `allen.town.podcast.searchpreference.*` (the handful of files that still declared a RetroMusic package were folded into the package matching their own directory), so every class in the build now lives under `allen.town.podcast.*` and upstream AppThemeHelper / RetroMusic / SearchPreference sources no longer apply as drop-in patches.

### The big classes are split into collaborators

The seven classes that used to exceed ~1,000 lines were split by pure extraction; every public API stayed on the original class, so callers did not change. When touching one of these, look for the logic in its helpers first:

- `storage/db/Db.java` — `DbSchema` (column constants and CREATE statements), a `Dao` base class with the shared transaction wrapper, and per-table `FeedDao`, `FeedItemDao`, `FeedMediaDao`, `QueueDao`, `FavoritesDao`, `DownloadLogDao`, `AdSegmentDao`. `Db` delegates to them.
- `core/service/playback/PlaybackService.java` — `PlaybackServiceMediaSession`, `PlaybackServiceMediaBrowser`, `PlaybackServiceNotificationUpdater`, `PlaybackServiceReceivers`, `PlaybackServicePlayerCallback`, `PlaybackServiceAutoSkipper`, `PlaybackServiceAdSkipper`.
- `core/service/playback/LocalPSMP.java` — `LocalPSMPAudioFocus`, `LocalPSMPAudioEffects`, `LocalPSMPPlayerFactory`, `LocalPSMPSeeker`, `LocalPSMPPlaybackEnder`, plus `PlayerLock` and `PlayerExecutor`.
- `core/pref/Prefs.kt` — `PrefsStore` plus the `PlaybackPrefs`, `DownloadPrefs`, `UiPrefs`, `NetworkPrefs` and `SyncPrefs` objects. `Prefs` remains the single facade for both Java and Kotlin callers; add new preferences to the matching group object and expose them through `Prefs`.
- `core/service/download/DownloadService.java` — `DownloadQueue`, `DownloadPipeline`, `DownloadCompletionHandler`, `DownloadNotifier`.
- `app/fragment/FeedItemlistFragment.kt` — header, loader, menu, multi-select and pager helpers under `fragment/feeditemlist/`.
- `app/activity/MainActivity.kt` — nav drawer, fragment navigator, player sheet, intent handler and hardware-key helpers under `activity/main/`.

### Ad auto-skip

Ad skipping is opt-in (`Prefs.isAdSkipEnabled`, default off) and has no ML runtime: `core/adskip/` is
pure-Java DSP. `PcmDecoder` streams a downloaded file through `MediaCodec` to mono 16 kHz float,
`AudioFeatureExtractor` turns it into one `FeatureFrame` per half second (RMS, crest factor, spectral
flatness/centroid/rolloff/flux, low-band ratio, 8-band timbre vector), and `AdDetector` robust-normalises
those against the episode median, finds change points, and scores regions that differ in timbre, carry
a music bed or are louder/more compressed, modulated by duration and position priors. `ChapterAdMatcher`
flags chapters titled like sponsor breaks, `AdSegmentMerger` gives manual > chapter > detected
precedence, and `AdAnalyzer` is the facade. `AdAnalysisWorker` (WorkManager, unique per media id,
enqueued from `MediaDownloadedHandler`) stores every result with confidence >= 0.3 in the `ad_segments`
table (`AdSegmentDao`, DB version 4); the sensitivity preference is applied at playback time, so
changing it needs no re-analysis. `PlaybackServiceAdSkipper` runs on the service's one-second ticker
next to the intro/ending skipper: it only skips when playback *entered* a segment within a 3 s window,
treats a seek into the middle as "let it play", posts `AdSkippedEvent` (the app shows the Undo
snackbar and answers with `AdSkipUndoEvent`), and never shows UI itself. The detector's honest limits:
host-read ads with no music bed and no level change are invisible to it, and timbre outliers such as
phone-line guests or archival clips are the likely false positives, which is why segments are drawn on
the seek bar and can be disabled or deleted per episode.

### How the layers talk to each other

- **core → app callbacks via `ClientConfig`.** `MyApp.onCreate` calls `ClientConfig.initialize(context, DownloadServiceCallbacksImpl())`, which also initialises `Db`, `Prefs`, `NetworkUtils` and the sync settings. Receivers and workers that may start in a fresh process call `ClientConfig.ensureInitialized(context)`, which throws if the app never ran. `DownloadServiceCallbacks` (PendingIntents into app Activities) is the only interface core needs from app; if core needs something else only app knows, extend it rather than adding a module dependency.
- **EventBus (greenrobot) is the main cross-layer channel.** Subscriber indexes are kapt-generated: `allen.town.podcast.ApEventBusIndex` (arg in `app/build.gradle`) and `allen.town.podcast.core.ApCoreEventBusIndex` (arg in `core/build.gradle`), both installed in `MyApp.onCreate`. A `@Subscribe` method in a module without an `eventBusIndex` kapt arg will not be indexed.
- **RxJava2** for DB/network work off the main thread (`subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())`), with a global handler in `app/.../error/RxJavaErrorHandlerSetup`.
- **Background work:** `DownloadService` is a foreground Service, not WorkManager. WorkManager is used for `FeedUpdateWorker` (scheduled by `core/util/download/AutoUpdateManager.java`) and `WidgetUpdaterWorker`.

## Gotchas

- `app/build.gradle` has a `copyLicense` task hooked to `preBuild` that copies the root `LICENSE` to `app/build/generated/license-assets/LICENSE.txt`; that directory is registered as an extra `assets` srcDir. It no longer writes into the source tree — do not reintroduce a source-tree write.
- ProGuard rules are in `app/proguard.cfg`; release enables `minifyEnabled` and `shrinkResources`.
- **detekt is a build gate.** Config lives in `config/detekt/detekt.yml`; it starts from detekt's
  default config and switches off everything stylistic (naming, complexity, `MagicNumber`,
  `MaxLineLength`, ...) so the gate only reports likely bugs. `maxIssues: 0`, so any finding fails.
  `UnsafeCallOnNullableType`, `PrintStackTrace` and `EmptyCatchBlock` must stay at zero with no
  baseline entry; there is no `config/detekt/baseline.xml` and adding one for those three is not
  allowed. Deliberate exceptions carry `@Suppress("RuleName")` next to a `// detekt: ...` comment
  saying why. Note that `UnsafeCallOnNullableType` needs type resolution, so the root
  `build.gradle` chains `detektDebug` onto `detekt`; running detekt therefore compiles first.
- **The Kotlin `!!` count is 0 and ratcheted there.** `scripts/bangbang.max` holds `0`, enforced by
  `pixi run nn` (a grep) and, independently, by detekt's `UnsafeCallOnNullableType`. Do not raise
  the max; rewrite the null handling instead.
- **`core` compiles Kotlin with `allWarningsAsErrors`** (`core/build.gradle`). It has only a
  handful of Kotlin files, so keeping them warning-free is cheap. `app` deliberately does not.
- All UI uses ViewBinding (ButterKnife was removed). kapt runs two processors (Glide, EventBus) and is the usual source of opaque build errors.
- **`pixi run lint` passes.** Three modules carry deliberate suppressions: `lib/theme/lint.xml` and `lib/common/lint.xml` downgrade `RestrictedApi` to a warning (vendored theme/preference code reaches into androidx internals that have no public equivalent), and `PlaybackService`/`PlaybackController`/`DownloadService` carry `@SuppressLint("UnspecifiedRegisterReceiverFlag")` on their pre-Android-13 `registerReceiver` branch (androidx.core 1.7.0 has no `ContextCompat.registerReceiver`). Cursor tinting now uses `TextView.setTextCursorDrawable` on API 29+ and only falls back to reflection below that.
- `CONTRIBUTING.md` asks not to upgrade dependencies or build tools without a concrete reason; several pinned versions (e.g. ExoPlayer 2.15.1) have comments explaining why newer versions break.
- The only long-lived branch is `main`.
- `docs/REFACTOR_BASELINE.md` records the metrics the refactor drove to zero (`!!`, `printStackTrace`, empty catches, Rx chains without `onError`, CJK text outside translations, detekt findings). Treat those zeros as invariants when adding code, not as a one-off cleanup.
- Known follow-ups not yet done: add `@Nullable`/`@NonNull` to the Java model/core getters that the `!!` sweep had to guard on the Kotlin side, and turn on Kotlin warnings-as-errors for `app` (only `core` has it).
