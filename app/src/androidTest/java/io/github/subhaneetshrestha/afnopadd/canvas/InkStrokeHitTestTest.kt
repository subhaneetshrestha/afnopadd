package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The production stroke hit test runs against Ink's native mesh, so it is checked on a device. */
@RunWith(AndroidJUnit4::class)
class InkStrokeHitTestTest {

    // A horizontal 100-unit stroke with an 8-unit marker brush: ink covers roughly |y| <= 4.
    private val stroke = StrokeElement(
        id = ElementId("01JAX9Q8K0TZ3M4N5P6R7S0001"),
        anchor = WorldPoint(0.0, 0.0),
        frameLevel = 0,
        z = FractionalIndex.first(),
        bounds = WorldRect(-4.0, -4.0, 104.0, 4.0),
        version = 1,
        versionNonce = 1,
        createdAt = 0L,
        updatedAt = 0L,
        brush = BrushSpec("ink:marker", size = 8f, colorArgb = 0xFF000000, opacity = 1f, epsilon = 0.025f),
        points = StrokePoints.of(
            x = FloatArray(11) { it * 10f },
            y = FloatArray(11),
            pressure = FloatArray(11) { 1f },
            tiltRad = FloatArray(11),
            orientationRad = FloatArray(11),
            timeMillis = FloatArray(11) { it * 8f },
        ),
    )
    private val ink = StrokeHitTest.ink()

    @Test
    fun aSegmentCrossingTheInkTouchesIt() {
        assertTrue(ink.touches(stroke, LocalPoint(50f, -20f), LocalPoint(50f, 20f), radius = 0.5f))
    }

    @Test
    fun aTapOnTheInkTouchesIt() {
        assertTrue(ink.touches(stroke, LocalPoint(30f, 1f), LocalPoint(30f, 1f), radius = 0.5f))
    }

    @Test
    fun aSegmentBesideTheInkDoesNotTouchItUntilTheRadiusReaches() {
        assertFalse(ink.touches(stroke, LocalPoint(20f, 10f), LocalPoint(80f, 10f), radius = 2f))
        assertTrue(ink.touches(stroke, LocalPoint(20f, 10f), LocalPoint(80f, 10f), radius = 8f))
    }

    @Test
    fun theTestFollowsTheInkEdgeInEveryDirection() {
        // The ink edge is at |y| = 4; a segment one unit outside it touches only once the radius exceeds one.
        assertFalse(ink.touches(stroke, LocalPoint(40f, 5f), LocalPoint(60f, 5f), radius = 0.5f))
        assertTrue(ink.touches(stroke, LocalPoint(40f, 5f), LocalPoint(60f, 5f), radius = 1.5f))
        assertFalse(ink.touches(stroke, LocalPoint(40f, -5f), LocalPoint(60f, -5f), radius = 0.5f))
        // Diagonal and vertical segments that stop short of the ink.
        assertFalse(ink.touches(stroke, LocalPoint(40f, 30f), LocalPoint(60f, 10f), radius = 1f))
        assertFalse(ink.touches(stroke, LocalPoint(50f, -30f), LocalPoint(50f, -10f), radius = 1f))
        assertTrue(ink.touches(stroke, LocalPoint(50f, -30f), LocalPoint(50f, -5f), radius = 2f))
    }
}
