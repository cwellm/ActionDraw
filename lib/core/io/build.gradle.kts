// Files: where the app keeps its own, writing a file whole or not at all, moving and deleting
// folders without leaving halves behind, names Windows accepts, and reading pictures. It knows
// nothing of boards or concepts — what goes into a file is the caller's business (ROADMAP F11.2).
plugins {
    // Picture decoding is Skia's, with ImageIO behind it.
    id("actiondraw.skia")
}

dependencies {
    // AVIF decoding: an ImageIO plugin (libavif/libdav1d JNI). Neither the JDK nor the bundled
    // Skia can read AVIF; ImageDecoder falls back to ImageIO, so having it at runtime is enough.
    runtimeOnly("io.github.nemanjastokuca:avif-imageio-native-reader:0.1.0")
}
