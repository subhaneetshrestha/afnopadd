package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.canvas.internal.ImageBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageBucketsTest {

    @Test
    fun `a bitmap is requested at the next power of two of its on-screen size`() {
        assertEquals(512, ImageBuckets.bucketFor(300.0))
        assertEquals(512, ImageBuckets.bucketFor(512.0))
        assertEquals(1_024, ImageBuckets.bucketFor(513.0))
    }

    @Test
    fun `buckets stop at a small floor and at the stored size cap`() {
        assertEquals(ImageBuckets.MIN_PX, ImageBuckets.bucketFor(3.0))
        assertEquals(ImageBuckets.MIN_PX, ImageBuckets.bucketFor(0.0))
        assertEquals(ImageBuckets.MAX_PX, ImageBuckets.bucketFor(50_000.0))
    }

    @Test
    fun `zooming out never asks for a bigger bitmap`() {
        val sizes = generateSequence(8_000.0) { it / 1.3 }.takeWhile { it > 1.0 }.map(ImageBuckets::bucketFor).toList()
        assertTrue(sizes.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(sizes.first() > sizes.last())
    }
}
