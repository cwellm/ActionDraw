// The operating system beyond files and the network (ROADMAP F11.4): the pen's own input, the
// clipboard, the file dialogs, the browser and the system's motion setting. The only module that
// knows it runs on Windows — the pen comes in through WM_POINTER, the setting through
// SystemParametersInfo, both by JNA — and nothing here needs Compose.
plugins {
    id("actiondraw.kotlin-library")
}

dependencies {
    // Pen pressure and tilt come from native input, not from Compose (LEARNINGS L1): on Windows,
    // WM_POINTER + GetPointerPenInfo through JNA — pure Java, no compiler, no build step.
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}
