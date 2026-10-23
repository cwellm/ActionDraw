// The lens kit (docs/Architecture.md, lib/lens): how a picture is seen — the lenses, the knobs over
// them, the proportion grids and the glass — with the tray that holds them, the picture they work
// on and the keys that work them, all on the design contract. The rooms that show a picture share
// it (the Practice room's session, the Lens room), so it is part of the UI kit but no room of its
// own: it knows none of them, and every one of them may use it.
plugins {
    id("actiondraw.compose")
}

dependencies {
    api(project(":lib:domain"))
    api(project(":lib:design:contract"))
    // Its tests draw in the default design.
    testImplementation(project(":lib:design:atelier"))
}
