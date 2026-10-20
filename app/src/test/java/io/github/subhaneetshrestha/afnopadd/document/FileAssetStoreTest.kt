package io.github.subhaneetshrestha.afnopadd.document

import io.github.subhaneetshrestha.afnopadd.document.internal.AssetRecord
import io.github.subhaneetshrestha.afnopadd.document.internal.AssetRegistry
import io.github.subhaneetshrestha.afnopadd.document.internal.FileAssetStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class FileAssetStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** In-memory stand-in for the Room-backed registry. */
    private class FakeRegistry : AssetRegistry {
        val records = linkedMapOf<AssetId, AssetRecord>()
        override suspend fun register(record: AssetRecord) {
            records[record.id] = record
        }
        override suspend fun all(): List<AssetRecord> = records.values.toList()
        override suspend fun remove(ids: Collection<AssetId>) {
            ids.forEach { records.remove(it) }
        }
    }

    private val registry = FakeRegistry()

    private fun store() = FileAssetStore(dir = folder.newFolder("assets"), registry = registry, clock = { 5_000L })

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `put stores the bytes under their sha256 and records them`() = runTest {
        val store = store()
        val bytes = "hello sticky board".toByteArray()

        val id = store.put(bytes.inputStream(), mime = "image/png", width = 10, height = 20)

        assertEquals(sha256(bytes), id.sha256Hex)
        assertTrue(store.file(id).isFile)
        assertTrue(bytes.contentEquals(store.file(id).readBytes()))
        assertEquals(
            AssetRecord(id = id, mime = "image/png", width = 10, height = 20, byteSize = bytes.size.toLong(), createdAt = 5_000L),
            registry.records.getValue(id),
        )
    }

    @Test
    fun `putting the same bytes twice yields one asset`() = runTest {
        val store = store()
        val bytes = ByteArray(10_000) { it.toByte() }

        val first = store.put(bytes.inputStream(), mime = "image/jpeg")
        val second = store.put(bytes.inputStream(), mime = "image/jpeg")

        assertEquals(first, second)
        assertEquals(1, registry.records.size)
        assertEquals(listOf(first.sha256Hex), store.file(first).parentFile!!.list()!!.toList())
    }

    @Test
    fun `a source that fails midway leaves no partial file`() = runTest {
        val store = store()
        val failing = object : InputStream() {
            private var served = 0
            override fun read(): Int {
                if (served++ > 100) throw IOException("disk unplugged")
                return 7
            }
        }

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { store.put(failing, mime = "image/png") }
        }

        assertEquals(emptyList<String>(), folder.root.walk().filter { it.isFile }.map { it.name }.toList())
        assertTrue(registry.records.isEmpty())
    }

    @Test
    fun `garbage collection removes unreferenced assets and keeps referenced ones`() = runTest {
        val store = store()
        val kept = store.put("kept".toByteArray().inputStream(), mime = "image/png")
        val dropped = store.put("dropped".toByteArray().inputStream(), mime = "image/png")

        val removed = store.collectGarbage(referenced = setOf(kept))

        assertEquals(1, removed)
        assertTrue(store.file(kept).isFile)
        assertFalse(store.file(dropped).exists())
        assertEquals(setOf(kept), registry.records.keys)
    }

    @Test
    fun `garbage collection also removes stray files that are not registered`() = runTest {
        val store = store()
        val stray = java.io.File(store.file(AssetId("f".repeat(64))).parentFile, "0".repeat(64)).apply { writeText("stray") }

        store.collectGarbage(referenced = emptySet())

        assertFalse(stray.exists())
    }
}
