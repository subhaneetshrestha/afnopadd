package io.github.subhaneetshrestha.afnopadd.shell

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The back stack is saved across rotation and process death by serialising its keys, so every
 * destination must survive a serialisation round trip through the sealed interface.
 */
class DestinationSerializationTest {

    private val json = Json

    @Test
    fun `editor destination survives a json round trip`() {
        val original: Destination = Destination.Editor(sheetId = "01JAX9Q8K0TZ3M4N5P6R7S8T9V")

        val restored = json.decodeFromString<Destination>(json.encodeToString(original))

        assertEquals(original, restored)
    }

    @Test
    fun `library destination survives a json round trip`() {
        val original: Destination = Destination.Library

        val restored = json.decodeFromString<Destination>(json.encodeToString(original))

        assertEquals(original, restored)
    }
}
