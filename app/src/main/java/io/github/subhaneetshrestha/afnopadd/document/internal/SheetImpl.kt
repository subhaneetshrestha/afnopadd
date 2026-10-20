package io.github.subhaneetshrestha.afnopadd.document.internal

import io.github.subhaneetshrestha.afnopadd.document.CommittedTransaction
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementDiff
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.SheetWriter
import io.github.subhaneetshrestha.afnopadd.document.TitleChange
import io.github.subhaneetshrestha.afnopadd.document.Transaction
import io.github.subhaneetshrestha.afnopadd.document.TransactionId
import io.github.subhaneetshrestha.afnopadd.document.TransactionKind
import io.github.subhaneetshrestha.afnopadd.document.TransactionResult
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory sheet state with transaction, undo and redo semantics. Persistence is delegated to the
 * [SheetWriter], which receives each committed transaction synchronously in commit order.
 */
internal class SheetImpl(
    meta: SheetMeta,
    initialElements: List<Element>,
    private val writer: SheetWriter,
    private val ulid: Ulid = Ulid.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
) : Sheet {

    private val lock = Any()
    private val _meta = MutableStateFlow(meta)
    private val _snapshot = MutableStateFlow(ElementSnapshot(initialElements.associateBy { it.id }))
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    private val undoStack = ArrayDeque<CommittedTransaction>()
    private val redoStack = ArrayDeque<CommittedTransaction>()

    override val meta: StateFlow<SheetMeta> = _meta.asStateFlow()
    override val snapshot: StateFlow<ElementSnapshot> = _snapshot.asStateFlow()
    override val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    override val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    override fun transact(label: String, block: Transaction.() -> Unit): TransactionResult = synchronized(lock) {
        val working = WorkingTransaction(_snapshot.value, _meta.value.title)
        working.block()
        val diffs = working.diffs()
        val titleChange = working.titleChange()
        if (diffs.isEmpty() && titleChange == null) return TransactionResult.NoChange

        val committed = CommittedTransaction(
            id = TransactionId(ulid.next()),
            kind = TransactionKind.USER,
            inverseOf = null,
            label = label,
            committedAt = clock(),
            diffs = diffs,
            titleChange = titleChange,
        )
        apply(committed)
        undoStack.addLast(committed)
        if (undoStack.size > historyLimit) undoStack.removeFirst()
        redoStack.clear()
        publishFlags()
        TransactionResult.Committed(committed)
    }

    override fun undo(): Unit = synchronized(lock) {
        val original = undoStack.removeLastOrNull() ?: return
        val inverse = original.inverse(kind = TransactionKind.UNDO)
        apply(inverse)
        redoStack.addLast(inverse)
        publishFlags()
    }

    override fun redo(): Unit = synchronized(lock) {
        val undone = redoStack.removeLastOrNull() ?: return
        val inverse = undone.inverse(kind = TransactionKind.REDO)
        apply(inverse)
        undoStack.addLast(inverse)
        if (undoStack.size > historyLimit) undoStack.removeFirst()
        publishFlags()
    }

    private fun CommittedTransaction.inverse(kind: TransactionKind) = CommittedTransaction(
        id = TransactionId(ulid.next()),
        kind = kind,
        inverseOf = id,
        label = label,
        committedAt = clock(),
        diffs = diffs.asReversed().map { it.inverse() },
        titleChange = titleChange?.let { TitleChange(before = it.after, after = it.before) },
    )

    private fun apply(transaction: CommittedTransaction) {
        val elements = _snapshot.value.elements.toMutableMap()
        for (diff in transaction.diffs) {
            when (diff) {
                is ElementDiff.Added -> elements[diff.id] = diff.after
                is ElementDiff.Updated -> elements[diff.id] = diff.after
                is ElementDiff.Deleted -> elements.remove(diff.id)
            }
        }
        _snapshot.value = ElementSnapshot(elements)
        _meta.value = _meta.value.copy(
            title = transaction.titleChange?.after ?: _meta.value.title,
            updatedAt = transaction.committedAt,
        )
        writer.write(transaction)
    }

    private fun publishFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /** Collects a transaction's net effect per element without touching the published snapshot. */
    private class WorkingTransaction(
        private val base: ElementSnapshot,
        private val baseTitle: String,
    ) : Transaction {

        private val changes = LinkedHashMap<ElementId, ElementDiff>()
        private var title = baseTitle

        private fun effective(id: ElementId): Element? = when (val change = changes[id]) {
            null -> base.elements[id]
            is ElementDiff.Added -> change.after
            is ElementDiff.Updated -> change.after
            is ElementDiff.Deleted -> null
        }

        override fun add(element: Element) {
            require(effective(element.id) == null) { "Element ${element.id.value} already exists" }
            changes[element.id] = when (val prior = changes[element.id]) {
                is ElementDiff.Deleted -> ElementDiff.Updated(before = prior.before, after = element)
                else -> ElementDiff.Added(element)
            }
        }

        override fun update(id: ElementId, change: (Element) -> Element) {
            val before = effective(id) ?: throw IllegalArgumentException("Element ${id.value} does not exist")
            val after = change(before)
            require(after.id == id) { "An update may not change the element id" }
            if (after == before) return
            when (val prior = changes[id]) {
                null -> changes[id] = ElementDiff.Updated(before = before, after = after)
                is ElementDiff.Added -> changes[id] = ElementDiff.Added(after)
                is ElementDiff.Updated -> if (after == prior.before) changes.remove(id) else changes[id] = ElementDiff.Updated(prior.before, after)
                is ElementDiff.Deleted -> error("unreachable: deleted elements have no effective value")
            }
        }

        override fun delete(id: ElementId) {
            val before = effective(id) ?: throw IllegalArgumentException("Element ${id.value} does not exist")
            when (val prior = changes[id]) {
                null -> changes[id] = ElementDiff.Deleted(before)
                is ElementDiff.Added -> changes.remove(id)
                is ElementDiff.Updated -> changes[id] = ElementDiff.Deleted(prior.before)
                is ElementDiff.Deleted -> error("unreachable: deleted elements have no effective value")
            }
        }

        override fun setTitle(title: String) {
            this.title = title
        }

        fun diffs(): List<ElementDiff> = changes.values.toList()

        fun titleChange(): TitleChange? = if (title != baseTitle) TitleChange(baseTitle, title) else null
    }

    private companion object {
        const val DEFAULT_HISTORY_LIMIT = 200
    }
}
