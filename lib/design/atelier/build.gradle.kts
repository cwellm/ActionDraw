// The atelier (docs/Architecture.md, lib/design): the default design system, CONCEPT.md's design
// language in Compose — a graphite table, paper and ink, six pigments, one lamp, its fonts. It
// implements the design contract; screens use the contract, and the app chooses this one.
plugins {
    id("actiondraw.compose")
}

dependencies {
    // What it implements, and what its users then see.
    api(project(":lib:design:contract"))
    // The paper's tooth on screen is the grain Sketch draws its pages with: one paper, not two.
    implementation(project(":lib:sketch-engine"))
}
