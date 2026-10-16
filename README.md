# Niagara-like launcher

A minimal Android home screen. Favourite app icons sit in a column on the left, so the middle of the wallpaper stays clear. Calendar and music widgets are at the top, and every other app is one slide away on the A–Z strip.

The design came from the interactive prototype in [`prototype/index.html`](prototype/index.html).

## Features

- **Favourites:** a column of icons centred on the left. Names are optional. Folders show their first four apps and open as a row beside the folder.
- **Pie mode** (setting): no column. Hold anywhere on empty space and your first 8 favourites (apps or folders) appear in a ring around your finger. Slide towards one and let go to open it, or let go in the middle for the home menu. With a Loud combo accent (Pop Garden, Baby Blue or Rave) the pie is stamped down instead: the disc lands oversized and tilted with a hard ink shadow and outline, and icons punch straight out like slapped-on stickers. Slide onto a folder and its apps fan out in an arc beyond it; keep going outward and sweep along the arc to pick one.
- **Edit favourites:** tick apps on and off and drag the handles to reorder. Each folder's apps are listed under it, with buttons to move one out or remove it; tap a folder to rename it. Drop an app on a folder to add it, or on another app to make a new folder, or use **New folder** to name one and tick its apps. It's in the hold menus for apps and for empty space.
- **Icons:** themed (the app's monochrome icon tinted with wallpaper colours, Android 13+), colour or greyscale. Three sizes and three shapes (rounded, circle, square).
- **Readability:** text shadow from off to strong, an optional fade or dim behind the text, and text colour from the wallpaper or forced light or dark.
- **Look:** accent colour (from the wallpaper, a fixed colour, or one of the Loud launcher's eight palettes: Taxi, Riso, Acid, Night Brutal, Candy, Pop Garden, Baby Blue or Rave, which go fully Loud: themed icon tiles get the combo colours, and glass and blur give way to solid outlined panels with hard shadows everywhere), font (system, condensed, serif, mono), and clock size and weight.
- **Liquid glass** (setting): on Android 13+ the edges bend the wallpaper like a lens and fade into a frosted middle ("Bend edges", on by default). Icon tiles, folders, the music card, folder rows, the pie, the big A–Z letter bubble (with a gentler lens), the search box when lists sit on the wallpaper, and the search panel (80% of the screen tall) show the wallpaper behind them blurred and slightly more vivid, under a light tint for contrast, with a hairline rim. To blur the real wallpaper the launcher needs to read it: allow wallpaper access (Android calls it "All files access"), or pick your wallpaper photo in Launcher settings, which sets it as the wallpaper and keeps a copy. Without either, glass uses a frosted tint. The whole wallpaper also blurs behind search, the A–Z list and the pie on phones that support window blur.
- **Portrait lock:** the home screen stays upright by default; turn it off in settings.
- **Notification dots:** on icons and folders, respecting each app's dot setting.
- **Widgets:** the next calendar event within a week, and a music player card with album art, progress, and previous, play/pause and next buttons.
- **A–Z strip:** drag along the right edge to jump through every app, listed with icons. Slide left onto a name and let go to open it.
- **Search and app list style** (setting): a panel from the bottom (80% tall, edge to edge, rounded top corners; glass with liquid glass on), or wallpaper only (no background, the rest of the home screen fades away while it's open). Both search and the A–Z list use the same style.
- **Full-width rows:** favourites, the A–Z list and search results can be tapped anywhere along their line.
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
