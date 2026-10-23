// A module of the UI kit (docs/Architecture.md): Kotlin with Compose for Desktop — the design
// system and, from F11.10, the features. It gets Compose's runtime, foundation, Material and ui
// as its API; its tests get the machine's desktop runtime (Skia's natives among it) and Compose's
// test rule. The application's packaging is the app's (org.jetbrains.compose), not the kit's.
plugins {
    id("actiondraw.kotlin-library")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    "api"("org.jetbrains.compose.runtime:runtime:$COMPOSE_VERSION")
    "api"("org.jetbrains.compose.foundation:foundation:$COMPOSE_VERSION")
    "api"("org.jetbrains.compose.material:material:$COMPOSE_VERSION")
    "api"("org.jetbrains.compose.ui:ui:$COMPOSE_VERSION")
    "testImplementation"("org.jetbrains.compose.desktop:desktop-jvm-${skikoTarget()}:$COMPOSE_VERSION")
    "testImplementation"("org.jetbrains.compose.ui:ui-test-junit4:$COMPOSE_VERSION")
    "testRuntimeOnly"("org.junit.vintage:junit-vintage-engine:5.10.2")
}
