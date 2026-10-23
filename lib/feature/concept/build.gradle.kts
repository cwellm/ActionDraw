// Concepts (docs/Architecture.md, lib/feature): a thing that lives once — a character, a place —
// with its pictures, notes, links and documents, linked onto any board. The room's state and logic
// over the domain's Lib, its screens on the design contract; boards are reached through its host
// (ConceptHost), and boards read it as the domain's ConceptSource.
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
