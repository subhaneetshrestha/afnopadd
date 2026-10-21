package io.github.subhaneetshrestha.afnopadd.document.internal

/**
 * Tells damage to a database file apart from temporary trouble. The bundled SQLite driver puts the
 * result code in the exception message ("Error code: 11, message: …"). The file itself is unusable
 * for SQLITE_CORRUPT (11), SQLITE_NOTADB (26) and their extended codes, and for SQLITE_ERROR (1):
 * the store only runs fixed queries against its own schema, so a plain SQL error means the file
 * lacks that schema ("no such table"). A full disk, a lock or an I/O error must never get a healthy
 * sheet treated as broken.
 */
internal object SqliteErrors {

    private val CODE = Regex("""Error code: (\d+)""")
    private val FILE_UNUSABLE = setOf(1, 11, 26) // SQLITE_ERROR, SQLITE_CORRUPT, SQLITE_NOTADB

    fun isCorruption(message: String?): Boolean {
        val code = message?.let { CODE.find(it) }?.groupValues?.get(1)?.toIntOrNull() ?: return false
        // Extended result codes carry the primary code in their low byte.
        return (code and 0xFF) in FILE_UNUSABLE
    }
}
