rootProject.name = "ActionDraw"

// The drawing engine is a library of its own: pure Kotlin/JVM over Skia, no Compose, testable
// headless (docs/Pencil-Engine-Architecture.md). The app depends on it; nothing depends on the app.
include(":sketch-engine")

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
