package io.github.subhaneetshrestha.afnopadd.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UlidTest {

    private val crockfordBase32 = Regex("[0-9A-HJKMNP-TV-Z]{26}")

    @Test
    fun `generated ids are 26 crockford base32 characters`() {
        val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(1))

        repeat(100) {
            val id = ulid.next()
            assertTrue("'$id' is not a ULID", crockfordBase32.matches(id))
        }
    }

    @Test
    fun `ids generated within one millisecond strictly increase`() {
        val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(7))

        val ids = List(1_000) { ulid.next() }

        ids.zipWithNext().forEach { (earlier, later) ->
            assertTrue("$earlier should sort before $later", earlier < later)
        }
    }

    @Test
    fun `ids generated later sort after earlier ones`() {
        var now = 1_700_000_000_000L
        val ulid = Ulid(clock = { now }, random = Random(3))

        val earlier = ulid.next()
        now += 1
        val later = ulid.next()

        assertTrue(earlier < later)
    }

    @Test
    fun `the timestamp component is recoverable`() {
        val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(1))

        assertEquals(1_700_000_000_000L, Ulid.timestampOf(ulid.next()))
    }

    @Test
    fun `element ids reject anything that is not a ulid`() {
        assertThrows(IllegalArgumentException::class.java) { ElementId("not-a-ulid") }
        // 'U' is not part of the Crockford alphabet.
        assertThrows(IllegalArgumentException::class.java) { ElementId("01JAX9Q8K0TZ3M4N5P6R7S8T9U") }
    }

    @Test
    fun `element ids accept a valid ulid`() {
        assertEquals("01JAX9Q8K0TZ3M4N5P6R7S8T9V", ElementId("01JAX9Q8K0TZ3M4N5P6R7S8T9V").value)
    }
}
