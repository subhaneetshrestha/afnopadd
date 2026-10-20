package io.github.subhaneetshrestha.afnopadd.document

import kotlinx.coroutines.flow.StateFlow

/** Current schema version of the per-sheet database and the element envelope. */
const val SHEET_SCHEMA_VERSION = 1

data class SheetMeta(
    val id: SheetId,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int = SHEET_SCHEMA_VERSION,
)

/** Immutable view of every live element, keyed by id, with a z-ordered list derived on demand. */
class ElementSnapshot(val elements: Map<ElementId, Element>) {

    /** Elements from back to front. */
    val ordered: List<Element> by lazy { elements.values.sortedBy { it.z } }

    companion object {
        val EMPTY = ElementSnapshot(emptyMap())
    }
}

/** One element-level change inside a transaction; the undo of a diff is its [inverse]. */
sealed interface ElementDiff {
    val id: ElementId

    fun inverse(): ElementDiff

    data class Added(val after: Element) : ElementDiff {
        override val id: ElementId get() = after.id
        override fun inverse(): ElementDiff = Deleted(after)
    }

    data class Updated(val before: Element, val after: Element) : ElementDiff {
        override val id: ElementId get() = before.id
        override fun inverse(): ElementDiff = Updated(before = after, after = before)
    }

    data class Deleted(val before: Element) : ElementDiff {
        override val id: ElementId get() = before.id
        override fun inverse(): ElementDiff = Added(before)
    }
}

data class TitleChange(val before: String, val after: String)

@JvmInline
value class TransactionId(val value: String)

enum class TransactionKind { USER, UNDO, REDO }

/**
 * What one gesture changed, as applied to the sheet. The same value feeds the in-memory snapshot,
 * the database writer, and the undo history, so the three can never disagree.
 */
data class CommittedTransaction(
    val id: TransactionId,
    val kind: TransactionKind,
    /** For undo and redo transactions, the transaction they reverse. */
    val inverseOf: TransactionId?,
    val label: String,
    val committedAt: Long,
    val diffs: List<ElementDiff>,
    val titleChange: TitleChange?,
)

sealed interface TransactionResult {
    data class Committed(val transaction: CommittedTransaction) : TransactionResult
    data object NoChange : TransactionResult
}

/** The mutations available inside [Sheet.transact]; each checks its target exists (or does not) and fails fast otherwise. */
interface Transaction {
    fun add(element: Element)
    fun update(id: ElementId, change: (Element) -> Element)
    fun delete(id: ElementId)
    fun setTitle(title: String)
}

/** Receives every committed transaction, in commit order, on the committing thread. Persistence hangs off this. */
fun interface SheetWriter {
    fun write(transaction: CommittedTransaction)
}

/**
 * A sheet open for editing. All changes go through [transact], which publishes a new snapshot
 * immediately and hands the committed transaction to the writer. Undo history lives in memory
 * only and is not restored after process death.
 */
interface Sheet {
    val meta: StateFlow<SheetMeta>
    val snapshot: StateFlow<ElementSnapshot>
    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>

    /** Runs [block] atomically: if it throws, nothing is applied. Returns [TransactionResult.NoChange] when nothing changed. */
    fun transact(label: String, block: Transaction.() -> Unit): TransactionResult

    fun undo()
    fun redo()

    /** Waits until every committed transaction has reached disk. */
    suspend fun flush()

    /** Flushes, then releases the database; the sheet must not be used afterwards. */
    suspend fun close()
}
