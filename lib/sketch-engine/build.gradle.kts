// The drawing engine: everything between a pen sample and a pixel, and nothing else. Pure
// Kotlin/JVM over Skia (skiko), no Compose dependency, so it can be tested headless, published on
// its own later, and replaced without touching the app. See docs/Pencil-Engine-Architecture.md.
plugins {
    // Kotlin, Java 17, Compose's own Skia and its native runtime for the headless tests.
    id("actiondraw.skia")
    kotlin("plugin.serialization")
}

group = "de.creaflect.sketch"
version = "0.1.0"

dependencies {
    // The sketch document (.sketch.json): the strokes with their samples, replayable.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
