package io.github.subhaneetshrestha.afnopadd.canvas.internal

/**
 * Bitmap sizes for images on screen: the next power of two of the image's on-screen long edge,
 * between [MIN_PX] and [MAX_PX] (the stored size cap). Powers of two keep the number of distinct
 * decodes small while pinching, and the decoder's own cache keys on the size.
 */
internal object ImageBuckets {
    const val MIN_PX = 64
    const val MAX_PX = 4_096

    fun bucketFor(screenLongEdgePx: Double): Int {
        var bucket = MIN_PX
        while (bucket < screenLongEdgePx && bucket < MAX_PX) bucket *= 2
        return bucket
    }
}
