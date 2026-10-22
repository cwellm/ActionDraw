// The pen's own input (docs/Architecture.md, device/): Windows Ink through JNA. Compose delivers
// no pressure or tilt (LEARNINGS L1), so the window's procedures are hooked and every WM_POINTER
// message read. It depends on nothing of ours; the app wires it in.
plugins {
    id("actiondraw.kotlin-library")
}

dependencies {
    // WM_POINTER + GetPointerPenInfo through JNA — pure Java, no compiler, no build step.
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}
