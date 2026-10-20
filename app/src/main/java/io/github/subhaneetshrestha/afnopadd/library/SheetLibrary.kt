package io.github.subhaneetshrestha.afnopadd.library

import io.github.subhaneetshrestha.afnopadd.document.SheetId
import kotlinx.coroutines.flow.Flow
import java.io.File

/** One row of the sheet library, as the launcher menu shows it. */
data class SheetSummary(
    val id: SheetId,
    val title: String,
    val dir: File,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val thumbnail: File?,
    val elementCount: Int,
)

/**
 * The index of every sheet on the device. Sheet directories under `files/sheets/` are the source
 * of truth; the index is a fast, sortable mirror that [reconcile] repairs at startup.
 */
interface SheetLibrary {

    /** All sheets, most recently opened first, then most recently changed. */
    val sheets: Flow<List<SheetSummary>>

    /** The sheet "Continue" opens, or null before any sheet has been opened. */
    val lastOpened: Flow<SheetSummary?>

    /** Creates an empty sheet; a null [title] picks the next "Sheet N". Throws for an invalid title. */
    suspend fun createSheet(title: String? = null): SheetSummary

    suspend fun markOpened(id: SheetId)

    /** Throws [IllegalArgumentException] for a blank or over-long title. */
    suspend fun rename(id: SheetId, title: String)

    suspend fun duplicate(id: SheetId): SheetSummary

    /** Moves the sheet to the trash; [restore] undoes it until the process ends or the trash is purged. */
    suspend fun delete(id: SheetId)

    suspend fun restore(id: SheetId): Boolean

    /** Re-reads title, timestamps and element count from the sheet itself, e.g. after the editor closes it. */
    suspend fun refresh(id: SheetId)

    /**
     * Runs [close] (the editor flushing, closing and thumbnailing the sheet in its directory) under
     * the library's lock, then refreshes the sheet's entry. A delete or duplicate issued meanwhile
     * waits, so it never moves or copies a sheet that is still being written.
     */
    suspend fun closeEditing(id: SheetId, close: suspend (dir: File) -> Unit)

    /** Adds a sheet directory already placed under the library root (imports). */
    suspend fun register(dir: File): SheetSummary

    /** Makes the index match the directories on disk and purges trash older than seven days. */
    suspend fun reconcile()

    companion object {
        /** Written into the sheet directory by whoever renders it; [refresh] picks it up. */
        const val THUMBNAIL_FILE_NAME = "thumb.webp"
    }
}
