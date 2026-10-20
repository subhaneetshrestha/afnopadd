package io.github.subhaneetshrestha.afnopadd.document

import java.io.File

/** A sheet on disk could not be read: missing, corrupt, or written by a newer version of the app. */
class SheetFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** What can be learned about a sheet without loading its elements. */
data class SheetInfo(val meta: SheetMeta, val elementCount: Int)

/**
 * Creates and opens sheets. Each sheet is a directory holding `sheet.db` and, later, an `assets/`
 * folder and a thumbnail; the directory itself is chosen by the caller (`sheet-library` owns the
 * `files/sheets/<ulid>/` layout).
 */
interface SheetStore {

    /** Creates a new, empty sheet in [dir] (which must not already contain a database); [id] defaults to a fresh ULID. */
    suspend fun create(dir: File, title: String, id: SheetId? = null): Sheet

    /** Opens the sheet in [dir]; throws [SheetFormatException] if it is unreadable or too new. */
    suspend fun open(dir: File): Sheet

    /** Reads metadata and the live element count without decoding elements. */
    suspend fun inspect(dir: File): SheetInfo

    /**
     * Copies the closed sheet in [source] to [target] (which must not exist) under a new [id] and
     * [title]; element ids are kept, timestamps restart. Returns the copy's metadata.
     */
    suspend fun duplicate(source: File, target: File, id: SheetId, title: String): SheetMeta

    companion object {
        const val DATABASE_FILE_NAME = "sheet.db"
    }
}
