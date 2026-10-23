// Live Sketch (docs/Architecture.md, lib/feature): drawing with a real pen — the room's state over
// the sketch engine and the domain's Lib, its screens on the design contract. The pen comes from
// the host, already hooked; boards and concepts are reached through its host (SketchHost).
plugins {
    id("actiondraw.compose")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":lib:domain"))
    api(project(":lib:design:contract"))
    // The drawing engine: pen samples to pixels.
    api(project(":lib:sketch-engine"))
    // The Tune panel's leads, kept as JSON among the preferences.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Its tests stand the lib up over a temp folder and draw in the default design.
    testImplementation(project(":lib:data"))
    testImplementation(project(":lib:design:atelier"))
}
