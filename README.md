# Hudsify 2.0 — Full Foundation

**FEEL EVERY BEAT.**

This repository is a substantial runnable foundation for an original Android music/video app.

## Included
- Hudsify home with Music / Video entry points
- Device music scanning through MediaStore
- Search by title / artist / album
- Media3 playback
- Queue and media-item transitions
- Previous / next
- Shuffle / repeat
- Seeking and progress
- Full Now Playing screen
- Mini player
- Persistent favorites
- Persistent playlists
- Playlist creation
- Synced LRC lyrics parser + synchronized lyric UI
- Video player foundation with Media3 PlayerView
- Authorized remote video URL playback
- Subtitle-ready Media3 architecture
- Settings persistence
- GitHub Actions build workflow
- Hudsify visual system

## What is deliberately NOT bundled
Hudsify does not ship Spotify proprietary source code, Spotify credentials, DRM circumvention, scraped copyrighted catalogs, or an ad/Premium bypass.

For streaming, plug in APIs/catalogs and media you are authorized to use.

## Build
Open in Android Studio. If Android Studio asks to upgrade Gradle/AGP, accept the compatible upgrade.

A normal project checkout should then build with:

```bash
./gradlew assembleDebug
```

## Next production integrations
The architecture is ready for:
- authorized music catalog API
- account/authentication
- cloud sync
- download manager for licensed media
- subtitle file picker + Media3 SubtitleConfiguration
- lyrics provider
- artist/album metadata
- recommendations
- server-side entitlement system
- real audio/video source adapters
