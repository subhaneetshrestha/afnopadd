package io.github.subhaneetshrestha.afnopadd.document.internal

import io.github.subhaneetshrestha.afnopadd.document.CommittedTransaction
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementDiff
import io.github.subhaneetshrestha.afnopadd.document.SheetWriter
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.internal.db.ElementEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.db.MetaEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.db.OpEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.db.SheetDatabase
import io.github.subhaneetshrestha.afnopadd.document.internal.db.StrokePointsEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.db.toElementEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.db.toStrokePointsEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.json.ElementJsonCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicReference

/**
 * Persists committed transactions one after another on a single writer. Each transaction becomes
 * one SQLite transaction (element rows, stroke blobs, op rows, meta), so a crash can only lose the
 * gesture that was in flight. A failed write is remembered and rethrown by the next [flush];
 * later transactions still persist because every write carries full element rows.
 */
internal class RoomSheetWriter(
    private val database: SheetDatabase,
    private val codec: ElementJsonCodec,
    private val beforeCommit: suspend (CommittedTransaction) -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : SheetWriter {

    private sealed interface Command {
        class Write(val transaction: CommittedTransaction) : Command
        class Flush(val done: CompletableDeferred<Unit>) : Command
    }

    private val queue = Channel<Command>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lastError = AtomicReference<Throwable?>(null)

    /**
     * Strokes whose points a failed write did not store. A later change to such a stroke rewrites
     * its points too, even if they did not change, so a move after a failed add cannot leave it
     * empty. Only touched by the single consumer coroutine.
     */
    private val pointsMissing = HashSet<String>()

    init {
        scope.launch {
            for (command in queue) {
                when (command) {
                    is Command.Write -> try {
                        persist(command.transaction)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        command.transaction.diffs.forEach { diff ->
                            val after = (diff as? ElementDiff.Added)?.after ?: (diff as? ElementDiff.Updated)?.after
                            if (after is StrokeElement) pointsMissing += diff.id.value
                        }
                        lastError.compareAndSet(null, e)
                    }
                    is Command.Flush -> command.done.complete(Unit)
                }
            }
        }
    }

    override fun write(transaction: CommittedTransaction) {
        check(queue.trySend(Command.Write(transaction)).isSuccess) { "The sheet writer is closed" }
    }

    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        queue.send(Command.Flush(done))
        done.await()
        lastError.getAndSet(null)?.let { throw it }
    }

    suspend fun close() {
        try {
            flush()
        } finally {
            queue.close()
            scope.cancel()
        }
    }

    private suspend fun persist(transaction: CommittedTransaction) {
        val elements = ArrayList<ElementEntity>(transaction.diffs.size)
        val points = ArrayList<StrokePointsEntity>()
        val ops = ArrayList<OpEntity>(transaction.diffs.size + 1)
        for (diff in transaction.diffs) {
            when (diff) {
                is ElementDiff.Added -> {
                    elements += diff.after.toElementEntity(codec)
                    (diff.after as? StrokeElement)?.let { points += it.toStrokePointsEntity() }
                    ops += transaction.op(OP_ADD, diff.id.value, before = null, after = diff.after)
                }
                is ElementDiff.Updated -> {
                    elements += diff.after.toElementEntity(codec)
                    val after = diff.after
                    if (after is StrokeElement && (diff.before !is StrokeElement || after.points != diff.before.points || diff.id.value in pointsMissing)) {
                        points += after.toStrokePointsEntity()
                    }
                    ops += transaction.op(OP_UPDATE, diff.id.value, before = diff.before, after = diff.after)
                }
                is ElementDiff.Deleted -> {
                    elements += diff.before.toElementEntity(codec, deleted = true)
                    ops += transaction.op(OP_DELETE, diff.id.value, before = diff.before, after = null)
                }
            }
        }
        transaction.titleChange?.let { change ->
            ops += OpEntity(
                txnId = transaction.id.value,
                kind = transaction.kind.name,
                inverseOf = transaction.inverseOf?.value,
                label = transaction.label,
                committedAt = transaction.committedAt,
                elementId = null,
                op = OP_TITLE,
                beforeJson = JsonPrimitive(change.before).toString(),
                afterJson = JsonPrimitive(change.after).toString(),
            )
        }
        val meta = buildList {
            add(MetaEntity(RoomSheetStore.META_UPDATED_AT, transaction.committedAt.toString()))
            transaction.titleChange?.let { add(MetaEntity(RoomSheetStore.META_TITLE, it.after)) }
        }
        database.sheetWriteDao().write(elements, points, ops, meta) { beforeCommit(transaction) }
        if (pointsMissing.isNotEmpty()) {
            points.forEach { pointsMissing -= it.elementId }
            transaction.diffs.filterIsInstance<ElementDiff.Deleted>().forEach { pointsMissing -= it.id.value }
        }
    }

    private fun CommittedTransaction.op(op: String, elementId: String, before: Element?, after: Element?) = OpEntity(
        txnId = id.value,
        kind = kind.name,
        inverseOf = inverseOf?.value,
        label = label,
        committedAt = committedAt,
        elementId = elementId,
        op = op,
        beforeJson = before?.let { codec.encode(it).toString() },
        afterJson = after?.let { codec.encode(it).toString() },
    )

    private companion object {
        const val OP_ADD = "add"
        const val OP_UPDATE = "update"
        const val OP_DELETE = "delete"
        const val OP_TITLE = "title"
    }
}
