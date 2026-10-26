// The design system's contract (docs/Architecture.md, lib/design): what a screen may ask of the
// design it is drawn in — its colours, the rooms' pigments, its type, its materials (the grain,
// the lamp's shadows, the things on the table) and the chrome every room shares — read through the
// theme the way Material's `MaterialTheme` is. With it, the UI kit's basics every screen builds
// on: motion and the chrome that steps back, a chip, focus and Enter in a field. It depends on the
// domain alone, for the rooms it gives a pigment; the atelier implements it, and so may another
// design system.
plugins {
    id("actiondraw.compose")
}

dependencies {
    api(project(":lib:domain"))
}
