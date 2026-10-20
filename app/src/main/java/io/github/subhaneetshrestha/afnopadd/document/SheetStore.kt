package io.github.subhaneetshrestha.afnopadd.document

import java.io.File

/** A sheet on disk could not be read: missing, corrupt, or written by a newer version of the app. */
class SheetFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Creates and opens sheets. Each sheet is a directory holding `sheet.db` and, later, an `assets/`
 * folder and a thumbnail; the directory itself is chosen by the caller (`sheet-library` owns the
 * `files/sheets/<ulid>/` layout).
 */
interface SheetStore {

    /** Creates a new, empty sheet in [dir] (which must not already contain a database). */
    suspend fun create(dir: File, title: String): Sheet

    /** Opens the sheet in [dir]; throws [SheetFormatException] if it is unreadable or too new. */
    suspend fun open(dir: File): Sheet

    companion object {
        const val DATABASE_FILE_NAME = "sheet.db"
    }
}
