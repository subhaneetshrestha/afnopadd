package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.TestElements
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderGeometryTest {

    @Test
    fun `content bounds are the union of element bounds`() {
        val a = TestElements.stroke(z = FractionalIndex("a0"), anchor = WorldPoint(0.0, 0.0)) // bounds 0,0..10,10
        val b = TestElements.stroke(z = FractionalIndex("a1"), anchor = WorldPoint(-20.0, 30.0)) // bounds -20,30..-10,40
        val snapshot = ElementSnapshot(listOf(a, b).associateBy { it.id })

        assertEquals(WorldRect(-20.0, 0.0, 10.0, 40.0), RenderGeometry.contentBounds(snapshot))
        assertNull(RenderGeometry.contentBounds(ElementSnapshot.EMPTY))
    }

    @Test
    fun `fit pads evenly by a fraction of the longer side and scales the long edge to the requested size`() {
        val fit = RenderGeometry.fit(WorldRect(0.0, 0.0, 200.0, 100.0), maxEdgePx = 512, paddingFraction = 0.05)

        assertEquals(WorldRect(-10.0, -10.0, 210.0, 110.0), fit.bounds)
        assertEquals(512.0 / 220.0, fit.scale, 1e-12)
        assertEquals(512, fit.widthPx)
        assertEquals(279, fit.heightPx)
    }

    @Test
    fun `fit of a point still yields a renderable square`() {
        val fit = RenderGeometry.fit(WorldRect(5.0, 5.0, 5.0, 5.0), maxEdgePx = 512)

        assertEquals(512, fit.widthPx)
        assertEquals(512, fit.heightPx)
    }

    @Test
    fun `pixel size rounds up`() {
        assertEquals(PixelSize(1001, 500), RenderGeometry.pixelSize(WorldRect(0.0, 0.0, 100.05, 50.0), scale = 10.0))
    }
}
