// A Kotlin module that draws or decodes with Skia, without Compose: the sketch engine, the files
// layer's picture decoding. It gets the Skia binding Compose itself ships, so the app holds one
// Skia and not two, and its tests get the native runtime of the machine they run on.
plugins {
    id("actiondraw.kotlin-library")
}

dependencies {
    "api"("org.jetbrains.skiko:skiko-awt:$SKIKO_VERSION")
    "testRuntimeOnly"("org.jetbrains.skiko:skiko-awt-runtime-${skikoTarget()}:$SKIKO_VERSION")
}
