# TikTV — TikTok player for Android TV

An **unofficial** Android TV app that plays TikTok videos. The official TikTok
app no longer works on Android TV, so this app takes a TikTok link (or the full
share text copied from the TikTok app), extracts the direct video URL and plays
it full-screen with full TV remote support.

## Features

- Paste any TikTok video link or share text → **Play** instantly
- **Save** videos to a local watchlist (stored on the device) and play them later
- Full TV remote support: D-pad navigation, play/pause/seek via the on-screen player controls
- Runs on Android TV **and** regular Android phones/tablets

## Build with GitHub Actions (recommended)

The APK is built by GitHub Actions — no local Android SDK needed:

1. Push your branch to this repository (or trigger the workflow manually).
2. Go to **Actions → Build APK → Run workflow** (or just push to `main`).
3. When the job finishes, download **`TikTV-debug.apk`** from the job artifacts.

The workflow (`.github/workflows/build.yml`) checks out the repo, sets up JDK 17
and the Android SDK, and runs `./gradlew :app:assembleDebug`.

## Install on your TV

The APK is signed with the debug key, so install it with `adb`:

```bash
adb install TikTV-debug.apk
```

Then open **Settings → Device Preferences → Security & restrictions → Unknown
sources** on the TV and allow your adb-installed apps. The app appears in the TV
launcher (it declares the `LEANBACK_LAUNCHER` intent filter).

## How it works

- `network/TiktokFetcher.kt` fetches the video page (mobile user agent), parses
  the embedded `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON to find the direct MP4
  URL, thumbnail, title and author, with regex fallbacks for the raw HTML.
- `ui/player/PlayerScreen.kt` plays the URL with Media3 (ExoPlayer), using the
  standard player controls (works with the TV remote).
- `data/WatchlistStore.kt` persists saved videos with DataStore preferences.

> **Note:** TikTok changes its page structure frequently and blocks scrapers.
> The extraction logic is defensive, but a link may fail if TikTok changes
> something — just try again or copy a fresh link. This project is for
> personal use and is not affiliated with TikTok.
