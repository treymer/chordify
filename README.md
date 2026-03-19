# Cadence

An Android music utility app with six tools: a chromatic tuner, a key finder, a metronome, a music theory suggester, and a note finder.

## Features

### Tuner
A chromatic tuner that listens to your microphone and shows the nearest note with a semicircle gauge displaying cents deviation (±50¢). Color-coded feedback — green when in tune (≤10¢), amber when close (≤25¢), red when off.

### Key Finder
Detects the musical key of a song or progression. Play 3 distinct notes and the app identifies all matching major/minor keys, displayed as styled result cards.

### Metronome
A tap-tempo metronome with an adjustable BPM (40–240). A green circle flashes on each beat and a synthesized click plays through the speaker. BPM can be dialed in with a slider or ±1/±10 increment buttons.

### Theory
Select a genre, key, and Major/Minor mode to get curated music theory content across six swipeable tabs:

- **Progression** — chord progressions with Roman numeral analysis and links to chord diagrams
- **Scales** — recommended scales with links to fingering diagrams
- **Arpeggios** — genre-appropriate arpeggios with diagram links (Google Search fallback for arpeggios not on the reference site)
- **Rhythm** — 5 strum patterns per genre displayed on a 16-subdivision grid with ↓/↑/✕ notation
- **Intervals** — all 12 intervals from the selected root note with symbols, names, semitone counts, and resulting notes
- **Extensions** — extended chord shapes (sus2, sus4, maj9, dom9, m9, dom11, dom13) with diagram links

Supported genres: Rock, Blues, Jazz, Funk, Metal, Country, Reggae, Pop, R&B

A **Capo helper** below the key selector suggests capo positions and open-chord shapes (C, D, E, G, A) to simplify playing in any key.

### Note Finder
Select any note and see every position it appears across the guitar neck, shown across two fretboard diagrams (frets 0–12 and 13–24). Inlay markers and fret numbers are included for reference.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Pitch detection | [TarsosDSP](https://github.com/JorenSix/TarsosDSP) |
| Metronome audio | Android `AudioTrack` (PCM synthesis) |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| Build system | Gradle 8 with Kotlin DSL |

---

## Requirements

- Android Studio (Hedgehog or newer)
- JDK 17 — bundled with Android Studio at `/Applications/Android Studio.app/Contents/jbr/`
- Android SDK 36
- A device or emulator running Android 7.0+

> **Note:** If Gradle fails with a JDK version error, set `org.gradle.java.home` in your local `~/.gradle/gradle.properties` to point at JDK 17. Android Studio's bundled JDK is at `/Applications/Android Studio.app/Contents/jbr/Contents/Home` on macOS.

---

## Building Locally

Clone the repo and build a debug APK:

```bash
git clone https://github.com/treymer/cadence.git
cd cadence
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

**Install directly to a connected device:**

```bash
./gradlew installDebug
```

Your device must have USB debugging enabled (Settings → Developer Options → USB Debugging).

**Run unit tests:**

```bash
./gradlew test
```

---

## CI/CD

GitHub Actions runs on every push and pull request to `main`. Pushes that only change documentation (e.g. `*.md` files) skip the workflow entirely.

| Job | Trigger | What it does |
|-----|---------|-------------|
| Unit Tests | Push + PR | `./gradlew testDebugUnitTest` |
| Build | After tests pass | Builds signed release AAB, uploads as artifact (14-day retention) |
| Release | Code push to `main` only | Creates a GitHub Release with the AAB attached |
| Publish | Code push to `main` only | Uploads AAB to Google Play internal track |

See [`.github/workflows/android.yml`](.github/workflows/android.yml) for the full pipeline.

---

## Permissions

| Permission | Why |
|-----------|-----|
| `RECORD_AUDIO` | Required by the Tuner and Key Finder to access the microphone |

The Metronome, Theory, and Note Finder features do not require any permissions.
