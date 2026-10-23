package io.github.subhaneetshrestha.afnopadd.canvas

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import java.io.File

/**
 * Loads the bitmaps the canvas draws images with. The canvas asks for a size per zoom bucket and
 * keeps drawing what it has until the new size arrives, so loading never blocks a frame.
 */
fun interface ImageBitmapLoader {
    /**
     * Starts loading [file] fitted within [maxEdgePx] on its long edge; [onLoaded] or [onFailed]
     * runs on the main thread. Returns a function that cancels the load if it is still running.
     */
    fun load(file: File, maxEdgePx: Int, onLoaded: (Bitmap) -> Unit, onFailed: () -> Unit): () -> Unit
}

/**
 * [ImageBitmapLoader] through Coil 3 (image-board spec decision 6): decoded off the main thread
 * into hardware bitmaps, with Coil's memory cache keyed on file and size, so zooming back to a
 * bucket seen before is instant.
 */
class CoilImageBitmapLoader(private val context: Context) : ImageBitmapLoader {
    override fun load(file: File, maxEdgePx: Int, onLoaded: (Bitmap) -> Unit, onFailed: () -> Unit): () -> Unit {
        val request = ImageRequest.Builder(context)
            .data(file)
            .size(maxEdgePx, maxEdgePx)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .target(onSuccess = { image -> onLoaded(image.toBitmap()) }, onError = { onFailed() })
            .build()
        val disposable = SingletonImageLoader.get(context).enqueue(request)
        return { disposable.dispose() }
    }
}
