# diskok

🎯 A physics-based mobile game where you bounce a ball to reach targets — simple, fast levels, satisfying ricochets.

One ball, one target. **Drag to pull back and aim — a dotted arc previews the
shot — then release to launch.** Watch the ball arc, bounce, and (hopefully)
kiss the pad. Miss and the level restarts instantly. Pastel, geometric, fluid —
inspired by the clean minimalism of Philipp Stollenmayer's games.

## Tech

Native **Android (Kotlin)**, single Gradle module. The game runs on a
`SurfaceView` with a dedicated render thread synced to the display
(`lockCanvas`/`unlockCanvasAndPost`), so it stays smooth at 60/90/120 Hz with a
tiny footprint and no game-engine dependency. Physics (gravity, restitution,
moving/rotated bouncers, continuous-ish collision via sub-stepping) is a compact
custom step in `Engine.kt`.

Key files (`app/src/main/java/com/diskok/game/`):

- `Engine.kt` — state machine, physics, rendering.
- `Levels.kt` — the 18 levels (normalized, resolution-independent).
- `Theme.kt` — the pastel palettes.
- `GameView.kt` — `SurfaceView`, render loop, input, haptics.
- `MainActivity.kt` — fullscreen immersive host.

## Build & run

Requires JDK 17+ and the Android SDK (set `sdk.dir` in `local.properties` or
open the project in Android Studio, which configures it for you).

```bash
# Debug APK on a connected device/emulator
./gradlew installDebug

# Or just build the debug APK
./gradlew assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
```

## Publish to Google Play

Google Play expects a signed **App Bundle (.aab)**.

1. Create an upload keystore (once):
   ```bash
   keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA \
     -keysize 2048 -validity 10000 -alias upload
   ```
2. Add signing config to `app/build.gradle.kts` (or use Android Studio's
   *Build > Generate Signed Bundle*), then:
   ```bash
   ./gradlew bundleRelease   # -> app/build/outputs/bundle/release/app-release.aab
   ```
3. Upload the `.aab` in the Google Play Console. Provide a 512×512 icon, feature
   graphic, and screenshots there (the launcher icon ships as an adaptive vector
   in the app).

`applicationId` is `com.diskok.game`, `versionCode` 1 / `versionName` 1.0 — bump
these in `app/build.gradle.kts` for each release.
