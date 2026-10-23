// The build's shared setup, as plugins of its own (ROADMAP F11.2): a module says what kind it is
// — plain Kotlin, Kotlin with Skia, or a module of the UI kit with Compose — and gets Kotlin,
// Java 17 and its tests from here.
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
