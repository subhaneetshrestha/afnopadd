package io.github.subhaneetshrestha.afnopadd.images.commands

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.movedBy
import io.github.subhaneetshrestha.afnopadd.document.withCommon
import io.github.subhaneetshrestha.afnopadd.tools.Command
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.select.Transforms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The image actions of the selection's context bar (SPEC-image-board): crop (entering crop mode
 * is the editor's job, through [onCrop]), reset crop, and arrange in row. Each change is one
 * undoable transaction; enabled states follow the selection and the sheet in [scope].
 */
class ImageCommands(
    private val context: ToolContext,
    scope: CoroutineScope,
    private val onCrop: (ImageElement) -> Unit,
) {
    private val selectedImages: StateFlow<List<ImageElement>> =
        combine(context.selection.selected, context.sheet.snapshot) { ids, snapshot -> ids.mapNotNull { snapshot.elements[it] as? ImageElement } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val singleImage = combine(context.selection.selected, selectedImages) { ids, images -> ids.size == 1 && images.size == 1 }
        .stateIn(scope, SharingStarted.Eagerly, false)
    private val singleCroppedImage = combine(context.selection.selected, selectedImages) { ids, images -> ids.size == 1 && images.singleOrNull()?.crop != null }
        .stateIn(scope, SharingStarted.Eagerly, false)
    // Other selected elements do not get in the way; they are simply left where they are.
    private val twoOrMoreImages: StateFlow<Boolean> = selectedImages.map { it.size >= 2 }.stateIn(scope, SharingStarted.Eagerly, false)

    val all: List<Command> = listOf(
        Command(CROP, R.string.command_image_crop, R.drawable.ic_crop, singleImage) { current().singleOrNull()?.let(onCrop) },
        Command(RESET_CROP, R.string.command_image_reset_crop, R.drawable.ic_crop_reset, singleCroppedImage) { resetCrop() },
        Command(ARRANGE_ROW, R.string.command_arrange_row, R.drawable.ic_arrange_row, twoOrMoreImages) { arrangeRow() },
    )

    /** The selected images as the sheet has them now (the flow may lag a frame behind a transaction). */
    private fun current(): List<ImageElement> {
        val elements = context.sheet.snapshot.value.elements
        return context.selection.selected.value.mapNotNull { elements[it] as? ImageElement }
    }

    private fun resetCrop() {
        val image = current().singleOrNull()?.takeIf { it.crop != null } ?: return
        context.sheet.transact(label = "Reset crop") {
            update(image.id) { element ->
                (element as ImageElement).copy(
                    crop = null,
                    bounds = Transforms.imageBounds(element.anchor, element.frameLevel, element.size, element.rotationDeg, crop = null),
                ).withCommon(version = element.version + 1, versionNonce = context.random.nextInt(), updatedAt = context.clock())
            }
        }
    }

    /**
     * Lines up the selected images left to right in the order they already run, [GAP_DP] apart on
     * screen, each centred on the first one's middle; the first stays where it is. Other selected
     * elements are left alone.
     */
    private fun arrangeRow() {
        val images = current().sortedBy { it.bounds.left }
        if (images.size < 2) return
        val gap = GAP_DP * context.density / context.camera.camera.value.zoom
        val middle = (images.first().bounds.top + images.first().bounds.bottom) / 2
        var nextLeft = images.first().bounds.right + gap
        val moves = images.drop(1).associate { image ->
            val dx = nextLeft - image.bounds.left
            val dy = middle - (image.bounds.top + image.bounds.bottom) / 2
            nextLeft += image.bounds.width + gap
            image.id to (dx to dy)
        }
        val now = context.clock()
        context.sheet.transact(label = "Arrange in row") {
            for ((id, delta) in moves) {
                update(id) { element ->
                    element.movedBy(delta.first, delta.second).withCommon(version = element.version + 1, versionNonce = context.random.nextInt(), updatedAt = now)
                }
            }
        }
    }

    companion object {
        const val CROP = "cmd:image-crop"
        const val RESET_CROP = "cmd:image-reset-crop"
        const val ARRANGE_ROW = "cmd:arrange-row"
        val IDS = listOf(CROP, RESET_CROP, ARRANGE_ROW)
        /** Opens the "Add image" choice; registered by the editor, which owns the picker. */
        const val IMPORT = "cmd:image-import"
        /** Leaves crop mode; registered by the editor, which owns crop mode. */
        const val CROP_DONE = "cmd:image-crop-done"
        const val GAP_DP = 16.0
    }
}
