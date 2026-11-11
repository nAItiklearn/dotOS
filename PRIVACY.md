# Privacy

**DotOS collects nothing and sends nothing.**

DotOS Launcher has no `INTERNET` permission (it is explicitly removed from the merged manifest) and no `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, location or advertising-id permission. CI inspects the built APKs with `aapt2 dump permissions` and fails the build if any of those appear. The app has no analytics, no crash reporting, no advertising, no account, no sign-in and no update check. Everything it needs (wallpapers, icons, fonts, widgets) is inside the APK.

This is an unofficial, fan-made project and is not affiliated with Nothing Technology Limited.

## What is stored, and where

Everything is stored on your device in DotOS's private app storage. No other app can read it, and Android's cloud backup and device-transfer are switched off for DotOS (`allowBackup="false"` and exclusion rules for every data domain).

| Data | Location | Purpose |
|---|---|---|
| Settings | DataStore file `launcher_settings` (one JSON document) | Your choices: theme, grid, icon style, gestures, wallpaper config |
| Home layout | Room database `dotos.db`, table `items` | What is on your home pages, dock and in folders |
| Hidden apps | `dotos.db`, table `hidden_apps` | Which apps you hid |
| Icon overrides | `dotos.db`, table `app_overrides` | Per-app icon choices |
| App launch counts and last-launched time | `dotos.db`, table `app_usage` | The "Suggestions" tile in the App Library |
| Saved wallpaper presets | `dotos.db`, table `wallpaper_presets` | Your own wallpaper looks |
| Widget state | `dotos.db`, table `widget_state` | Sticky note text, counters, timers, Snake high score, Quick Capture notes, Away Time screen on/off history, Photo widget image reference |
| Voice recordings | `files/recordings/*.m4a` | Recorder widget and the voice memo in Quick Capture |
| Photos you pick | `files/widget_photos/` and `files/wallpapers/` | Photo widget and photo wallpaper (private copies made through the system Photo Picker) |
| Icon cache | `cache/icon_masks/` | Pre-computed icon shapes, safe to delete (Settings > Performance > Clear icon cache) |
| Crash counter | SharedPreferences `crash_guard` | A count and a timestamp used to enter safe mode after repeated start-up crashes. No stack traces or device data. |

"Reset everything" in Settings > About erases settings, layout, hidden apps, icon overrides and wallpaper photos. Uninstalling DotOS removes all of the above.

## Backups

Backups are made only when you ask. Settings > Backup saves one `.dotbackup` zip to a location you choose through the Android file picker (Storage Access Framework), and restores from a file you choose. It contains your settings, home layout, hidden apps, icon overrides and saved wallpaper presets. It does not contain recordings, notes, photos or Away Time history. DotOS never uploads the file; where you keep it is up to you.

## Optional permissions

DotOS asks for these only when you first use the feature that needs them, and works without them.

| Permission | Unlocks | Notes |
|---|---|---|
| `RECORD_AUDIO` | Recorder widget, voice memo in Quick Capture | Recording starts only when you tap. Files stay in app-private storage. |
| `ACTIVITY_RECOGNITION` | Pedometer widget | Reads the phone's step-counter sensor; counts stay on the device. |
| `READ_CALENDAR` | Upcoming events in the Date & Calendar widget | Read live from the calendar provider; not copied or stored. |
| Notification access (special access) | Notification badges on icons; now-playing info in the Music widget | See below. |
| Accessibility service (special access) | "Lock screen" gesture | See below. |

DotOS never asks for contacts, location, camera, storage or phone permissions.

### Notification listener (local only)

If you enable notification access, DotOS counts the active, clearable notifications per app to draw badges. It looks only at each notification's package name and whether it is clearable, ongoing or a group summary, not at its text. The Music widget uses the same access to read the active media session (title, artist, cover art, playback state) so it can show what is playing. Badge counts live in memory only and are discarded when the listener disconnects. Nothing leaves the device.

### Accessibility service (lock screen only)

The accessibility service exists for a single purpose: performing the system "lock screen" action when you trigger the gesture. It is declared without permission to retrieve window content, and it ignores all accessibility events. It is off until you enable it in Android settings.

## Always-present permissions

`QUERY_ALL_PACKAGES` lets the launcher list every launchable app on the device and find installed icon packs; the list is held in memory and is not stored or sent anywhere. `BIND_APPWIDGET` hosts widgets. `EXPAND_STATUS_BAR` opens the notification shade from a gesture. `REQUEST_DELETE_PACKAGES` powers the Uninstall action. `USE_BIOMETRIC` protects the hidden apps list with your fingerprint or screen lock. `VIBRATE` provides haptic feedback. `SET_WALLPAPER` is declared for handing the wallpaper to the system.

## Things outside DotOS's control

- Widgets from other apps that you place on the home screen are code from those apps. DotOS can host and clip them but cannot stop them from using the network.
- Links such as "Source code" in About are opened by your browser, not by DotOS.
- Android itself, your device maker or an app store may collect diagnostics about any app. DotOS does not integrate with any of them.
- Hiding an app only hides it inside DotOS. Other launchers and system Settings still show it.

## Changes and questions

Any change that adds a permission or data flow must update this file and the README permission table in the same pull request. Questions: open an issue at https://github.com/nAItiklearn/dotOS/issues.
