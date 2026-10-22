package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.LoadResult
import java.io.File

/**
 * The boards: which there are, where each lives, and what each holds. A board is a folder — its
 * pictures, and the board file beside them — so a board is named by its folder here, and the
 * folder stays the truth: nothing is kept anywhere else that could disagree with it.
 */
interface Boards {
    // ---- One board ----

    /** Whether [dir] is a board: whether it holds a board file. */
    fun isBoard(dir: File): Boolean

    /** The board in [dir] as its file holds it, not checked against the folder: for lists. */
    fun peek(dir: File): Board?

    /**
     * Opens the board in [dir], checked against what the folder holds now: a card whose picture
     * was renamed or moved outside the app follows it, one whose picture is gone goes.
     */
    fun load(dir: File): LoadResult<Board>

    /**
     * Keeps [board] as the one in [dir]. It is written off the caller's thread, and a newer
     * board for the same folder handed over before it was written replaces it — a burst of
     * edits costs one write. Whatever reads the board back sees the newest.
     */
    fun save(dir: File, board: Board)

    /** [save], and waits for it: whether the board file was written. */
    fun saveNow(dir: File, board: Board): Boolean

    // ---- Which boards there are ----

    /** What is recorded for the board in [dir], if it is recorded. */
    fun entryFor(dir: File): BoardEntry?

    /** Records [name] for the board in [dir] — its folder keeps the name it has. */
    fun rename(dir: File, name: String)

    /** The board opened last, if it is still a board. */
    fun last(): BoardRef?

    /**
     * Every board: those recorded, wherever they live, and any board folder found in the boards
     * home or among the recent ones that is not recorded yet — recorded from now on.
     */
    fun available(): List<BoardRef>

    /**
     * Every board, depth first, so a sub-board follows the board it sits in. One whose parent
     * is recorded but no longer readable counts as a root rather than vanishing with it.
     */
    fun tree(): List<BoardNode>

    /** The board [dir] sits inside, if it is a sub-board of one that is still a board. */
    fun parentOf(dir: File): BoardEntry?

    /** The boards directly inside [dir], by name. */
    fun childrenOf(dir: File): List<BoardEntry>

    /** How many boards would go with [dir] if its folder were deleted. */
    fun countInside(dir: File): Int

    /** Notes that [dir] was opened: first among the recent boards, and recorded if it was not. */
    fun opened(dir: File)

    // ---- Making, deleting, moving ----

    /**
     * Makes a board of [board] in [parent], in a folder named after it. A board of that name
     * there already is not made twice; a leftover folder of that name keeps its name, and the
     * new board gets a folder beside it.
     */
    fun create(parent: File, board: Board): BoardCreation

    /** Deletes the board in [dir], as far as [how] says; any board inside a deleted folder goes too. */
    fun delete(dir: File, how: Deletion): BoardDeletion

    /**
     * Moves the board in [dir] into [into] — out to the boards home when that is null — with
     * its pictures and the boards inside it. Nesting is where a folder sits, so this is how the
     * tree of boards is rearranged.
     */
    fun move(dir: File, into: File?): BoardMove

    // ---- The board's own copies ----

    /**
     * Copies [picture] into the board in [dir] as its wallpaper, so the board stays whole when
     * it moves. Returns the copy's path in the board, or null when it could not be copied.
     */
    fun copyWallpaper(dir: File, picture: File): String?

    /** Deletes the wallpaper copy at [path] in the board in [dir]. */
    fun deleteWallpaper(dir: File, path: String)
}

/** A board by its name and its folder: what a list of boards shows. */
data class BoardRef(val name: String, val dir: File)

/** One board in the tree of boards, with how deep it sits and whose child it is. */
data class BoardNode(val name: String, val dir: File, val depth: Int, val parent: String?)

/** How far deleting a board goes. The board file is ActionDraw's; the pictures are the person's. */
enum class Deletion {
    /** Only the board file goes: the folder and every picture in it stay, no longer a board. */
    FORGET,

    /** The folder goes, and everything in it. Never the default. */
    DELETE_FOLDER,
}

/** How making a board went. */
sealed interface BoardCreation {
    /** Made, in [dir]. */
    data class Created(val dir: File) : BoardCreation

    /** There is such a board already, in [dir]: that one is meant. */
    data class Existing(val dir: File) : BoardCreation

    /** [dir] could not be made. */
    data class CouldNotCreate(val dir: File) : BoardCreation

    /** [dir] is there, but the board file could not be written into it. */
    data class CouldNotWrite(val dir: File) : BoardCreation
}

/** How deleting a board went. */
enum class BoardDeletion {
    /** The folder was gone already. */
    GONE_ALREADY,

    /** Refused: not a board folder, or one nobody means to erase (a drive root, the home). */
    REFUSED,

    /** The board file went; the folder and the pictures are still there. */
    FORGOTTEN,

    /** The board file could not be removed. */
    NOT_FORGOTTEN,

    /** The folder went, with everything in it. */
    DELETED,

    /** The folder could not be deleted: something in it is in use. */
    NOT_DELETED,
}

/** How moving a board went. */
sealed interface BoardMove {
    /** Moved, to [to]. [oldLeft] when the old folder could not be cleared away. */
    data class Moved(val to: File, val oldLeft: Boolean) : BoardMove

    /** The folder is not a board. */
    data object NotABoard : BoardMove

    /** The place to move it to is the board itself. */
    data object IntoItself : BoardMove

    /** The place to move it to lies inside it. */
    data object IntoItsOwnSubBoard : BoardMove

    /** The place to move it to could not be opened. */
    data class CannotOpen(val target: File) : BoardMove

    /** It is there already. */
    data object AlreadyThere : BoardMove

    /** The move itself was refused: the destination turned out to be inside the folder. */
    data object RefusedIntoItself : BoardMove

    /** It could not be moved; nothing happened to it. */
    data object Failed : BoardMove
}

/**
 * What bringing pictures into a folder produced: the new cards, and whatever was left out — so
 * the person hears why a dropped file did not appear instead of it vanishing.
 */
data class ImportOutcome(
    val items: List<ImageItem> = emptyList(),
    /** Files of a format this build cannot read. */
    val unsupported: List<File> = emptyList(),
    /** Pictures that were there already. */
    val duplicates: Int = 0,
)
