// The Idea Board (docs/Architecture.md, lib/feature): boards of pictures, notes and links, in groups
// or placed freely — the room's state and logic over the domain's Lib, its screens on the design
// contract. It knows no other room: a session, a concept, a sketch are asked for through its host
// (BoardHost) and the domain's ConceptSource, which the app fills.
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
