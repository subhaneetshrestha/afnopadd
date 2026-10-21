package io.github.subhaneetshrestha.afnopadd.tools.select

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.movedBy
import io.github.subhaneetshrestha.afnopadd.document.withCommon
import io.github.subhaneetshrestha.afnopadd.tools.Command
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext

/**
 * The selection's context bar (SPEC-drawing-tools decision 7): delete, duplicate, bring to front,
 * send to back. Each is one undoable transaction, is enabled only while something is selected, and
 * acts on the selected elements that are still on the sheet. Reordering gives only the selected
 * elements new fractional indexes, keeping their order among themselves.
 */
class SelectionCommands(private val context: ToolContext) {

    private val selection get() = context.selection
    private val enabled = context.selection.hasSelection

    val all: List<Command> = listOf(
        Command(DELETE, R.string.command_delete_selection, R.drawable.ic_delete, enabled) { delete() },
        Command(DUPLICATE, R.string.command_duplicate_selection, R.drawable.ic_duplicate, enabled) { duplicate() },
        Command(BRING_TO_FRONT, R.string.command_bring_to_front, R.drawable.ic_bring_to_front, enabled) { bringToFront() },
        Command(SEND_TO_BACK, R.string.command_send_to_back, R.drawable.ic_send_to_back, enabled) { sendToBack() },
    )

    /** The selected elements still on the sheet, bottom to top. */
    private fun selectedElements(): List<Element> {
        val elements = context.sheet.snapshot.value.elements
        return selection.selected.value.mapNotNull { elements[it] }.sortedBy { it.z }
    }

    private fun delete() {
        val doomed = selectedElements()
        selection.clear()
        if (doomed.isEmpty()) return
        context.sheet.transact(label = "Delete") { doomed.forEach { delete(it.id) } }
    }

    private fun duplicate() {
        val originals = selectedElements()
        if (originals.isEmpty()) return
        val offset = DUPLICATE_OFFSET_PX / context.camera.camera.value.zoom
        val now = context.clock()
        var top = context.sheet.snapshot.value.ordered.lastOrNull()?.z
        val copies = originals.map { original ->
            val z = FractionalIndex.between(top, null).also { top = it }
            original.movedBy(offset, offset).withCommon(
                id = ElementId(context.ulid.next()),
                z = z,
                version = 1,
                versionNonce = context.random.nextInt(),
                createdAt = now,
                updatedAt = now,
            )
        }
        context.sheet.transact(label = "Duplicate") { copies.forEach { add(it) } }
        selection.set(copies.mapTo(LinkedHashSet()) { it.id })
    }

    private fun bringToFront() {
        val chosen = selectedElements()
        if (chosen.isEmpty()) return
        var top = context.sheet.snapshot.value.ordered.lastOrNull()?.z
        reorder(chosen.associate { it.id to FractionalIndex.between(top, null).also { z -> top = z } }, "Bring to front")
    }

    private fun sendToBack() {
        val chosen = selectedElements()
        if (chosen.isEmpty()) return
        var bottom = context.sheet.snapshot.value.ordered.firstOrNull()?.z
        // From the topmost selected down, each goes just below the last, so their order is kept.
        reorder(chosen.asReversed().associate { it.id to FractionalIndex.between(null, bottom).also { z -> bottom = z } }, "Send to back")
    }

    private fun reorder(newZ: Map<ElementId, FractionalIndex>, label: String) {
        val now = context.clock()
        context.sheet.transact(label = label) {
            for ((id, z) in newZ) {
                update(id) { it.withCommon(z = z, version = it.version + 1, versionNonce = context.random.nextInt(), updatedAt = now) }
            }
        }
    }

    companion object {
        const val DELETE = "cmd:delete-selection"
        const val DUPLICATE = "cmd:duplicate-selection"
        const val BRING_TO_FRONT = "cmd:bring-to-front"
        const val SEND_TO_BACK = "cmd:send-to-back"
        val IDS = listOf(DELETE, DUPLICATE, BRING_TO_FRONT, SEND_TO_BACK)

        /** Copies land this many screen pixels down and to the right, so they are visibly separate. */
        const val DUPLICATE_OFFSET_PX = 16.0
    }
}
