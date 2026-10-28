// The Lens room (docs/Architecture.md, lib/feature): any picture seen differently, with no clock
// running — the room's state over the domain's Lib, its screens on the design contract, the
// picture seen through the lens kit. It knows no other room: the boards it pins to and the way out
// are its host's (LensHost), which the app fills.
plugins {
    id("actiondraw.compose")
}

dependencies {
    api(project(":lib:domain"))
    api(project(":lib:design:contract"))
    api(project(":lib:lens"))
    // Its tests stand the lib up over a temp folder and draw in the default design.
    testImplementation(project(":lib:data"))
    testImplementation(project(":lib:design:atelier"))
}
