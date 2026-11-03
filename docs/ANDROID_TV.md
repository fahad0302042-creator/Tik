# Android TV / Google TV notes

## Layout selection

Upstream Cloudstream picks one of three layouts and this fork keeps that mechanism untouched:

| `app_layout_key` | Layout | Meaning |
| --- | --- | --- |
| `-1` (Auto, default) | TV or PHONE | TV when the device reports a television/leanback UI mode, otherwise phone |
| `0` | PHONE | Force the phone layout |
| `1` | TV | Force the TV layout |
| `2` | EMULATOR | Landscape/emulator phone layout with TV-ish navigation |

Auto detection uses `DeviceLayout.isAutoTv` (leanback / television UI mode). On a Google TV device
you should not have to change anything. You can still force the TV layout in
**Settings → UI → Layout** if a device reports a phone UI mode.

## Launcher presence

`AccountSelectActivity2` declares both `android.intent.category.LAUNCHER` and
`android.intent.category.LEANBACK_LAUNCHER`, so the fork appears in the Android TV launcher. The
`android:banner` attribute points at `@drawable/fork_banner` (320x180 dp, `res/drawable/fork_banner.xml`);
without a banner Google TV shows a stretched, low resolution icon instead.

The manifest also marks the touchscreen and leanback features as optional, and it declares the
`com.android.providers.tv.permission.READ_EPG_DATA` / `WRITE_EPG_DATA` permissions that upstream uses
for "watch next" rows.

## Overscan

Some televisions crop the edges of the picture. Cloudstream ships an overscan slider
(**Settings → UI → Overscan**, in dp) that defaults to 0, which means an overscanning TV can cut off
the first row of posters.

The fork defaults that slider to 24 dp on TV layouts only (`res/values/fork_config.xml`,
`fork_default_tv_overscan_dp`), leaving phones and the emulator layout at 0. An explicit user choice —
including 0 — always wins, and the value is written the first time the preference screen is opened, so
"never touched" stays distinguishable from "set to 0".

## Remote (D-pad) navigation

What already works upstream and is preserved:

- D-pad navigation through the navigation rail, home rows, search, library and settings.
- Focus-driven scrolling on the home page: moving to a row scrolls it into view.
- Player keys: D-pad center/play-pause, left/right to seek, up/down to change speed or open the
  subtitle/episode panels, long press for fast seek, media keys from the remote.
- Every focusable control is reachable; the TV layouts avoid touch-only affordances.

What the fork changes:

- **Back on the TV home screen.** Upstream only checked whether the focused view's direct parent was
  a known container, which breaks as soon as an extra layout wraps a row. `ForkTvHelper.isWithin`
  walks the whole ancestor chain, so Back moves focus from a content row to the provider selector and
  from any header/category chip to the home navigation rail, no matter how deeply nested the focused
  view is.
- **Accessibility.** TV row headers are marked `accessibilityHeading="true"`, so TalkBack and switch
  access announce the row name when you move between rows.

## Installing

- `adb install -r app-stable-debug.apk` after `adb connect <tv-ip>:5555` (enable developer options and
  USB/network debugging on the TV).
- Or copy the APK to a USB drive and install it with a TV file manager (you need "install unknown
  apps" permission for that file manager).
- The application id differs from the official one, so the fork installs **next to** official
  Cloudstream without overwriting it. Data is not shared between them.

## Known limitations

- No Firebase/Play services features; upstream does not require them.
- Official Cloudstream extensions load normally, but extension repositories are third party URLs —
  nothing is bundled here.
- App updates are disabled until this repository publishes signed releases
  (`tiktv.appUpdatesEnabled` in `gradle.properties`).
