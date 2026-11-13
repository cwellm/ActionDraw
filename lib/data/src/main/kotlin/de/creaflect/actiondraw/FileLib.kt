package de.creaflect.actiondraw

import de.creaflect.actiondraw.board.BoardRegistry
import de.creaflect.actiondraw.board.Boards
import de.creaflect.actiondraw.board.FileBoards
import de.creaflect.actiondraw.board.LinkPreview
import de.creaflect.actiondraw.board.LinkPreviews
import de.creaflect.actiondraw.collage.Collages
import de.creaflect.actiondraw.collage.FileCollages
import de.creaflect.actiondraw.concept.ConceptRegistry
import de.creaflect.actiondraw.concept.Concepts
import de.creaflect.actiondraw.concept.FileConcepts
import de.creaflect.actiondraw.image.FilePictures
import de.creaflect.actiondraw.image.FilePracticeHistory
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.image.PracticeHistory
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.io.AppDirs
import de.creaflect.actiondraw.lifecycle.LibRun
import de.creaflect.actiondraw.network.Fetcher
import de.creaflect.actiondraw.network.Http
import de.creaflect.actiondraw.sketch.FileSketches
import de.creaflect.actiondraw.sketch.Sketches
import java.io.File

/**
 * The [Lib] over the files (docs/Architecture.md, lib/data). A board is a folder with its board
 * file beside the pictures, a concept the same; the settings and the registries of which boards
 * and concepts there are live in `~/.actiondraw`. Nothing is kept anywhere else, so the files
 * stay the truth — a board copied in Explorer is a board.
 *
 * What is written often — a board's file after every edit — goes through [run]'s writes, off the
 * caller's thread, the newest of a folder winning; whatever reads a folder back waits for its
 * write first, and closing the run waits for them all. The inline run, the default, writes at
 * once on the caller's thread: what a test wants.
 */
class FileLib private constructor(dirs: AppDirs, run: LibRun, fetcher: Fetcher) : Lib {
    /** The lib over the files of the person running the app. */
    constructor(run: LibRun = LibRun.inline()) : this(AppDirs.current, run, Http)

    /**
     * The lib over [home] as though it were the person's home — the settings, the registries,
     * the thumbnails and the default homes of boards, concepts, sketches and collages all inside it: a
     * test's temp folder.
     */
    constructor(home: File, run: LibRun = LibRun.inline()) : this(AppDirs(home), run, Http)

    /** [FileLib] over [home] going online through [fetcher]: a test's stub, so no socket is ever opened. */
    internal constructor(home: File, fetcher: Fetcher) : this(AppDirs(home), LibRun.inline(), fetcher)

    private val settings = Settings(dirs)

    override val preferences: Preferences = settings
    override val boards: Boards = FileBoards(settings, BoardRegistry(dirs.config), run.writes, dirs)
    override val concepts: Concepts = FileConcepts(settings, ConceptRegistry(dirs.config), run.writes, dirs)
    override val pictures: Pictures = FilePictures(ThumbCache(dirs.thumbnails))
    override val practice: PracticeHistory = FilePracticeHistory
    override val sketches: Sketches = FileSketches(settings)
    override val collages: Collages = FileCollages(settings)
    override val previews: LinkPreviews = LinkPreviews { dir, url, title -> LinkPreview.fetch(dir, url, title, fetcher) }
    override val diagnostics: Diagnostics = FileDiagnostics(dirs)
}
