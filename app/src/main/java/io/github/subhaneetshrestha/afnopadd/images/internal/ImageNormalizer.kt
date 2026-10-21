package io.github.subhaneetshrestha.afnopadd.images.internal

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import io.github.subhaneetshrestha.afnopadd.images.ImportFailure
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Why a source could not be normalised. */
internal class NormalizeException(val reason: ImportFailure, message: String, cause: Throwable? = null) : Exception(message, cause)

/** A normalised image in a temporary [file]; closing it deletes the file. */
internal class NormalizedImage(val file: File, val mime: String, val width: Int, val height: Int) : Closeable {
    override fun close() {
        file.delete()
    }
}

/**
 * Turns a picked source into an image fit to store (SPEC-image-board decision 3): upright, at most
 * [MAX_EDGE_PX] on its long edge, in a format every Android device decodes.
 *
 * The source is first copied to a private temporary file, since picker streams may only be read
 * once and the grant is not kept. Its header is read without decoding pixels; an image that is
 * already upright, within the cap and a JPEG, PNG or WebP is stored byte for byte. Anything else
 * is decoded once by [ImageDecoder], which applies the EXIF orientation and scales straight to the
 * cap, and re-encoded: JPEG at quality 90 when opaque, lossless (WebP where the platform can write
 * it, PNG on API 29) when it has alpha.
 */
internal class ImageNormalizer(private val resolver: ContentResolver, private val workDir: File) {

    fun normalize(uri: Uri): NormalizedImage {
        val source = copyToWorkDir(uri)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            val mime = bounds.outMimeType
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || mime == null) {
                throw NormalizeException(ImportFailure.UNSUPPORTED, "Not an image this device can decode")
            }
            val turned = orientationNeedsBaking(source)
            if (!turned && max(bounds.outWidth, bounds.outHeight) <= MAX_EDGE_PX && mime in STORED_AS_IS) {
                return NormalizedImage(source, mime, bounds.outWidth, bounds.outHeight)
            }
            return reencode(source).also { source.delete() }
        } catch (e: NormalizeException) {
            source.delete()
            throw e
        } catch (e: IOException) {
            source.delete()
            throw NormalizeException(ImportFailure.UNSUPPORTED, "Could not decode the image", e)
        } catch (e: RuntimeException) {
            // Decoders throw unchecked exceptions for corrupt data.
            source.delete()
            throw NormalizeException(ImportFailure.UNSUPPORTED, "Could not decode the image", e)
        }
    }

    private fun copyToWorkDir(uri: Uri): File {
        check(workDir.isDirectory || workDir.mkdirs()) { "Could not create $workDir" }
        val file = File(workDir, "import-${UUID.randomUUID()}")
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            return file
        } catch (e: IOException) {
            file.delete()
            throw NormalizeException(ImportFailure.UNREADABLE, "Could not read the source", e)
        } catch (e: SecurityException) {
            file.delete()
            throw NormalizeException(ImportFailure.UNREADABLE, "Access to the source was revoked", e)
        }
    }

    /** Whether the file's EXIF says it must be turned or mirrored to be viewed upright. */
    private fun orientationNeedsBaking(file: File): Boolean = try {
        val orientation = ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED
    } catch (e: IOException) {
        false
    }

    private fun reencode(source: File): NormalizedImage {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            // Software pixels so they can be compressed; scaled uniformly so a turned image keeps its shape.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > MAX_EDGE_PX) {
                val scale = MAX_EDGE_PX.toDouble() / longEdge
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            }
        }
        try {
            val (format, mime) = when {
                !bitmap.hasAlpha() -> Bitmap.CompressFormat.JPEG to "image/jpeg"
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSLESS to "image/webp"
                else -> Bitmap.CompressFormat.PNG to "image/png"
            }
            val out = File(workDir, "normalized-${UUID.randomUUID()}")
            out.outputStream().use { if (!bitmap.compress(format, JPEG_QUALITY, it)) throw IOException("Could not encode the image") }
            return NormalizedImage(out, mime, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        /** The stored long edge never exceeds this (owner decision 2 in SPEC-image-board). */
        const val MAX_EDGE_PX = 4_096
        private const val JPEG_QUALITY = 90
        private val STORED_AS_IS = setOf("image/jpeg", "image/png", "image/webp")
    }
}
