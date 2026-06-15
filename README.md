# ActionDraw

A small desktop tool for **action drawing**: point it at a folder of reference images and it shows
them one at a time, in random order, on a configurable timer — to practice and improve your drawing
skills. Built with Compose for Desktop (Kotlin/JVM), so the same code runs on Windows and Linux
(e.g. ArchLinux).

## Features
- Pick a folder; images are shown in **random order**.
- **Time per image** configurable from 30 s to 10 min, in 30 s steps.
- Controls: **Play** (restart the timer for the current image), **Pause/Resume**, **Stop** (back to
  the menu), **Prev**, **Next**. The timer auto-advances when it runs out.
- Adjust the time **while paused** — the elapsed time is left untouched.
- Live filters: **Black & white**, **Blur**, **Upside down** (more in [IDEAS.md](IDEAS.md)).
- **Fullscreen** toggle (`F`): the image fills the entire screen with only the remaining time in the
  bottom corner — no controls. `Esc` leaves fullscreen and restores the normal resizable window.
- Keyboard shortcuts: `Space` play/pause, `←`/`→` prev/next, `F` fullscreen, `Esc` exit fullscreen
  (or stop the session and return to the menu when already windowed).
- **Remembers what you've drawn**: images already shown are recorded per folder in
  `.actiondraw_seen.txt` and skipped next time. Once every image has been shown, the cycle resets.

## Requirements
- JDK 17 (a `JAVA_HOME` pointing at a JDK 17 install).

## Run
```sh
./gradlew run        # on Windows: gradlew.bat run
```

## Test
```sh
./gradlew test
```

## Build a native installer
```sh
./gradlew packageMsi    # Windows installer
./gradlew packageDeb    # Debian/Linux package
```
(On ArchLinux you can also just `./gradlew run`, or build a distributable with
`./gradlew createDistributable`.)
