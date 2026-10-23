package io.github.subhaneetshrestha.afnopadd.images

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.images.internal.ImageNormalizer
import io.github.subhaneetshrestha.afnopadd.images.internal.ImagePlacement
import io.github.subhaneetshrestha.afnopadd.images.internal.NormalizeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * Where imported images go: centred on [center] in the zoom frame [frameLevel], each fitted to a
 * square of [maxSizeFraction] of the viewport's shorter side ([shortSideWorld], in world units),
 * several in a row [gapWorld] apart (image-board spec decision 4).
 */
data class Placement(
    val center: WorldPoint,
    val frameLevel: Int,
    val shortSideWorld: Double,
    val gapWorld: Double,
    val maxSizeFraction: Double = 0.6,
)

enum class ImportFailure {
    /** Not an image this device can decode. */
    UNSUPPORTED,
    /** The source could not be read (gone, or access revoked). */
    UNREADABLE,
    /** The sheet could not store it (for example, the disk is full). */
    NOT_STORED,
}

sealed interface ImportResult {
    /** The source as the user picked it, as a string so results stay plain data. */
    val source: String

    data class Imported(override val source: String, val id: ElementId) : ImportResult
    data class Failed(override val source: String, val reason: ImportFailure) : ImportResult
}

/** One result per source, in the order given. */
data class ImportSummary(val results: List<ImportResult>) {
    val imported: List<ImportResult.Imported> get() = results.filterIsInstance<ImportResult.Imported>()
    val failed: List<ImportResult.Failed> get() = results.filterIsInstance<ImportResult.Failed>()
}

interface ImageImporter {
    /**
     * Copies each source into [sheet]'s assets (normalised: upright, at most 4,096 px) and adds the
     * images that worked as one undoable transaction, placed by [placement]. A source that fails is
     * reported in the summary and the rest carry on. [onProgress] gets (done, total) after each
     * source, on a background thread.
     */
    suspend fun import(sources: List<Uri>, sheet: Sheet, placement: Placement, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportSummary
}

/**
 * [ImageImporter] on the platform decoders. Sources are handled one at a time on the IO dispatcher,
 * so at most one decoded bitmap (bounded by the 4,096 px cap) is in memory (image-board spec
 * decision 7). Picker URI grants are only used while importing, never kept.
 */
class AndroidImageImporter(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val ulid: Ulid = Ulid.Default,
    /** Sources larger than this are refused before they fill the cache (a file picker can hand over anything). */
    maxSourceBytes: Long = ImageNormalizer.MAX_SOURCE_BYTES,
) : ImageImporter {

    private val normalizer = ImageNormalizer(context.contentResolver, File(context.cacheDir, "import"), maxSourceBytes)

    override suspend fun import(sources: List<Uri>, sheet: Sheet, placement: Placement, onProgress: (Int, Int) -> Unit): ImportSummary {
        val stored = mutableListOf<Pair<Int, Outcome.Stored>>()
        val outcomes = arrayOfNulls<ImportResult>(sources.size)
        withContext(Dispatchers.IO) {
            sources.forEachIndexed { i, uri ->
                // A cancelled import stops between sources and adds nothing; what it stored is
                // unreferenced and collected when the sheet closes.
                ensureActive()
                when (val outcome = store(uri, sheet)) {
                    is Outcome.Stored -> stored += i to outcome
                    is Outcome.Failed -> outcomes[i] = ImportResult.Failed(uri.toString(), outcome.reason)
                }
                onProgress(i + 1, sources.size)
            }
        }
        if (stored.isNotEmpty()) {
            val rects = ImagePlacement.layout(stored.map { (_, s) -> ImagePlacement.PixelSize(s.width, s.height) }, placement)
            val now = clock()
            var top = sheet.snapshot.value.ordered.lastOrNull()?.z
            val elements = stored.mapIndexed { n, (i, s) ->
                val rect = rects[n]
                ImageElement(
                    id = ElementId(ulid.next()),
                    anchor = WorldPoint(rect.left, rect.top),
                    frameLevel = placement.frameLevel,
                    z = FractionalIndex.between(top, null).also { top = it },
                    bounds = rect,
                    version = 1,
                    versionNonce = random.nextInt(),
                    createdAt = now,
                    updatedAt = now,
                    asset = s.asset,
                    size = ImagePlacement.localSize(rect, placement.frameLevel),
                    rotationDeg = 0f,
                    crop = null,
                ).also { outcomes[i] = ImportResult.Imported(sources[i].toString(), it.id) }
            }
            sheet.transact(label = if (elements.size == 1) "Import image" else "Import images") { elements.forEach { add(it) } }
        }
        return ImportSummary(outcomes.map { checkNotNull(it) })
    }

    private sealed interface Outcome {
        class Stored(val asset: AssetId, val width: Int, val height: Int) : Outcome
        class Failed(val reason: ImportFailure) : Outcome
    }

    /** Normalises [uri] and puts it into the sheet's assets; returns what was stored, or why not. */
    private suspend fun store(uri: Uri, sheet: Sheet): Outcome = try {
        normalizer.normalize(uri).use { image ->
            try {
                val asset = image.file.inputStream().use { sheet.assets.put(it, image.mime, image.width, image.height) }
                Outcome.Stored(asset, image.width, image.height)
            } catch (e: IOException) {
                Log.w(TAG, "Could not store $uri", e)
                Outcome.Failed(ImportFailure.NOT_STORED)
            }
        }
    } catch (e: NormalizeException) {
        Log.w(TAG, "Could not import $uri: ${e.reason}", e)
        Outcome.Failed(e.reason)
    }

    private companion object {
        const val TAG = "ImageImporter"
    }
}
