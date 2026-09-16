# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

FocusPodcast is an Android podcast app (app id `allen.town.focus.podcast`, packages `allen.town.podcast.*`). It is a heavily modified fork of AntennaPod: the structure and most core classes match AntennaPod, but several were renamed (`PodDBAdapter` → `Db`, `UserPreferences` → `Prefs`, `de.danoeh.antennapod` → `allen.town.podcast`), so upstream docs do not map 1:1. Mixed Kotlin/Java: `app` is Kotlin-leaning, `core`/`model`/`parser`/`storage` are almost all Java. All comments and configuration are in English; only the `values-zh-rCN`, `values-zh-rTW` and `values-ja` translation resources contain non-Latin text.

The app is F-Droid-only: there are no build flavors, no Google Play / Firebase / ads / in-app-purchase code, and no pro gating.

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
pixi run check      # build + test + lint + nn
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
./gradlew checkstyle                     # root task, checkstyle 8.24 over the whole tree
./gradlew :app:connectedDebugAndroidTest # Espresso/Robotium instrumentation tests (device required)
```

JVM unit tests exist in `parser/feed`, `playback/base`, and `core` (`core/src/test`). Robolectric 4.12.2 works under JDK 17 but only emulates up to API 34, so every module with Robolectric tests has `src/test/resources/robolectric.properties` pinning `sdk=34`. Prefer plain JUnit4 for pure logic; use `@RunWith(RobolectricTestRunner.class)` when the code under test needs real Android classes (SQLite, `TextUtils`, resources).

## Module architecture

`settings.gradle` includes 18 modules: 15 app modules plus the three vendored libraries under `lib/`. Every module applies `common.gradle`, which supplies the Kotlin/kapt/parcelize plugins, Java 17, viewBinding and the shared `packagingOptions`/`lintOptions`. All shared versions (SDK levels, `versionCode`/`versionName`, library versions) live in `project.ext` in the root `build.gradle`.

Dependency direction (top depends on bottom):

- **app** — Activities/fragments/adapters, `MyApp` Application. Depends on core and nearly everything else.
- **core** — the workhorse: `DBReader`/`DBWriter`/`DBTasks`, `PlaybackService` + `LocalPSMP` + `ExoPlayerWrapper`, `DownloadService`, `FeedUpdateWorker`, `SyncService`, `Prefs`, widgets, Glide setup, backup/OPML.
- **playback/base** — abstract `PlaybackServiceMediaPlayer`, `PlayerStatus`.
- **storage/database** — raw SQLite `Db.java` (package `allen.town.podcast.storage.db`, `focusPodcastApp.db`, `VERSION = 3`, renumbered from AntennaPod), `DBUpgrade.java` hand-written ALTER ladder, `mapper/*CursorMapper`. No Room.
- **parser/feed** (RSS/Atom), **parser/media** (ID3/Vorbis chapters), **net/ssl**, **net/sync/model** + **net/sync/gpoddernet**
- **event** — EventBus payload classes only. **model** — POJOs (`Feed`, `FeedItem`, `FeedMedia`, ...).
- **ui/common** (shared views), **ui/app-start-intent** (typed Intent builders so lower modules can launch app Activities without depending on `app`), **ui/i18n** and **ui/png-icons** (resources only).
- **lib/common** (`allen.town.focus_common.*` — base Activity/Application, dialogs, utils), **lib/theme** (`code.name.monkey.appthemehelper.*`), **lib/searchpreference** (`com.bytehamster.lib.preferencesearch.*`). These were git submodules until Phase 1; they are now ordinary in-tree modules and may be edited freely.

### How the layers talk to each other

- **core → app callbacks via `ClientConfig`.** `core/.../ClientConfig.java` is a static registry of callback interfaces (`applicationCallbacks`, `downloadServiceCallbacks`). `app/.../config/ClientConfigurator.java` fills it in a static initializer, which `MyApp` forces to run with `Class.forName("allen.town.podcast.config.ClientConfigurator")`. If core needs something only app knows, add it here rather than a module dependency.
- **EventBus (greenrobot) is the main cross-layer channel.** Subscriber indexes are kapt-generated: `allen.town.podcast.ApEventBusIndex` (arg in `app/build.gradle`) and `allen.town.podcast.core.ApCoreEventBusIndex` (arg in `core/build.gradle`), both installed in `MyApp.onCreate`. A `@Subscribe` method in a module without an `eventBusIndex` kapt arg will not be indexed.
- **RxJava2** for DB/network work off the main thread (`subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())`), with a global handler in `app/.../error/RxJavaErrorHandlerSetup`.
- **Background work:** `DownloadService` is a foreground Service, not WorkManager. WorkManager is used for `FeedUpdateWorker` (scheduled by `core/util/download/AutoUpdateManager.java`) and `WidgetUpdaterWorker`.

## Gotchas

- `app/build.gradle` has a `copyLicense` task hooked to `preBuild` that copies the root `LICENSE` to `app/build/generated/license-assets/LICENSE.txt`; that directory is registered as an extra `assets` srcDir. It no longer writes into the source tree — do not reintroduce a source-tree write.
- ProGuard rules are in `app/proguard.cfg`; release enables `minifyEnabled` and `shrinkResources`.
- All UI uses ViewBinding (ButterKnife was removed). kapt runs two processors (Glide, EventBus) and is the usual source of opaque build errors.
- **`pixi run lint` passes.** Three modules carry deliberate suppressions: `lib/theme/lint.xml` and `lib/common/lint.xml` downgrade `RestrictedApi` to a warning (vendored theme/preference code reaches into androidx internals that have no public equivalent), and `PlaybackService`/`PlaybackController`/`DownloadService` carry `@SuppressLint("UnspecifiedRegisterReceiverFlag")` on their pre-Android-13 `registerReceiver` branch (androidx.core 1.7.0 has no `ContextCompat.registerReceiver`). Cursor tinting now uses `TextView.setTextCursorDrawable` on API 29+ and only falls back to reflection below that.
- `CONTRIBUTING.md` asks not to upgrade dependencies or build tools without a concrete reason; several pinned versions (e.g. ExoPlayer 2.15.1) have comments explaining why newer versions break.
- The only long-lived branch is `main`.
