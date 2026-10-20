package io.github.subhaneetshrestha.afnopadd.document.internal

import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.AssetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Assets as files named by their SHA-256 under the sheet's `assets/` directory. Bytes are streamed
 * into a temporary file while hashing and moved into place only when complete, so a failed import
 * never leaves a partial asset behind.
 */
internal class FileAssetStore(
    private val dir: File,
    private val registry: AssetRegistry,
    private val clock: () -> Long = System::currentTimeMillis,
) : AssetStore {

    override suspend fun put(source: InputStream, mime: String, width: Int, height: Int): AssetId = withContext(Dispatchers.IO) {
        check(dir.isDirectory || dir.mkdirs()) { "Could not create asset directory $dir" }
        val temp = File(dir, "$TEMP_PREFIX${UUID.randomUUID()}")
        val digest = MessageDigest.getInstance("SHA-256")
        var byteSize = 0L
        try {
            temp.outputStream().use { out ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                    byteSize += read
                }
            }
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
        val id = AssetId(digest.digest().toHex())
        val target = file(id)
        if (target.exists()) {
            temp.delete()
        } else if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Could not move asset into place at $target")
        }
        registry.register(AssetRecord(id, mime, width, height, byteSize, clock()))
        id
    }

    override fun file(id: AssetId): File = File(dir, id.sha256Hex)

    override suspend fun collectGarbage(referenced: Set<AssetId>): Int = withContext(Dispatchers.IO) {
        val unreferenced = registry.all().map { it.id }.filter { it !in referenced }
        registry.remove(unreferenced)
        val keep = referenced.map { it.sha256Hex }.toSet()
        var removed = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep && file.delete()) removed++
        }
        removed
    }

    private fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = HEX_DIGITS[value ushr 4]
            chars[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
        }
        return String(chars)
    }

    private companion object {
        const val TEMP_PREFIX = ".tmp-"
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
