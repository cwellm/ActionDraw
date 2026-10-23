// The domain, implemented (docs/Architecture.md, lib/data): the repositories over the files —
// boards, concepts, pictures and their thumbnails, practice history, sketches, settings, link
// previews — mapping what is stored to what callers see, and writing off the UI thread through the
// lib's run. The only module that reaches the files or the network: it takes the core and the
// network as `implementation`, so they are on nobody else's classpath.
plugins {
    // Pictures are decoded, scaled and drawn onto contact sheets with Skia.
    id("actiondraw.skia")
}

dependencies {
    // What it implements, and so what its callers see: the domain's types and contracts.
    api(project(":lib:domain"))
    // The run whose writes it goes through: the app makes one and hands it in.
    api(project(":lib:core:lifecycle"))
    // How things are stored, the files, the network — its own, and nobody else's.
    implementation(project(":lib:core:model"))
    implementation(project(":lib:core:io"))
    implementation(project(":lib:network"))
    // What a sketch document is called on disk.
    implementation(project(":lib:sketch-engine"))
    // Reading and writing the sidecars (.actiondraw_board.json, .actiondraw_concept.json).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
