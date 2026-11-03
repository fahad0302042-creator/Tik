# Changelog

All notable changes to this fork. Upstream Cloudstream changes are not repeated here; see
[the upstream repository](https://github.com/recloudstream/cloudstream) for those.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) for the fork suffix only.

## [Unreleased]

## [4.8.0-tik.1] — 2026-11-03

Based on upstream Cloudstream `5024ace2922a148d38df2a816b483b529789cf58` (4.8.0, version code 68).

### Added

- Distinct application id `io.github.fahad0302042_creator.tiktv`, so the fork installs next to
  official Cloudstream.
- `tiktv.*` configuration block in `gradle.properties` (app id, name, version, source URL, upstream
  commit, update switch, update owner/repository) validated at Gradle configuration time.
- Original app icon and Android TV banner that do not embed the app name.
- `ForkTvHelper` with `defaultOverscanDp` and `isWithin`, plus unit and instrumented tests.
- TV-only overscan default of 24 dp (`fork_default_tv_overscan_dp`); phones stay at 0 dp.
- "About this fork" entry in Settings linking to the fork source.
- `scripts/validate_fork.py` static checks for the fork invariants.
- GitHub Actions build workflow (APK + unit tests + validation) and a manual TV emulator smoke
  workflow.
- Docs: `README.md`, `docs/FORK_NOTICES.md`, `docs/ANDROID_TV.md`, `docs/TV_TEST_PLAN.md`,
  `docs/UPSTREAM_README.md`.

### Changed

- Back on the TV home screen walks the full ancestor chain, so nested rows, header controls and
  category chips hand focus back to the navigation rail.
- TV row headers are marked as accessibility headings.
- Downloaded player task affinity is scoped to the fork application id.

### Fixed

- The app updater can no longer offer official Cloudstream APKs: app updates are disabled and the
  GitHub owner/repository come from fork configuration. Extension updates keep working.
- Hardcoded localized app names no longer conflict with the generated fork name.
