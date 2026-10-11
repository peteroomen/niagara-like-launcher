# Niagara-like launcher

A minimal Android home screen. Favourite app icons sit in a column on the left, so the middle of the wallpaper stays clear. Calendar and music widgets are at the top, and every other app is one slide away on the A–Z strip.

The design came from the interactive prototype in [`prototype/index.html`](prototype/index.html).

## Features

- **Favourites:** a column of icons centred on the left. Names are optional. Folders show their first four apps and open as a row beside the folder.
- **Pie mode** (setting): no column. Hold anywhere on empty space and your first 8 favourites appear in a ring around your finger. Slide towards one and let go to open it, or let go in the middle for the home menu.
- **Edit favourites:** tick apps on and off, and drag the handles to reorder. It's in the hold menus for apps and for empty space.
- **Icons:** themed (the app's monochrome icon tinted with wallpaper colours, Android 13+), colour or greyscale. Three sizes and three shapes (rounded, circle, square).
- **Readability:** text shadow from off to strong, an optional fade or dim behind the text, and text colour from the wallpaper or forced light or dark.
- **Look:** accent colour (from the wallpaper or a fixed colour), font (system, condensed, serif, mono), and clock size and weight.
- **Portrait lock:** the home screen stays upright by default; turn it off in settings.
- **Notification dots:** on icons and folders, respecting each app's dot setting.
- **Widgets:** the next calendar event within a week, and a music player card with album art, progress, and previous, play/pause and next buttons.
- **A–Z strip:** drag along the right edge to jump through every app, listed with icons. Slide left onto a name and let go to open it.
- **Clock:** tap the time to open the Clock app, or the date for your calendar.
- **Gestures:**
  - Double-tap empty space to turn the screen off (needs [Power Bubble](https://github.com/peteroomen/power-bubble)).
  - Swipe down for notifications.
  - Swipe up to search; Enter opens the top result or searches the web.
- **Hold an app** (on the home screen, in the A–Z list or in search results): add it to favourites or a folder, move it out of a folder, rename, hide, see App info, or uninstall.
- **Hold a folder:** rename it, ungroup it, or remove it.
- **Hold empty space:** wallpaper and launcher settings.
- **Wallpaper colours:** text, accent and icon colours come from the wallpaper and switch to dark text on light wallpapers.

## Install

1. Download the APK from the latest run of the **Build** workflow (Actions tab → latest run → *launcher-debug*), or build it with `./gradlew assembleDebug`.
2. Open **Launcher settings** (it's in the app list) and tap **Make this your home app**.
3. In the same screen:
   - Turn on notification access, for dots and the music widget.
   - Allow calendar access, for the calendar widget.
   - Android 13+ blocks notification access for sideloaded apps at first. Go to *App info → ⋮ → Allow restricted settings*, then try again.
4. Install Power Bubble and turn on its accessibility service, so double-tap can turn the screen off.

Requires Android 12 or later.

## Code

| Path | What |
|---|---|
| `model/Fav.kt` | Favourites and folders, and every edit to them (unit tested) |
| `data/` | Installed apps (`LauncherApps`), icon cache, saved state (DataStore), calendar, first-run favourites |
| `system/` | Notification listener for dots, media sessions for the music widget, wallpaper colours, system intents |
| `ui/` | Compose UI: home screen, icons and folders, widgets, A–Z list and search, hold menus |
| `SettingsActivity.kt` | Setup and appearance settings |

```sh
./gradlew testDebugUnitTest assembleDebug
```
