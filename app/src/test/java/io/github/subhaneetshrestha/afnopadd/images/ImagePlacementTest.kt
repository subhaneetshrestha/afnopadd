package io.github.subhaneetshrestha.afnopadd.images

import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.images.internal.ImagePlacement
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePlacementTest {

    // A 1,000 x 600 world-unit viewport centred on (500, 300): 60 percent of the short side is 360.
    private val placement = Placement(center = WorldPoint(500.0, 300.0), frameLevel = 0, shortSideWorld = 600.0, gapWorld = 16.0)

    private fun assertRect(expected: WorldRect, actual: WorldRect) {
        assertEquals("left", expected.left, actual.left, 1e-9)
        assertEquals("top", expected.top, actual.top, 1e-9)
        assertEquals("right", expected.right, actual.right, 1e-9)
        assertEquals("bottom", expected.bottom, actual.bottom, 1e-9)
    }

    @Test
    fun `a single image is centred and fits 60 percent of the shorter viewport side`() {
        val (landscape) = ImagePlacement.layout(listOf(ImagePlacement.PixelSize(4_000, 3_000)), placement)
        assertRect(WorldRect(320.0, 165.0, 680.0, 435.0), landscape)

        val (portrait) = ImagePlacement.layout(listOf(ImagePlacement.PixelSize(1_000, 2_000)), placement)
        assertRect(WorldRect(410.0, 120.0, 590.0, 480.0), portrait)
    }

    @Test
    fun `several images sit in a centred row with gaps, each fitted to the same box`() {
        val rects = ImagePlacement.layout(
            listOf(ImagePlacement.PixelSize(400, 400), ImagePlacement.PixelSize(800, 400), ImagePlacement.PixelSize(100, 200)),
            placement,
        )

        // Widths 360, 360 and 180 plus two 16-unit gaps make a 932-unit row centred on x = 500.
        assertRect(WorldRect(34.0, 120.0, 394.0, 480.0), rects[0])
        assertRect(WorldRect(410.0, 210.0, 770.0, 390.0), rects[1])
        assertRect(WorldRect(786.0, 120.0, 966.0, 480.0), rects[2])
    }

    @Test
    fun `local sizes are in the frame's units`() {
        val deep = placement.copy(frameLevel = 2)

        val size = ImagePlacement.localSize(WorldRect(0.0, 0.0, 3.6, 2.7), deep.frameLevel)

        assertEquals(360f, size.width, 1e-3f)
        assertEquals(270f, size.height, 1e-3f)
    }

    @Test
    fun `an import summary counts what landed and what failed`() {
        val summary = ImportSummary(
            listOf(
                ImportResult.Imported(source = "a", id = io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures.idOf(1)),
                ImportResult.Failed(source = "b", reason = ImportFailure.UNSUPPORTED),
                ImportResult.Imported(source = "c", id = io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures.idOf(2)),
            ),
        )

        assertEquals(2, summary.imported.size)
        assertEquals(listOf("b"), summary.failed.map { it.source })
    }
}
