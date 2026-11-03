# TV test plan

Run this on a real Android TV / Google TV device with the remote that ships with it.
Every step should be possible without a mouse, a touchscreen or an air-mouse app.

## 0. Install

- [ ] `adb connect <tv-ip>:5555` then `adb install -r app-stable-debug.apk` succeeds.
- [ ] The app icon appears in the launcher's app row.
- [ ] The launcher **banner** is shown (not a stretched square icon).
- [ ] Official Cloudstream, if installed, still works and still opens its own data.
- [ ] First start: account select screen is reachable with the remote.

## 1. Home screen, D-pad

- [ ] Up/Down moves between the navigation rail and the content rows; focus is always visible.
- [ ] Left/Right inside a row moves card by card and the row scrolls to keep focus on screen.
- [ ] Moving down from a lower card to the next row does not leave focus off screen.
- [ ] Reaching the end of a row does not jump to an unrelated row.
- [ ] Nothing is clipped by the screen edge (overscan default of 24 dp does its job). If your TV does
      not overscan, set **Settings → UI → Overscan** to 0 and confirm it stays 0 after a restart.

## 2. Back button (fork change)

- [ ] Focus deep inside a content row → Back moves focus to the provider/API selector.
- [ ] Focus on the provider selector or another header control → Back moves focus to the home
      navigation rail.
- [ ] Focus on a category/main-page chip → Back moves focus to the home navigation rail.
- [ ] Focus on the navigation rail → Back behaves like before (exit confirmation / exits).
- [ ] No screen where Back does nothing at all.

## 3. Search and results

- [ ] Search is reachable from the rail and a keyboard entry works with the remote.
- [ ] Results grid navigates with the D-pad; selecting a result opens the detail page.
- [ ] On the detail page, season/episode and dub selectors are reachable and focusable.
- [ ] Playback starts with the center button.

## 4. Player

- [ ] Center button plays/pauses.
- [ ] Left/Right seeks; Up/Down adjusts speed or opens the panels as upstream does.
- [ ] Long press left/right seeks faster.
- [ ] Remote media keys (play/pause, rewind, fast forward) work.
- [ ] Back closes the player and returns focus to the previous screen.
- [ ] Subtitles, quality and speed pickers are fully navigable.

## 5. Settings

- [ ] Every settings category opens and all entries are reachable with the D-pad.
- [ ] The overscan slider moves with left/right and the change applies immediately.
- [ ] **Updates** group: the update entries are disabled and labelled
      "App updates are disabled for this fork…".
- [ ] **About this fork** (top of Settings) is focusable and pressing center opens
      `https://github.com/fahad0302042-creator/Tik` in the browser.
- [ ] Extensions screens still load and update extensions.

## 6. Update isolation

- [ ] No notification or dialog ever offers an official Cloudstream APK.
- [ ] With `tiktv.appUpdatesEnabled=false`, `getAppUpdate` reports no update (covered by
      `ForkTvHelperInstrumentedTest.updaterIsIsolatedFromOfficialRepository`).

## 7. Automated

```bash
python3 scripts/validate_fork.py            # fork invariants
./gradlew :app:testStableDebugUnitTest      # ForkTvHelperTest + upstream unit tests
./gradlew :app:connectedStableDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...ForkTvHelperInstrumentedTest
```

- [ ] `ForkTvHelperInstrumentedTest` passes on a TV/emulator (focus helpers, application id, leanback
      launcher entry, update isolation).
- [ ] `scripts/validate_fork.py` reports "All fork checks passed".

`.github/workflows/tv-smoke.yml` runs the instrumented tests on a `tv_1080p` API 34 emulator and can
be triggered manually from the Actions tab.
