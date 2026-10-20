package io.github.subhaneetshrestha.afnopadd.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FractionalIndexTest {

    @Test
    fun `between with no neighbours yields the first key`() {
        assertEquals(FractionalIndex.first(), FractionalIndex.between(null, null))
    }

    @Test
    fun `between with no upper bound is greater than the lower bound`() {
        val a = FractionalIndex.first()

        val next = FractionalIndex.between(a, null)

        assertTrue(a < next)
    }

    @Test
    fun `between with no lower bound is smaller than the upper bound`() {
        val b = FractionalIndex.first()

        val previous = FractionalIndex.between(null, b)

        assertTrue(previous < b)
    }

    @Test
    fun `between orders strictly between its neighbours`() {
        val a = FractionalIndex.first()
        val b = FractionalIndex.between(a, null)

        val mid = FractionalIndex.between(a, b)

        assertTrue(a < mid)
        assertTrue(mid < b)
    }

    @Test
    fun `between rejects reversed neighbours`() {
        val a = FractionalIndex.first()
        val b = FractionalIndex.between(a, null)

        assertThrows(IllegalArgumentException::class.java) { FractionalIndex.between(b, a) }
        assertThrows(IllegalArgumentException::class.java) { FractionalIndex.between(a, a) }
    }

    @Test
    fun `keys compare the same way as their strings`() {
        val keys = listOf("a0", "a1", "Zz", "b00").map(::FractionalIndex)

        assertEquals(keys.map { it.key }.sorted(), keys.sorted().map { it.key })
    }

    @Test
    fun `appending ten thousand keys keeps them short`() {
        var last = FractionalIndex.first()

        repeat(10_000) { last = FractionalIndex.between(last, null) }

        assertTrue("key '${last.key}' grew too long", last.key.length <= 8)
    }

    @Test
    fun `prepending ten thousand keys keeps them short`() {
        var first = FractionalIndex.first()

        repeat(10_000) { first = FractionalIndex.between(null, first) }

        assertTrue("key '${first.key}' grew too long", first.key.length <= 8)
    }

    @Test
    fun `ten thousand random inserts never collide and stay sorted`() {
        val random = Random(42)
        val keys = mutableListOf(FractionalIndex.first())

        repeat(10_000) {
            val position = random.nextInt(keys.size + 1)
            val before = keys.getOrNull(position - 1)
            val after = keys.getOrNull(position)
            keys.add(position, FractionalIndex.between(before, after))
        }

        assertEquals(keys.size, keys.toSet().size)
        assertEquals(keys.sorted(), keys)
    }
}
