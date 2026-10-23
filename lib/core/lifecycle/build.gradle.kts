// The lib's run (docs/Architecture.md, lib/core): loading it, the scope and the dispatcher its
// work runs in, the writes it must not lose, the memory it holds while running, and closing down
// cleanly. It depends on nothing of ours — only on coroutines.
plugins {
    id("actiondraw.kotlin-library")
}

dependencies {
    // The version Compose 1.7.3 brings along, so the app holds one coroutines library, not two.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}
