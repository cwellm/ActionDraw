// The API (docs/Architecture.md, lib/domain): the types callers work with — boards and their
// cards, concepts, the session's vocabulary — abstracted from how they are stored; the contracts
// for getting and keeping them, behind `Lib`, which lib:data implements; and the contracts the lib
// needs from its host (clipboard, file dialogs, the browser, the pen). It depends on nothing of
// ours; everything else implements it, uses it or extends it.
plugins {
    // A picture comes as a Skia image: what Compose Desktop and the sketch engine draw with.
    id("actiondraw.skia")
}
