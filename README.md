# Arrow Game (Android)

Android port of [larry-fuqua/arrow-game](https://github.com/larry-fuqua/arrow-game) — the Linux-native arrow-shading puzzle game.

**Package:** `com.larry.arrowgame`  
**UI name:** Arrow Game

## Features

- Same puzzle generation (shape templates, winding arrows, hole regions)
- Levels: Beginner → Expert
- Touch: slide to highlight an arrow, release to select
- Scoring: `10 × length`, blocked-click penalty, time bonus
- Local / Guest profiles and high scores
- Celebration fireworks + score panel
- Synthesized sound effects (mute toggle in HUD)

## Requirements

- Android Studio **or** Android SDK command-line tools
- JDK 17+
- `minSdk` 26 / `targetSdk` 36

## Build & run

```bash
cd arrow-game-android

# Create local.properties with your SDK path if needed:
# sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk

./gradlew assembleDebug
./gradlew installDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`.

Or open the folder in Android Studio and run the `app` configuration.

## How to play

1. Sign in with a display name or Guest.
2. Pick a level — a puzzle is generated automatically.
3. Slide your finger to highlight an arrow, then release to select.
4. If its path out is clear, it slides out and shades cells. If blocked, it blinks red (−10 pts).
5. Clear blockers first. When every arrow is gone, you win.
6. A time bonus rewards faster clears.

## Project layout

```
arrow-game-android/
  app/src/main/java/com/larry/arrowgame/
    MainActivity.kt
    game/          # Config, Levels, Shapes, Puzzle, FlowAnim, GameViewModel, GameAudio
    data/          # Storage (profiles + scores)
    ui/            # Compose screens + board canvas
```

Logic is a Kotlin port of the Python modules in the Linux repo (`arrow_game/puzzle.py`, `shapes.py`, `levels.py`, etc.).

## License / forking

Public repository — feel free to fork and experiment. Related project: [arrow-game](https://github.com/larry-fuqua/arrow-game) (Linux / pygame).
