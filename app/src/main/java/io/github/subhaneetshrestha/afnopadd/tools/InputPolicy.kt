package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerRole

/** What a single finger does: AUTO draws until a stylus has been seen on the device, then pans (app-shell spec decision 4). */
enum class FingerMode { AUTO, DRAW, NAVIGATE }

/**
 * Decides whether a single pointer draws or moves the camera. Two pointers always belong to the
 * camera whatever this says; this only covers the one-pointer case.
 */
object InputPolicy {

    fun roleFor(kind: PointerKind, toolCapabilities: Set<ToolCapability>, fingerMode: FingerMode, stylusSeen: Boolean): PointerRole = when {
        // The hand tool moves around with any pointer, stylus included (drawing-tools spec decision 8).
        ToolCapability.NAVIGATE in toolCapabilities -> PointerRole.NAVIGATE
        isStylus(kind) || kind == PointerKind.MOUSE -> PointerRole.DRAW
        else -> when (fingerMode) {
            FingerMode.DRAW -> PointerRole.DRAW
            FingerMode.NAVIGATE -> PointerRole.NAVIGATE
            FingerMode.AUTO -> if (stylusSeen) PointerRole.NAVIGATE else PointerRole.DRAW
        }
    }

    /** A pointer that proves the device has a stylus; its eraser end counts too. */
    fun isStylus(kind: PointerKind): Boolean = kind == PointerKind.STYLUS || kind == PointerKind.ERASER
}
