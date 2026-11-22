# Elvan Gallery

A photo and video gallery for Android, designed and built by **Elvan Parthasarathy** with **Elvan Navil UI**.

## What it does

- **Pictures** under date headings; pinch for bigger or smaller tiles; hold and slide to select a run of pictures
- **Albums** with groups, essential albums, your own names, and drag-to-arrange
- **Stories** made from your busiest days
- **Search** by name, album or kind (videos, camera, screenshots, downloads)
- **Viewer**: pictures grow out of their tile and shrink back; swipe up for details; drag down to close; pinch and double-tap zoom; videos play in place with a frame strip to seek
- **Photo editor**: crop and straighten, rotate, flip, filters, tone (light balance, brightness, exposure, contrast, saturation, temperature, tint, vignette), drawing, stickers and text
- **Video editor**: trim, rotate, filters and tone, text, speed, sound on or off
- **Favourites, Recent, Videos and Trash** (restore or delete for good)
- **Private Safe** behind your phone's lock screen
- **Quote cards** in Elvan Sans

Everything you edit is saved as a new file; your originals are never changed.

## Elvan Navil UI

The look and motion come from Elvan Navil UI: the large title that folds into a pill as you scroll, the floating pill
navbar, frosted bars, selection pills and Elvan Sans. The library lives in the
[Udukkai](https://github.com/ElvanParthasarathy/Udukkai) project (`kmp/elvanNavil`).

## Building

1. In Udukkai's `kmp` folder, publish Elvan Navil UI to your local Maven repository:
   `./gradlew :elvanNavil:publishToMavenLocal`
2. In this folder: `./gradlew :app:assembleDebug`

Android 8.1 or newer. Kotlin, Jetpack Compose, Coil and Media3.

---

© Elvan Parthasarathy
