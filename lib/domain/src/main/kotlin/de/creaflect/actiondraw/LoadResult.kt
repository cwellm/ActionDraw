package de.creaflect.actiondraw

/** What opening a board's or a concept's folder found. */
sealed interface LoadResult<out T> {
    /** Nothing to open: the folder is not one (yet). */
    data object None : LoadResult<Nothing>

    /**
     * [value] as the folder holds it. [fromBackup] when its file could not be read and the
     * backup beside it was used instead — worth telling the person.
     */
    data class Loaded<T>(val value: T, val fromBackup: Boolean = false) : LoadResult<T>

    /** There is a file, but neither it nor its backup could be read. */
    data object Failed : LoadResult<Nothing>
}
