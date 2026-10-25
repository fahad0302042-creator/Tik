# TikTV — TikTok for Android TV

An unofficial, sideload-only Android TV / Google TV app for watching TikTok on a big screen,
built because the official TikTok TV app no longer works.

It is a **full-screen WebView wrapper around TikTok's public website** with a remote-control
layer added on top: D-pad feed navigation, a virtual mouse, media-key support and a native
text-entry dialog so you can sign in or search with a TV remote.

There is no API key, no scraping, no private endpoint and no data harvesting. The app renders
the same pages your TV's browser would.

<p align="center">
  <img src="app/src/main/res/drawable-xhdpi/banner.png" width="480" alt="TikTV banner">
</p>

---

## Download / install

**Option A — Downloader app on the TV (easiest)**

1. Install **Downloader** (by AFTVnews) from the Play Store on your TV.
2. Open it and enter the release URL for this repo:
   `https://github.com/<owner>/<repo>/releases/latest/download/TikTV.apk`
3. Install, then launch **TikTV** from your apps row.

**Option B — ADB**

```bash
adb connect <TV-IP>:5555
adb install -r TikTV.apk
```

**Option C — build artifact**

Every push builds the APK automatically (GitHub → Actions → *Build APK* → Artifacts).

Updates install over the old version because every build is signed with the same
throwaway keystore committed in `keystore/` (see [Security notes](#security-notes)).

> The TV must allow installs from unknown sources: **Settings → System → Developer options
> → Install unknown apps** (or "Security → Unknown sources").

---

## Remote controls

| Button | Watching a video | In mouse mode |
| --- | --- | --- |
| D-pad Up / Down | Previous / next video | Move the pointer |
| D-pad Left / Right | Rewind / forward 10 s | Move the pointer |
| OK | Play / pause | Click |
| **Hold OK** | Turn mouse pointer on | Turn it off |
| Back | Go back a page (twice to exit) | Leave mouse mode |
| **Menu** / Info | App menu: search, sign in, settings, diagnostics | same |
| Red | Toggle mouse pointer | Toggle mouse pointer |
| Green | Toggle **cinema mode** | |
| Yellow | Mute / unmute | |
| Blue / Search | Search TikTok | |

Media keys on newer remotes (play/pause, next, previous) work too.

### Signing in

Open **Menu → Sign in**. Mouse mode turns on automatically so you can tap the form fields;
every text field you focus opens a native keyboard dialog, so a remote can type a password.

**Sign in with Google will not work** — Google blocks embedded browsers (`disallowed_useragent`)
by design. Use phone number, email or username instead. The session persists in the WebView
cookie store until you use **Settings → Sign out**.

### Cinema mode (experimental)

Green button / Settings. It pins the current video to the full viewport and hides TikTok's
surrounding interface. It works by re-styling the page's DOM, so if TikTok changes its
markup it may look odd — toggle it off again and you are back to the normal layout.

---

## Settings

| Setting | What it does |
| --- | --- |
| Home page | For You / Following / Explore / LIVE / custom URL |
| Site layout | Desktop (default, best on a TV) or mobile layout |
| Cinema mode | Full-screen video overlay |
| Auto-advance | Next video when the current one ends |
| Start with mouse cursor | Pointer visible on launch |
| Cursor speed | Slow / Normal / Fast |
| Sign out | Clears cookies + site storage |
| Reset | Back to defaults |

Settings apply when you return to the feed; changing the site layout reloads the page.

---

## Known limitations

- **Not official.** TikTok can change its website, show "open in app" banners, add interstitial
  login walls or block embedded browsers at any time. Nothing here can guarantee it keeps working.
- **No account-API features.** Likes, comments, DMs and publishing use TikTok's own web UI.
  Some of them (e.g. upload) may refuse to run in a WebView.
- **Google / Apple sign-in fails** in any WebView, including this one.
- **Feed navigation is best-effort.** `Tv.next()` tries arrow buttons, then synthetic arrow
  keys, then a synthetic swipe, then scrolling, and stops at the first one that changes the
  video. If TikTok redesigns, use **Menu → Diagnostics** to see what the injected script can
  still find.
- **No playback of DRM-protected content** (not applicable to TikTok, but this player is a
  plain WebView).
- Search, share sheets and some overlays are designed for mouse/touch; that is what the virtual
  cursor is for.
- Video quality/decoding depends on the TV's WebView (Chromium) build; very old TVs may struggle
  with 1080p60.

---

## Building it yourself

Requirements: JDK 17, Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # signed with keystore/tiktv.jks
```

CI (`.github/workflows/android.yml`) runs on every push, uploads the APK as an artifact, and
publishes a GitHub release when you push a tag (`git tag v1 && git push --tags`) or run the
workflow manually.

```
app/src/main/
├── java/com/tiktv/app/
│   ├── MainActivity.kt      WebView host, key handling, virtual mouse, dialogs
│   ├── SettingsActivity.kt  D-pad friendly settings screen
│   └── Prefs.kt             SharedPreferences wrapper
├── assets/tv.js             Injected page script (feed stepping, cinema mode, text entry)
└── res/                     Theme, layouts, launcher icon and TV banner
```

### How feed navigation works

`MainActivity` turns a D-pad press into `Tv.next()` inside the page. `tv.js` then tries, in order:

1. TikTok's own next/previous arrow buttons (`[data-e2e="arrow-right"]`, …)
2. synthetic `ArrowDown` / `ArrowUp` keyboard events (what TikTok's desktop player listens to)
3. a synthetic finger swipe (what the mobile web feed listens to)
4. scrolling the feed container

and stops as soon as the `<video>` source actually changes. The diagnostic screen reports which
of those are currently available.

---

## Security notes

- The only JavaScript bridge is `TvBridge`: it receives small log strings, a text-field request
  (which opens a native dialog) and toast text. It cannot read the page, files or the network.
- Camera and microphone permission requests from web pages are always denied.
- Navigation to non-`http(s)` links (`intent://`, `market://`, app deep links) is blocked so a
  "get the app" banner cannot kick you out of the TV app.
- `keystore/tiktv.jks` is a throwaway key whose password is public on purpose: it exists only so
  successive sideload builds share one signing identity. It protects nothing — do not reuse it
  for anything you publish on a store.

## Legal

TikTV is not affiliated with, endorsed by or connected to TikTok or ByteDance. "TikTok" is a
trademark of its owner. This app is a personal-use browser wrapper; you are responsible for
complying with TikTok's terms of service in your region.

MIT licensed — see [LICENSE](LICENSE).
