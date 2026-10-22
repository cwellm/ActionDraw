// The root holds no code: the modules are in lib/, app/ and device/ (docs/Architecture.md). It
// declares the plugins' versions once, for every module, and the lint every module gets.
plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    // The lint gate on every pull request (.github/workflows/lint.yml); the rules are in .editorconfig.
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
    }
}
