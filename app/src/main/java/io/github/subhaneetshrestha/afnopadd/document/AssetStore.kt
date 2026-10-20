package io.github.subhaneetshrestha.afnopadd.document

import java.io.File
import java.io.InputStream

/**
 * Content-addressed binary assets (imported images) belonging to one sheet. Bytes are stored once
 * per SHA-256, so the same photo placed twice costs nothing extra, and the sheet directory stays
 * self-contained for export.
 */
interface AssetStore {

    /** Streams [source] into the store and returns its id; identical bytes return the existing id. */
    suspend fun put(source: InputStream, mime: String, width: Int = 0, height: Int = 0): AssetId

    /** The file holding the asset's bytes (may not exist for an unknown id). */
    fun file(id: AssetId): File

    /** Deletes every asset not in [referenced]; returns how many files were removed. */
    suspend fun collectGarbage(referenced: Set<AssetId>): Int
}
