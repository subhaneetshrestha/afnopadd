package io.github.subhaneetshrestha.afnopadd.tools.color

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Recent colours are a convenience: a settings file that cannot be read or written must never crash the editor. */
class RecentColorsStoreErrorsTest {

    private val broken = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("settings file unreadable") }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("settings file unwritable")
    }

    private val logged = mutableListOf<String>()
    private fun store() = RecentColorsStore(broken, onError = { message, _ -> logged += message })

    @Test
    fun `an unreadable settings file reads as no recent colours`() = runBlocking {
        assertEquals(emptyList<Long>(), store().colors.first())
        assertEquals(listOf("Could not read recent colours"), logged)
    }

    @Test
    fun `a failed write is dropped instead of thrown`() = runBlocking {
        store().add(0xFFFF0000)
        assertEquals(listOf("Could not save recent colour"), logged)
    }
}
