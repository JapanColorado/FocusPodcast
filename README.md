# FocusPodcast

FocusPodcast helps you manage and play podcasts and virtual podcasts/audio books. It lets you manage podcasts, audio books, YouTube and RSS news feeds in one application, and supports a high degree of customization to meet your different playback needs.

This repository is an independent, F-Droid-style fork of [allentown521/FocusPodcast](https://github.com/allentown521/FocusPodcast) (itself derived from [AntennaPod](https://github.com/AntennaPod/AntennaPod)). Compared with upstream it:

- ships a single build with **no ads, analytics, crash reporting, in-app purchases or cloud services** of any kind;
- vendors its former submodules in-tree and keeps the whole codebase under `allen.town.podcast.*`;
- has an English-only source tree (translations for Chinese and Japanese are kept);
- enforces a set of quality gates in CI-style checks: zero Kotlin `!!`, zero `printStackTrace`, no empty catch blocks, every Rx chain with an error handler, detekt with no baseline, and a JVM unit-test suite;
- is not published on Google Play. Upstream's F-Droid listing is not this fork; build it yourself or install a release APK from this repository.

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Screenshot 1" height="200"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Screenshot 2" height="200"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Screenshot 3" height="200"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Screenshot 4" height="200"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Screenshot 5" height="200"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="Screenshot 6" height="200"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" alt="Screenshot 7" height="200"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" alt="Screenshot 8" height="200"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" alt="Screenshot 9" height="200">

The podcast app for your phone, tablet, car, watch or chromecast

★★★★★Main Features★★★★★

😍 No any ads ! ! !
🔒 Privacy protection, no server, will not and cannot track any of your privacy, will not upload any of your data
🌷 The latest Google MD3 material design
🚶 Podcast offline No WiFi? do not worry! Listen to episodes whenever and wherever you are commuting or relaxing.
💾 Compatible SD cards allow you to free up storage space on your device.
📦 Podcast groups, manage the genre and mood of the show.
⏲️ Sleep timer to put the podcast player to sleep.
🌍 Global selection of podcasts, all free to download
🔍 Powerful search engine to find various channels and episodes, always discover your favorite fresh audio content
🥇 Top trending total list highlights all the trending podcasts for you
🏠 Personalize the app to create your own unique podcast app

★★★★★Features★★★★★

Customization
• Customize fast forward and rewind playback
• Choose from a variety of beautiful desktop widgets
• Support custom theme color, pure black theme
• Support lock screen player
• Custom home page
• Customize the order and visibility of the navigation drawer
• Multiple Now Playing Themes

Subscribe/Discover
• Search by podcast name or keyword
• Browse latest/hot/popular podcasts by category
• Paste the podcast RSS/ATOM feed URL. Can also run iTunes, YouTube channel URL
• Support opml file import
• Supports access to password-protected feeds and audio

Play/Audio Effects
• Supports both audio and video podcasts. You can subscribe, browse and play any podcast
• Support online or offline playback
• Support for Bluetooth devices
• Sleep settings
• Drive mode for your convenience
• Built-in audio effects such as playback speed, volume boost and skip mute, down mix (mono)
• Variable playback speed for video podcasts
• Skip the beginning and end
• Chapter support, memory playback position

automation
• Auto-update podcasts with customizable update frequency, set specific times, download your favorite podcasts before waking up
• Automatically download the latest podcast episodes
• Automatically delete downloaded podcast files after playback
• Automatically download old episodes when storage space is low
• Control applications with third-party applications via Intent

other
• Multi-language support
• Support tablet mode
• Complete application backup/restore with multiple options
• Find an episode by playback history or by searching for titles and shownotes
• support android 13 monochrome icon



## Feedback
Bug reports and feature requests for this fork go in this repository's [issue tracker](../../issues). Please read [CONTRIBUTING.md](CONTRIBUTING.md) first for how to report a bug or request a feature. Issues with upstream FocusPodcast belong in the [upstream tracker](https://github.com/allentown521/FocusPodcast/issues).

## License

FocusPodcast is licensed under the GNU General Public License (GPL-3.0). You can find the license text in the LICENSE file.

## Building FocusPodcast

The build is driven by [pixi](https://pixi.sh), which provides JDK 17. You need an Android SDK with platform 35 and build-tools 34.0.0 (default location `~/Android/Sdk`, override with `ANDROID_HOME`). There are no git submodules and no build flavors.

```bash
pixi install          # one-time: fetches the JDK
pixi run build        # debug APK (no secrets needed)
pixi run test         # JVM unit tests (JUnit4 + Robolectric)
pixi run lint         # Android lint
pixi run detekt       # detekt static analysis
pixi run nn           # Kotlin !! ratchet (must stay at 0)
pixi run check        # build + test + lint + nn + detekt
pixi run install      # install the debug APK on a connected device
pixi run release      # signed, minified release APK
```

Release builds additionally need a `secrets.properties` (copy `secrets.properties.sample`) with your signing keystore. See [CONTRIBUTING.md](CONTRIBUTING.md) for details, and `docs/REFACTOR_BASELINE.md` for the metrics the fork's quality gates are built around.

## Credits
The original FocusPodcast is written by [allen town](https://bento.me/allentown) ([FocusApps](https://focus.hk.cn)); if you like the app, consider sponsoring the upstream author on [Ko-fi](https://ko-fi.com/focusapps) or [Liberapay](https://liberapay.com/FocusApps/donate). FocusPodcast is in turn built on [AntennaPod](https://github.com/AntennaPod/AntennaPod).
