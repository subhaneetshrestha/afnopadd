package io.github.subhaneetshrestha.afnopadd.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasInputSink
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeFrame
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

enum class ToolCapability { STROKE, SHAPE, SELECT, ERASE, NAVIGATE }

/**
 * Something the user picks up. Tools are identified by stable string ids (`tool:pen`) so toolbar
 * layouts, presets and later plugins can refer to them; the toolbar is rendered from those ids.
 */
interface Tool {
    val id: String

    @get:StringRes
    val labelRes: Int

    @get:DrawableRes
    val iconRes: Int

    val capabilities: Set<ToolCapability>

    fun createController(context: ToolContext): ToolController
}

/** The live state machine of the active tool; it receives canvas input and commits transactions. */
interface ToolController : CanvasInputSink {
    fun onActivated() = Unit

    fun onDeactivated() = Unit

    /** The brush for a wet stroke started in [frame], or null if this tool does not ink. */
    fun wetStrokeBrush(frame: StrokeFrame): BrushSpec? = null
}

/** Everything a tool controller needs; clock and randomness are injectable for deterministic tests. */
class ToolContext(
    val sheet: Sheet,
    val camera: CameraController,
    val style: StateFlow<CurrentStyle>,
    val clock: () -> Long = System::currentTimeMillis,
    val random: Random = Random.Default,
    val ulid: Ulid = Ulid.Default,
)

/** The colour and thickness the stroke tools draw with; thickness is chosen in screen pixels. */
data class CurrentStyle(
    val colorArgb: Long = 0xFF000000,
    val widthPx: Float = 4f,
)
