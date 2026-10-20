package io.github.subhaneetshrestha.afnopadd

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the kotlinx.serialization plugin and JSON runtime are wired into the build, and pins the
 * forward-compatibility behaviour every Afnopadd file format relies on: unknown keys are ignored.
 */
class SerializationSmokeTest {

    @Serializable
    data class Probe(val id: Int, val name: String)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decoding ignores unknown keys`() {
        val decoded = json.decodeFromString<Probe>("""{"id":1,"name":"a","extra":true}""")

        assertEquals(Probe(id = 1, name = "a"), decoded)
    }

    @Test
    fun `encoding produces compact json`() {
        val encoded = json.encodeToString(Probe(id = 1, name = "a"))

        assertEquals("""{"id":1,"name":"a"}""", encoded)
    }
}
