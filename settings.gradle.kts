rootProject.name = "ActionDraw"

// Three groups of modules (docs/Architecture.md). Each depends only on what it declares, and
// Gradle holds it to that: lib knows nothing of app or device.

// lib — what an application builds on. The bare lib is headless: the core, the network, the
// drawing engine (a library of its own).
include(":lib:core:model")
include(":lib:core:io")
include(":lib:core:lifecycle")
include(":lib:network")
include(":lib:sketch-engine")

// device — the hardware the apps talk to natively.
include(":device:pen")

// app — the applications on top.
include(":app:desktop")

pluginManagement {
    // The modules' shared setup: Kotlin on Java 17 and their tests (actiondraw.kotlin-library),
    // Skia on top for those that draw or decode (actiondraw.skia).
    includeBuild("build-logic")
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
