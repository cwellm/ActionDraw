// The drawing engine: everything between a pen sample and a pixel, and nothing else. Pure
// Kotlin/JVM over Skia (skiko), no Compose dependency, so it can be tested headless, published on
// its own later, and replaced without touching the app. See docs/Pencil-Engine-Architecture.md.
plugins {
    kotlin("jvm")
}

group = "de.creaflect.sketch"
version = "0.1.0"

val skikoVersion = "0.8.18" // the one Compose 1.7.3 ships; one Skia in the app, not two

dependencies {
    api("org.jetbrains.skiko:skiko-awt:$skikoVersion")
    testImplementation(kotlin("test"))
    // Headless tests draw into a real Skia surface, which needs the native runtime for this OS.
    testRuntimeOnly("org.jetbrains.skiko:skiko-awt-runtime-${skikoTarget()}:$skikoVersion")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

/** `windows-x64`, `linux-x64`, `macos-arm64` … — the skiko runtime artifact for this machine. */
fun skikoTarget(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val platform = when {
        os.startsWith("windows") -> "windows"
        os.startsWith("mac") -> "macos"
        else -> "linux"
    }
    val cpu = if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"
    return "$platform-$cpu"
}
