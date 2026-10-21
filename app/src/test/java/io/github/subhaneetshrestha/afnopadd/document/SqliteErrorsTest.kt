package io.github.subhaneetshrestha.afnopadd.document

import io.github.subhaneetshrestha.afnopadd.document.internal.SqliteErrors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only damage to the file itself makes a sheet unreadable; a full disk or a busy lock is temporary. */
class SqliteErrorsTest {

    @Test
    fun `corrupt and not-a-database errors are format errors`() {
        assertTrue(SqliteErrors.isCorruption("Error code: 11, message: database disk image is malformed"))
        assertTrue(SqliteErrors.isCorruption("Error code: 26, message: file is not a database"))
        // Extended codes keep the primary code in their low byte (779 = SQLITE_CORRUPT_INDEX).
        assertTrue(SqliteErrors.isCorruption("Error code: 779, message: corrupt index"))
        // The store's own fixed queries only fail this way when the file lacks the sheet schema.
        assertTrue(SqliteErrors.isCorruption("Error code: 1, message: no such table: meta"))
    }

    @Test
    fun `transient errors are not format errors`() {
        assertFalse(SqliteErrors.isCorruption("Error code: 13, message: database or disk is full"))
        assertFalse(SqliteErrors.isCorruption("Error code: 5, message: database is locked"))
        assertFalse(SqliteErrors.isCorruption("Error code: 10, message: disk I/O error"))
        assertFalse(SqliteErrors.isCorruption("something without a code"))
        assertFalse(SqliteErrors.isCorruption(null))
    }
}
