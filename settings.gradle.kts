rootProject.name = "ActionDraw"

// The drawing engine is a library of its own: pure Kotlin/JVM over Skia, no Compose, testable
// headless (docs/Pencil-Engine-Architecture.md). The app depends on it; nothing depends on the app.
include(":sketch-engine")

// The modules of M10 (ROADMAP), bottom up: what the app is made of, then how it is stored, then
// the rooms. Each depends only on the ones below it, and Gradle holds it to that.
include(":core:model")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
