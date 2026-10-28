// The Collage room (docs/Architecture.md, lib/feature): pictures cut apart and laid on a cutting
// mat, to test whether a scene holds — the room's state over the domain's Lib, its screens on the
// design contract. It knows no other room: pictures come to it through the hand-off seam, and the
// way out is its host's (CollageHost), which the app fills.
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
