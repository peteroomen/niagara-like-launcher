# Niagara-like launcher

A minimal Android home screen: favourite app icons down the left edge so the middle of the wallpaper stays clear, calendar and music widgets, and every other app one slide away on an A–Z strip.

## Status

Design stage. The interactive prototype is in [`prototype/index.html`](prototype/index.html). Open it in a browser and try the gestures listed beside the phone. The Android app (Kotlin, Jetpack Compose) starts once the prototype is settled.

## Planned features

- Favourites as a column of icons on the left, with optional names and folders
- A–Z strip for all other apps
- Calendar and music widgets at the top
- Themed icons tinted with wallpaper colours, or the apps' own colour icons
- Notification previews under app names, swipe to dismiss
- Gestures: double-tap to turn the screen off (via [Power Bubble](https://github.com/peteroomen/power-bubble)), swipe down for notifications, swipe up to search
- Hide and rename apps
- Text and accent colours taken from the wallpaper
