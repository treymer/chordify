# Cadence

An Android music utility app with four tools: a chromatic tuner, a key finder, a metronome, and a chord/scale/arpeggio suggester.

## Features

### Tuner
A chromatic tuner that listens to your microphone and shows the nearest note with a semicircle gauge displaying cents deviation (±50¢). Color-coded feedback — green when in tune (≤10¢), amber when close (≤25¢), red when off.

### Key Finder
Detects the musical key of a song or progression. Play 3 distinct notes and the app identifies all matching major/minor keys, displayed as styled result cards.

### Metronome
A tap-tempo metronome with an adjustable BPM (40–240). A green circle flashes on each beat and a synthesized click plays through the speaker. BPM can be dialed in with a slider or ±1/±10 increment buttons.

### Suggester
Select a genre (Rock, Blues, Jazz, Funk, Metal), key, and Major/Minor mode to get curated chord progressions, scales, and arpeggios. Shows the relative key and links to chord diagrams.

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

## Installing from a GitHub Release

Every push to `main` triggers a CI build that attaches a debug APK to a [GitHub Release](https://github.com/treymer/cadence/releases).

To sideload it:
1. Download `app-debug.apk` from the latest release
2. Transfer it to your Android device
3. On the device, open the file — you may need to allow installs from unknown sources in Settings → Security

---

## CI/CD

GitHub Actions runs on every push and pull request to `main`. Pushes that only change documentation (e.g. `*.md` files) skip the workflow entirely — a release is only created when app code changes.

| Job | Trigger | What it does |
|-----|---------|-------------|
| Unit Tests | Push + PR | `./gradlew test` |
| Build | After tests pass | Builds debug APK, uploads as artifact (14-day retention) |
| Release | Code push to `main` only | Creates a GitHub Release with the APK attached |

See [`.github/workflows/android.yml`](.github/workflows/android.yml) for the full pipeline.

---

## Permissions

| Permission | Why |
|-----------|-----|
| `RECORD_AUDIO` | Required by the Tuner and Key Finder to access the microphone |

The Metronome does not require any permissions.
