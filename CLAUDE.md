# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

FocusPodcast is an Android podcast app (app id `allen.town.focus.podcast`, packages `allen.town.podcast.*`). It is a heavily modified fork of AntennaPod: the structure and most core classes match AntennaPod, but several were renamed (`PodDBAdapter` → `Db`, `UserPreferences` → `Prefs`, `de.danoeh.antennapod` → `allen.town.podcast`), so upstream docs do not map 1:1. Mixed Kotlin/Java: `app` is Kotlin-leaning, `core`/`model`/`parser`/`storage` are almost all Java. Many comments and some commit messages are in Chinese.

## Build prerequisites (the build fails out of the box)

1. **Init submodules.** `focus-common`, `focus-theme`, `focus-purchase`, `searchpreference` are empty git submodules (all from github.com/allentown521) and are imported everywhere (`allen.town.focus_common.*`, `allen.town.core.service.*`, `allen.town.focus_purchase.*`, `com.bytehamster.lib.preferencesearch.*`, `code.name.monkey.appthemehelper.*`). Root `build.gradle` also has a `flatDir` pointing into `focus-common/libs`.
   ```
   git submodule update --init
   ```
2. **Create `secrets.properties`** at repo root from `secrets.properties.sample`. `app/build.gradle` reads it for `buildConfigField`s, manifest placeholders, and **both** `debug` and `release` signing configs, so a keystore path is needed even for debug builds. The `googleAdsKey`/`dropboxScheme` placeholders and `google-services.json` (gitignored, needed by the applied `com.google.gms.google-services` plugin) are also missing from a fresh clone.
3. **JDK 17** (`compileSdk 35`, Gradle 8.7, AGP 8.4.1, Kotlin 1.9.23). Use pixi: `pixi install` fetches JDK 17 from conda-forge and `scripts/env.sh` exports `JAVA_HOME`/`ANDROID_HOME` on activation, so run every Gradle command as `pixi run <task>` (or `pixi shell` then `./gradlew ...`). `scripts/check-env.sh` writes `local.properties` (gitignored) pointing at `$ANDROID_HOME` (default `~/Android/Sdk`, which must have platform 35 and build-tools 34.0.0). The Debian SDK at `/usr/lib/android-sdk` has no platforms and is unusable.
4. Dependency resolution goes through ~13 Aliyun mirrors plus `jcenter()` in root `build.gradle`. Expect slow or flaky resolution outside China; do not "clean up" the mirror list without checking every old artifact still resolves.

## Commands

Flavor dimension `market` has three flavors: `fdroid`, `play`, `free`. Task names are `<flavor><BuildType>`, e.g. `assemblePlayDebug`.

```bash
pixi run build      # assembleFdroidDebug
pixi run test       # testFdroidDebugUnitTest
pixi run lint       # ./gradlew lint
pixi run nn         # !! ratchet (scripts/count-bangbang.sh)
pixi run check      # build + test + lint + nn
pixi run release    # assembleFdroidRelease (needs secrets.properties)
pixi run install    # adb install the debug APK
pixi run clean

# Raw Gradle equivalents (inside `pixi shell`):
./gradlew assembleFdroidDebug            # most self-contained flavor (no Google/ads/purchase deps)
./gradlew assemblePlayDebug              # full Google build (Cast, Firebase, Play Billing, Drive/Dropbox backup)
./gradlew assembleFreeRelease            # Chinese-market build; targetSdk 33, Alipay, Baidu stats, self-hosted updater

./gradlew test                           # all JVM unit tests (JUnit4 + Robolectric)
./gradlew :parser:feed:testFdroidDebugUnitTest
./gradlew :parser:feed:testFdroidDebugUnitTest --tests "allen.town.podcast.parser.feed.element.namespace.RssParserTest"
./gradlew :playback:base:testFdroidDebugUnitTest --tests "*RewindAfterPauseUtilTest"
./gradlew :core:testFdroidDebugUnitTest

./gradlew lint                           # per-module lint.xml in app/, core/, ui/i18n/; abortOnError=true but ignoreWarnings=true
./gradlew checkstyle                     # root task, checkstyle 8.24 over the whole tree
./gradlew :app:connectedFdroidDebugAndroidTest   # Espresso/Robotium instrumentation tests (device required)
```

JVM unit tests exist in `parser/feed`, `playback/base`, and `core` (`core/src/test`). Write new tests as plain JUnit4: the pinned Robolectric 4.5-alpha cannot load JDK 17 class files, so any `@RunWith(RobolectricTestRunner.class)` test fails with `IllegalArgumentException at ClassReader` under the required JDK. Android stubs in plain tests throw, so keep Android calls (e.g. `TextUtils`) out of the tested path or override them in a test subclass.

## Module architecture

`settings.gradle` includes 16 in-tree modules plus the 4 submodules. Every module applies `common.gradle`, which supplies the Kotlin/kapt/parcelize plugins, the three flavors, Java 17, viewBinding, and a GoRouter `api` + `kapt` dependency for every module. All shared versions (SDK levels, `versionCode`/`versionName`, library versions) live in `project.ext` in the root `build.gradle`.

Dependency direction (top depends on bottom):

- **app** — Activities/fragments/adapters, `MyApp` Application, flavor-specific purchase/ads/cloud code. Depends on core and nearly everything else.
- **core** — the workhorse: `DBReader`/`DBWriter`/`DBTasks`, `PlaybackService` + `LocalPSMP` + `ExoPlayerWrapper`, `DownloadService`, `FeedUpdateWorker`, `SyncService`, `Prefs`, widgets, Glide setup, backup/OPML.
- **playback/base** (abstract `PlaybackServiceMediaPlayer`, `PlayerStatus`), **playback/cast** (Chromecast; real impl only in `play`, stubs in `fdroid`/`free`)
- **storage/database** — raw SQLite `Db.java` (`focusPodcastApp.db`, `VERSION = 3`, renumbered from AntennaPod), `DBUpgrade.java` hand-written ALTER ladder, `mapper/*CursorMapper`. No Room.
- **parser/feed** (RSS/Atom), **parser/media** (ID3/Vorbis chapters), **net/ssl**, **net/sync/model** + **net/sync/gpoddernet**
- **event** — EventBus payload classes only. **model** — POJOs (`Feed`, `FeedItem`, `FeedMedia`, ...).
- **ui/common** (shared views), **ui/app-start-intent** (typed Intent builders so lower modules can launch app Activities without depending on `app`), **ui/i18n** and **ui/png-icons** (resources only).

### How the layers talk to each other

- **core → app callbacks via `ClientConfig`.** `core/.../ClientConfig.java` is a static registry of callback interfaces. `app/.../config/ClientConfigurator.java` fills it in a static initializer, which `MyApp` forces to run with `Class.forName("allen.town.podcast.config.ClientConfigurator")`. If core needs something only app knows, add it here rather than a module dependency.
- **Lower modules → app services via GoRouter as a service locator.** GoRouter is *not* used for activity routing (no `@Route` annotations). Service interfaces (`PayService`, `AdService`, `AliPayService`, `GooglePayService`, `AppService`) live in `allen.town.core.service` from `focus-common`; implementations are in `app/src/main/java/allen/town/podcast/service/*Impl.kt` annotated `@Service(remark = "/app/...")`. Callers do `GoRouter.getInstance().getService(PayService::class.java)` — this is how `Db.java`, `Prefs.kt`, `DBWriter`, `DownloadService`, and widgets check pro/purchase status from below `app`. Each module passes `GOROUTER_MODULE_NAME` to kapt (see `common.gradle`).
- **EventBus (greenrobot) is the main cross-layer channel.** Subscriber indexes are kapt-generated: `allen.town.podcast.ApEventBusIndex` (arg in `app/build.gradle`) and `allen.town.podcast.core.ApCoreEventBusIndex` (arg in `core/build.gradle`), both installed in `MyApp.onCreate`. A `@Subscribe` method in a module without an `eventBusIndex` kapt arg will not be indexed.
- **RxJava2** for DB/network work off the main thread (`subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())`), with a global handler in `app/.../error/RxJavaErrorHandlerSetup`.
- **Background work:** `DownloadService` is a foreground Service, not WorkManager. WorkManager is used for `FeedUpdateWorker` (scheduled by `core/util/download/AutoUpdateManager.java`) and `WidgetUpdaterWorker`.

### Flavor-specific source sets

Flavor directories exist under `app/src/{fdroid,play,free}`, `core/src/{fdroid,play,free}`, `net/ssl/src/*`, and `playback/cast/src/*`. The same class name (e.g. `PurchaseActivity`, `DriveBackupActivity`, `ProductWrap`, `SslProviderInstaller`, `WearMediaSession`, `CastPsmp`) is implemented per flavor, with `fdroid` usually a stub. When adding a class to one flavor's source set, add the counterpart to the others or the other flavors will not compile.

## Gotchas

- `app/build.gradle` has a `copyLicense` task hooked to `preBuild` that writes `LICENSE` into `app/src/main/assets/LICENSE.txt` (a source-tree write).
- ProGuard rules are in `app/proguard.cfg`; release enables `minifyEnabled` and `shrinkResources`. Crashlytics mapping upload is force-disabled via a `taskGraph.whenReady` hook.
- ButterKnife and ViewBinding coexist in `app`; prefer ViewBinding for new UI. kapt runs four processors (ButterKnife, Glide, EventBus, GoRouter) and is the usual source of opaque build errors.
- `CONTRIBUTING.md` asks not to upgrade dependencies or build tools without a concrete reason; several pinned versions (e.g. ExoPlayer 2.15.1, `appupdate` 4.3.1) have comments explaining why newer versions break.
- `CONTRIBUTING.md` describes a `develop`/`master` branch flow inherited from AntennaPod, but this repo's only long-lived branch is `main` (plus an `androidx-media3` migration branch on origin).
