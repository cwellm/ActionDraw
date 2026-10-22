// The API (docs/Architecture.md, lib/domain): the types callers work with — boards and their
// cards, concepts, the session's vocabulary — abstracted from how they are stored, and the
// contracts the lib needs from its host (clipboard, file dialogs, the browser, the pen). It
// depends on nothing of ours; everything else implements it, uses it or extends it.
plugins {
    id("actiondraw.kotlin-library")
}
