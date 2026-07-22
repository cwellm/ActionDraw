import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "de.creaflect.actiondraw"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
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
                iconFile.set(project.file("art/icons/actiondraw.ico"))
                menuGroup = "ActionDraw"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Stable GUID: future versions upgrade in place instead of installing side by side.
                upgradeUuid = "028fad78-4d72-4bab-858a-6e06e449826e"
            }
            linux {
                iconFile.set(project.file("art/icons/actiondraw-512.png"))
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
    args(project.file("art/actiondraw.svg").path, project.file("art/icons").path)
}
