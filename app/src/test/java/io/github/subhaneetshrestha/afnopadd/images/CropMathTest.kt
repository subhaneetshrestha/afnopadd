package io.github.subhaneetshrestha.afnopadd.images

import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.LocalSize
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.images.crop.CropMath
import io.github.subhaneetshrestha.afnopadd.tools.select.Transforms
import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {

    private val size = LocalSize(200f, 100f)

    private fun assertRect(expected: LocalRect, actual: LocalRect) {
        assertEquals("left", expected.left, actual.left, 1e-3f)
        assertEquals("top", expected.top, actual.top, 1e-3f)
        assertEquals("right", expected.right, actual.right, 1e-3f)
        assertEquals("bottom", expected.bottom, actual.bottom, 1e-3f)
    }

    @Test
    fun `a crop is kept inside the image and never smaller than the minimum`() {
        assertRect(LocalRect(0f, 0f, 200f, 100f), CropMath.clamp(LocalRect(-50f, -10f, 300f, 120f), size, minSize = 10f))
        // Collapsed past the minimum: it keeps the minimum from its left and top.
        assertRect(LocalRect(50f, 20f, 60f, 30f), CropMath.clamp(LocalRect(50f, 20f, 52f, 21f), size, minSize = 10f))
        // A minimum that would cross the far edge is pulled back inside.
        assertRect(LocalRect(190f, 90f, 200f, 100f), CropMath.clamp(LocalRect(198f, 95f, 199f, 99f), size, minSize = 10f))
    }

    @Test
    fun `dragging a corner moves only that corner`() {
        val crop = LocalRect(20f, 10f, 180f, 90f)

        assertRect(LocalRect(40f, 25f, 180f, 90f), CropMath.dragCorner(crop, CropMath.Corner.TOP_LEFT, LocalPoint(40f, 25f), size, 10f))
        assertRect(LocalRect(20f, 10f, 200f, 100f), CropMath.dragCorner(crop, CropMath.Corner.BOTTOM_RIGHT, LocalPoint(250f, 130f), size, 10f))
        // A corner dragged past the opposite one stops at the minimum size.
        assertRect(LocalRect(20f, 10f, 30f, 90f), CropMath.dragCorner(crop, CropMath.Corner.TOP_RIGHT, LocalPoint(0f, 10f), size, 10f))
    }

    @Test
    fun `world points map into a turned image's own units and back`() {
        // A 40 x 20 image at the origin, turned a quarter about its centre (20, 10).
        val image = HitTestFixtures.image(1, WorldRect(0.0, 0.0, 40.0, 20.0)).copy(rotationDeg = 90f)

        // Its unturned top-left corner (0, 0) now sits at the top right of the turned box: (30, -10).
        val local = CropMath.toImageLocal(WorldPoint(30.0, -10.0), image)
        assertEquals(0f, local.x, 1e-4f)
        assertEquals(0f, local.y, 1e-4f)
        val corners = CropMath.cornersInWorld(image, LocalRect(0f, 0f, 40f, 20f))
        assertEquals(30.0, corners[0].x, 1e-4)
        assertEquals(-10.0, corners[0].y, 1e-4)
    }

    @Test
    fun `an image's bounds cover only its cropped part`() {
        val bounds = Transforms.imageBounds(WorldPoint(100.0, 50.0), frameLevel = 0, size = size, rotationDeg = 0f, crop = LocalRect(20f, 10f, 120f, 60f))

        assertEquals(WorldRect(120.0, 60.0, 220.0, 110.0), bounds)
    }
}
