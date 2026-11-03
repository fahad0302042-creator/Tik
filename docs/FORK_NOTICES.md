# Fork notices

## Upstream

| | |
| --- | --- |
| Project | [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream) |
| Imported commit | `5024ace2922a148d38df2a816b483b529789cf58` (master, 2026-10-28) |
| Upstream version | 4.8.0, version code 68 |
| License | [GPL-3.0](../LICENSE) |
| Upstream README | [docs/UPSTREAM_README.md](UPSTREAM_README.md) |

This repository is a fork. The GPL-3.0 license is kept verbatim, together with the upstream
copyright notices. The Kotlin namespace stays `com.lagradost.cloudstream3` on purpose: Cloudstream
extensions are compiled against that package, so renaming it would break every extension.

## Modifications

### Identity and coexistence

- `tiktv.*` configuration block added to `gradle.properties` (application id, app name, version,
  source URL, upstream commit, update switch, update owner/repository). Every value is read in
  `app/build.gradle.kts` and validated at configuration time.
- Application id: `io.github.fahad0302042_creator.tiktv` (never the official id).
- `resValue` now generates `app_name` and `play_with_app_name`; the 122 localized upstream copies were
  removed so the fork name cannot be half-translated. All other translated strings are untouched.
- New original assets: `res/drawable/fork_icon.xml` (adaptive-style icon), `res/drawable/fork_banner.xml`
  (320x180 TV banner). Neither embeds the app name, so renaming only needs `gradle.properties`.
- `AndroidManifest.xml`: icon, round icon and banner point at the fork drawables; the downloaded
  player task affinity uses `${applicationId}.downloadedplayer`.
- `settings.gradle.kts`: root project renamed to `Tik-TV`.
- BuildConfig fields added: `FORK_NAME`, `FORK_SOURCE_URL`, `FORK_UPSTREAM_COMMIT`,
  `FORK_APP_UPDATES_ENABLED`, `FORK_UPDATE_OWNER`, `FORK_UPDATE_REPOSITORY`.

### Update isolation

Official Cloudstream APKs must never be installable as an update to this fork.

- `utils/InAppUpdater.kt`: `getAppUpdate` returns "no update" when `FORK_APP_UPDATES_ENABLED` is
  false; `installPreReleaseIfNeeded` shows the `fork_updates_disabled` toast instead of downloading.
- `ui/settings/GithubReleases.kt` and `ui/settings/ApkUpdater.kt`: the GitHub owner/repository come
  from `BuildConfig.FORK_UPDATE_OWNER` / `FORK_UPDATE_REPOSITORY`, not from hardcoded upstream values.
- `ui/home/MainActivityScreen.kt`: `githubViewModel()` returns null while updates are disabled.
- `ui/settings/SettingsUpdates.kt`: legacy manual-check and prerelease preferences are hidden.
- `ui/settings/SettingsUpdatesScreen.kt` (Compose): update, prerelease, APK installer and update
  notification preferences are disabled and labelled with `fork_updates_disabled`.
- Extension/plugin repository updates are unaffected — those still use the extension repository URL.

Flip `tiktv.appUpdatesEnabled=true` in `gradle.properties` once this repository publishes its own
releases with signed APKs attached.

### Television behavior

- `utils/ForkTvHelper.kt` (new): `defaultOverscanDp(isTv, tvDefaultDp)` and `isWithin(view, ancestorId)`.
- `MainActivity.kt`: TV layouts start with the `fork_default_tv_overscan_dp` inset (`res/values/fork_config.xml`,
  24 dp) instead of 0 dp. Values already saved by the user — including 0 — always win.
- `ui/settings/SettingsUI.kt`: the same default is applied for the Settings slider on TV only. The
  "user has not chosen yet" state is captured before the preference is inflated, so an explicit 0
  survives.
- `ui/home/HomeFragment.kt`: `handleTvBackPress` now uses `ForkTvHelper.isWithin` to detect focus
  inside nested containers (content rows, header controls, category chips) instead of comparing
  direct parents only.
- `res/layout/homepage_parent_tv.xml`: row headers are marked `accessibilityHeading="true"`.
- `res/layout/main_settings.xml` + `ui/settings/SettingsFragment.kt`: About this fork entry showing
  `fork_about_summary` and opening `BuildConfig.FORK_SOURCE_URL`; focusable on TV.
- `res/values/strings.xml`: added `fork_updates_disabled` and `fork_about_summary`.

### Tooling

- `scripts/validate_fork.py`: static checks for every invariant above (identity values, no hardcoded
  app name, upstream update repository not referenced, TV resources present, XML well-formed, docs
  present). Runs in CI before the build.
- `.github/workflows/build.yml`: build + unit tests + validation, uploads the debug APK.
- `.github/workflows/tv-smoke.yml`: manual TV emulator (1080p, API 34) run of the fork instrumented tests.
- `app/src/test/.../ForkTvHelperTest.kt` and `app/src/androidTest/.../ForkTvHelperInstrumentedTest.kt`.

## Not modified

- Extension/provider loading, player, download engine, search, library, account system.
- The upstream tests (`SubtitleSelectionTest`, `ExampleInstrumentedTest`, `UriSerializerTest`,
  `SerializationClassTester`).
- No streaming sources, providers or extension repositories are bundled.

## AI assistance disclosure

Upstream's [AI-POLICY.md](../AI-POLICY.md) asks contributors to disclose AI assistance. Parts of this
fork were produced with AI assistance (Arena.ai Agent Mode). Nothing from it has been sent upstream as
a pull request; if it ever is, it must carry that disclosure and be tested as the policy requires.
