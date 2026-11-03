# Tik TV

Tik TV is an unofficial, Android TV / Google TV focused fork of [Cloudstream](https://github.com/recloudstream/cloudstream).

It keeps Cloudstream's features (extensions, search, library, downloads, player) and adds television
polishing, its own app identity so it can be installed next to the original Cloudstream, and an
updater that is isolated from upstream releases.

> Working title. The name is easy to change — see [Renaming](#renaming).

## Status

This is a personal fork, not an official Cloudstream build.

- Imported upstream commit: `5024ace2922a148d38df2a816b483b529789cf58`
- Upstream version: 4.8.0 (version code 68)
- Fork version: `4.8.0-tik.1` (version code 6801)
- License: GPL-3.0, same as upstream — see [LICENSE](LICENSE) and [docs/FORK_NOTICES.md](docs/FORK_NOTICES.md)
- No streaming sources or extensions are bundled. You install extension repositories yourself,
  exactly like upstream Cloudstream.

## What changed compared to upstream

| Area | Change |
| --- | --- |
| Identity | Distinct application id `io.github.fahad0302042_creator.tiktv`, own name, icon and TV banner |
| Coexistence | Installs alongside the official Cloudstream app; the downloaded player task affinity is scoped to the fork id |
| Updater | App updates are disabled and pinned to this repository, so an official Cloudstream APK can never be offered as an update. Extension updates keep working |
| TV overscan | TVs default to a 24 dp inset (`fork_default_tv_overscan_dp`); phones keep 0 dp. Any explicit user choice wins |
| Remote navigation | Back on the TV home screen walks focus through nested containers (content rows, header controls, category chips) instead of only direct children |
| Accessibility | TV row headers are marked as accessibility headings |

Everything else is upstream behavior. See [docs/FORK_NOTICES.md](docs/FORK_NOTICES.md) for the full
list of touched files, [docs/ANDROID_TV.md](docs/ANDROID_TV.md) for TV behavior and
[docs/TV_TEST_PLAN.md](docs/TV_TEST_PLAN.md) for the manual remote test checklist.

## Building

Requirements: JDK 17 and the Android SDK (compileSdk 37), or just let GitHub Actions build it.

```bash
./gradlew :app:assembleStableDebug          # debug APK
python3 scripts/validate_fork.py            # fork invariant checks
./gradlew :app:testStableDebugUnitTest      # JVM tests
```

Output: `app/build/outputs/apk/stable/debug/`.

The `prerelease` build type still expects the upstream `SIGNING_*` secrets; the `debug` build uses the
normal Android debug key, or the key pointed at by `TIKTV_DEBUG_STORE_FILE` when you want a stable
signature across machines.

### Stable debug signing

Two APKs can only replace each other when they share a signature. CI runners are fresh machines, so
without a fixed key every CI build has a different signature and you must uninstall before installing
again. To avoid that, create one non-production debug key once and register it as a repository secret:

```bash
# anywhere, outside the repository
openssl req -x509 -newkey rsa:2048 -keyout tiktv-debug.key -out tiktv-debug.crt \
  -days 10950 -nodes -subj "/C=US/O=Android/CN=Android Debug"
openssl pkcs12 -export -inkey tiktv-debug.key -in tiktv-debug.crt \
  -name androiddebugkey -out tiktv-debug.p12 -passout pass:android

gh secret set TIKTV_DEBUG_KEYSTORE_BASE64 --repo fahad0302042-creator/Tik < <(base64 -w0 tiktv-debug.p12)
```

The workflow decodes it into `TIKTV_DEBUG_STORE_FILE` when the secret exists and otherwise falls back
to the plain debug key. It is a debug key only — never use it for a published release, and set
`tiktv.appUpdatesEnabled=true` only once this repository publishes properly signed releases.

CI (`.github/workflows/build.yml`) builds `assembleStableDebug`, runs the unit tests and the fork
validation, and uploads the APK as a workflow artifact.

## Installing on a TV

```bash
adb connect <tv-ip>:5555
adb install -r app-stable-debug.apk
```

Or copy the APK to a USB stick / use a file manager on the TV. The app registers a
`LEANBACK_LAUNCHER` entry, so it appears in the Google TV launcher row of apps.

## Renaming

All identity values live in one place, `gradle.properties`:

```properties
tiktv.applicationId=io.github.fahad0302042_creator.tiktv
tiktv.appName=Tik TV
tiktv.sourceUrl=https://github.com/fahad0302042-creator/Tik
```

Change `tiktv.appName` and rebuild; the launcher name, the "Play with …" share target and the About
entry all follow it. Change the application id **before** the first install — Android treats a
different id as a different app, so existing installs and their data will not be carried over.

## Repository layout

| Path | Contents |
| --- | --- |
| `app/` | The Android app (upstream app module plus fork changes) |
| `library/`, `shared/` | Upstream support modules |
| `docs/` | Fork notices, TV notes, test plan, verbatim upstream README |
| `scripts/validate_fork.py` | Static checks for the fork invariants |
| `gradle.properties` | `tiktv.*` fork configuration block |

## Legal

Cloudstream is licensed under GPL-3.0 and this fork keeps that license, the upstream copyright
notices and the upstream LICENSE file. This project is not affiliated with the Cloudstream team.
Read [docs/FORK_NOTICES.md](docs/FORK_NOTICES.md) before redistributing.
