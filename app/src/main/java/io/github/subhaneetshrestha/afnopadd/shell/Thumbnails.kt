package io.github.subhaneetshrestha.afnopadd.shell

import android.graphics.Bitmap
import android.os.Build
import io.github.subhaneetshrestha.afnopadd.canvas.RenderGeometry
import io.github.subhaneetshrestha.afnopadd.canvas.Renderer
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.library.SheetLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes `thumb.webp` (longest edge 512 px) into a sheet directory from a snapshot, but only when
 * the sheet changed since the last thumbnail. An empty sheet has no thumbnail. The file is
 * written to a temporary name first so the menu never sees a half-written image.
 */
class ThumbnailWriter(private val renderer: Renderer) {

    suspend fun update(dir: File, snapshot: ElementSnapshot, updatedAt: Long): Unit = withContext(Dispatchers.Default) {
        val target = File(dir, SheetLibrary.THUMBNAIL_FILE_NAME)
        if (target.isFile && target.lastModified() >= updatedAt) return@withContext
        val content = RenderGeometry.contentBounds(snapshot)
        if (content == null) {
            target.delete()
            return@withContext
        }
        val fit = RenderGeometry.fit(content, maxEdgePx = MAX_EDGE_PX)
        val bitmap = renderer.renderBitmap(snapshot, fit.bounds, fit.scale)
        try {
            withContext(Dispatchers.IO) {
                val temp = File(dir, "${SheetLibrary.THUMBNAIL_FILE_NAME}.tmp")
                temp.outputStream().use { bitmap.compress(webp(), QUALITY, it) }
                if (!temp.renameTo(target)) temp.delete()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private companion object {
        const val MAX_EDGE_PX = 512
        const val QUALITY = 85
    }
}
