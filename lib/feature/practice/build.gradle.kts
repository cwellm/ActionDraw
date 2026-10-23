// The Practice room (docs/Architecture.md, lib/feature): timed poses and memory drawing — the
// room's state and logic over the domain's Lib, its screens on the design contract. It knows no
// other room: where a board session's pictures come from and where a picture is pinned are the
// room's own contracts (PracticeBoards, PinTargets), which the app fills.
plugins {
    id("actiondraw.compose")
}

dependencies {
    api(project(":lib:domain"))
    api(project(":lib:design:contract"))
    // Its tests stand the lib up over a temp folder and draw in the default design.
    testImplementation(project(":lib:data"))
    testImplementation(project(":lib:design:atelier"))
}
