package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerRole
import org.junit.Assert.assertEquals
import org.junit.Test

class InputPolicyTest {

    private val pen = setOf(ToolCapability.STROKE)
    private val hand = setOf(ToolCapability.NAVIGATE)

    @Test
    fun `in AUTO a finger draws until a stylus has been seen, then pans`() {
        assertEquals(PointerRole.DRAW, InputPolicy.roleFor(PointerKind.FINGER, pen, FingerMode.AUTO, stylusSeen = false))
        assertEquals(PointerRole.NAVIGATE, InputPolicy.roleFor(PointerKind.FINGER, pen, FingerMode.AUTO, stylusSeen = true))
    }

    @Test
    fun `the stylus always draws with a drawing tool`() {
        for (mode in FingerMode.entries) for (seen in listOf(false, true)) {
            assertEquals("$mode seen=$seen", PointerRole.DRAW, InputPolicy.roleFor(PointerKind.STYLUS, pen, mode, seen))
            assertEquals("$mode seen=$seen", PointerRole.DRAW, InputPolicy.roleFor(PointerKind.ERASER, pen, mode, seen))
        }
    }

    @Test
    fun `forcing a finger mode overrides the stylus flag`() {
        for (seen in listOf(false, true)) {
            assertEquals(PointerRole.DRAW, InputPolicy.roleFor(PointerKind.FINGER, pen, FingerMode.DRAW, seen))
            assertEquals(PointerRole.NAVIGATE, InputPolicy.roleFor(PointerKind.FINGER, pen, FingerMode.NAVIGATE, seen))
        }
    }

    @Test
    fun `a mouse draws, and an unknown pointer is treated like a finger`() {
        assertEquals(PointerRole.DRAW, InputPolicy.roleFor(PointerKind.MOUSE, pen, FingerMode.NAVIGATE, stylusSeen = true))
        assertEquals(PointerRole.NAVIGATE, InputPolicy.roleFor(PointerKind.UNKNOWN, pen, FingerMode.AUTO, stylusSeen = true))
        assertEquals(PointerRole.DRAW, InputPolicy.roleFor(PointerKind.UNKNOWN, pen, FingerMode.AUTO, stylusSeen = false))
    }

    @Test
    fun `the hand tool navigates with every pointer in every mode`() {
        for (kind in PointerKind.entries) for (mode in FingerMode.entries) for (seen in listOf(false, true)) {
            assertEquals("$kind $mode seen=$seen", PointerRole.NAVIGATE, InputPolicy.roleFor(kind, hand, mode, seen))
        }
    }

    @Test
    fun `only a stylus counts as seeing a stylus`() {
        assertEquals(true, InputPolicy.isStylus(PointerKind.STYLUS))
        assertEquals(true, InputPolicy.isStylus(PointerKind.ERASER))
        assertEquals(false, InputPolicy.isStylus(PointerKind.FINGER))
        assertEquals(false, InputPolicy.isStylus(PointerKind.MOUSE))
    }
}
