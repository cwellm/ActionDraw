import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The desktop application, on top of everything (docs/Architecture.md): its windows and rooms,
// the desktop's own clipboard, dialogs and browser, and the pen from device/pen. `./gradlew run`
// starts it; `./gradlew packageMsi` and `packageDeb` build its installers.
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "de.creaflect.actiondraw"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    // The drawing engine (Live Sketch), a library of its own: docs/Pencil-Engine-Architecture.md.
    implementation(project(":lib:sketch-engine"))
    // Boards, cards, concepts, session plans: the data, with no Compose and no files in it.
    implementation(project(":lib:core:model"))
    // Files: the app's own folders, safe writes, moving and deleting folders, reading pictures.
    implementation(project(":lib:core:io"))
    // The lib's run: its scope and dispatcher, the writes it must not lose, closing cleanly.
    implementation(project(":lib:core:lifecycle"))
    // The one way online: HTTP for link previews, http and https only.
    implementation(project(":lib:network"))
    // The pen's own input: Windows Ink, through JNA.
    implementation(project(":device:pen"))
    // Windows' animation setting (SystemPreferences) is read through JNA too.
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    // Reading and writing the sidecar files (.actiondraw_board.json, .actiondraw_concept.json)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    // Drives the real composables in tests: pointer behaviour (what a click actually selects)
    // cannot be checked any other way.
    testImplementation(compose.desktop.uiTestJUnit4)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "de.creaflect.actiondraw.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ActionDraw"
            packageVersion = "1.0.0"
            description = "Timed reference drawing practice"
            copyright = "© 2026 creaflect"
            vendor = "creaflect"

            windows {
                iconFile.set(rootProject.file("art/icons/actiondraw.ico"))
                menuGroup = "ActionDraw"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Stable GUID: future versions upgrade in place instead of installing side by side.
                upgradeUuid = "028fad78-4d72-4bab-858a-6e06e449826e"
            }
            linux {
                iconFile.set(rootProject.file("art/icons/actiondraw-512.png"))
                packageName = "actiondraw"
                menuGroup = "Graphics"
                appCategory = "Graphics"
                shortcut = true
            }
        }
    }
}

// Render art/actiondraw.svg into art/icons/ (PNGs + multi-res .ico) using the bundled Skia engine.
tasks.register<JavaExec>("genIcons") {
    group = "distribution"
    description = "Generate icon assets from art/actiondraw.svg"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.creaflect.actiondraw.tools.IconGenKt")
    args(rootProject.file("art/actiondraw.svg").path, rootProject.file("art/icons").path)
}
