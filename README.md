# SP Player

A native, fully offline audio-video player for low-end Android phones.
Minimal UI, simple colourful themes with day/night mode, auto-scan library,
battery-friendly playback via Media3 ExoPlayer (hardware decoders).

**No internet permission. No ads. No analytics. No accounts.**

By [SP DEV 22](https://sp-dev-22.github.io) — [YouTube](https://www.youtube.com/@sp-dev-22)

## Download

Get the latest APK from the [Releases page](https://github.com/sp-dev-22/sp-player/releases).
Every release installs as an update over the previous one.

## Features (v1.2.0)

- Auto-scan library (MediaStore): Songs / Videos / Folders, search, sort by name/date
- Play/pause, prev/next, seek, shuffle, repeat (All/One/Off)
- Video: fullscreen, volume/brightness swipe gestures, subtitles (auto same-name `.srt` + manual pick)
- Audio: bottom mini-player, background play with notification controls,
  own in-app volume slider (default 25%, independent of system volume)
- Play Video as Audio, Background Play Video
- Lock Video Player Screen, Picture-in-Picture (on supported devices)
- Separate sleep timers for audio and video (5/10/15/30/45/60 min)
- Select & Add to Playlist ("My Playlist")
- Playback speed (0.5x–2x)
- Accent themes: Pink (default), Blue, Green, Orange, Rainbow; day/night auto + manual toggle

Formats are whatever Android natively decodes: MP4, MKV, 3GP, WebM, MP3, M4A,
WAV, OGG, FLAC. No ffmpeg/VLC-style engine on purpose — it would bloat the APK
and drain the battery on a Go phone.

## Build

GitHub Actions builds on every push to `main` (APK artifact + versioned
Release) and on `v*` tags. CI installs Gradle 9.3.1 itself (same setup as
sp-launcher); the Gradle wrapper jar is intentionally not in the repo.

Local build (needs JDK 17 + Android SDK 34):

```sh
gradle assembleDebug
```

## Signing

The debug keystore is committed as `debug.keystore.b64` (base64 text) and
decoded by a Gradle `preBuild` task (and by CI) before every build, so all
updates install over previous versions. The binary `debug.keystore` is
git-ignored and never committed. This is a free personal app; the keystore
approach mirrors the SP WEB OS pattern.

## License

[PolyForm Noncommercial License 1.0.0](LICENSE) — free for noncommercial use.
Copyright 2026 Somdatt Prajapat.
