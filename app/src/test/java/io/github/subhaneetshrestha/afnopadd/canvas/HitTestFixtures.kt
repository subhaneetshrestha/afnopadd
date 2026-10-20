package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.LocalSize
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeGeometry
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.ShapeStyle
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.pow

/** Elements with exact, easy-to-reason-about geometry for hit-testing and eraser tests. */
object HitTestFixtures {

    /** A valid ULID that encodes [n], so tests can name elements by number. */
    fun idOf(n: Int) = ElementId("01JAX9Q8K0TZ3M4N5P6R7S" + n.toString().padStart(4, '0'))

    /**
     * A straight stroke between two world points, stored in frame [level] with a brush of
     * [widthWorld] world units, so its ink covers everything within `widthWorld / 2` of the segment.
     */
    fun line(
        id: Int,
        from: WorldPoint,
        to: WorldPoint,
        z: String = "a0",
        widthWorld: Double = 2.0,
        level: Int = 0,
    ): StrokeElement {
        val perWorld = 10.0.pow(level)
        val dx = ((to.x - from.x) * perWorld).toFloat()
        val dy = ((to.y - from.y) * perWorld).toFloat()
        val half = widthWorld / 2
        return StrokeElement(
            id = idOf(id),
            anchor = from,
            frameLevel = level,
            z = FractionalIndex(z),
            bounds = WorldRect(minOf(from.x, to.x) - half, minOf(from.y, to.y) - half, maxOf(from.x, to.x) + half, maxOf(from.y, to.y) + half),
            version = 1,
            versionNonce = 1,
            createdAt = 0L,
            updatedAt = 0L,
            brush = BrushSpec("ink:pressure-pen", (widthWorld * perWorld).toFloat(), 0xFF000000, 1f, 0.025f),
            points = StrokePoints.of(
                x = floatArrayOf(0f, dx / 2, dx),
                y = floatArrayOf(0f, dy / 2, dy),
                pressure = floatArrayOf(1f, 1f, 1f),
                tiltRad = floatArrayOf(0f, 0f, 0f),
                orientationRad = floatArrayOf(0f, 0f, 0f),
                timeMillis = floatArrayOf(0f, 8f, 16f),
            ),
        )
    }

    /** An outline shape spanning [rect] in world units with a 2-unit stroke. */
    fun shape(id: Int, kind: ShapeKind, rect: WorldRect, z: String = "a0", strokeWidth: Float = 2f): ShapeElement = ShapeElement(
        id = idOf(id),
        anchor = WorldPoint(rect.left, rect.top),
        frameLevel = 0,
        z = FractionalIndex(z),
        bounds = WorldRect(rect.left - strokeWidth / 2, rect.top - strokeWidth / 2, rect.right + strokeWidth / 2, rect.bottom + strokeWidth / 2),
        version = 1,
        versionNonce = 1,
        createdAt = 0L,
        updatedAt = 0L,
        shape = kind,
        geometry = ShapeGeometry(LocalPoint(0f, 0f), LocalPoint(rect.width.toFloat(), rect.height.toFloat())),
        style = ShapeStyle(strokeWidth = strokeWidth, colorArgb = 0xFF000000),
    )

    fun image(id: Int, rect: WorldRect, z: String = "a0"): ImageElement = ImageElement(
        id = idOf(id),
        anchor = WorldPoint(rect.left, rect.top),
        frameLevel = 0,
        z = FractionalIndex(z),
        bounds = rect,
        version = 1,
        versionNonce = 1,
        createdAt = 0L,
        updatedAt = 0L,
        asset = AssetId("a".repeat(64)),
        size = LocalSize(rect.width.toFloat(), rect.height.toFloat()),
        rotationDeg = 0f,
        crop = null,
    )
}
