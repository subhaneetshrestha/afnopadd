package io.github.subhaneetshrestha.afnopadd.document

import kotlin.random.Random

/** Deterministic fixtures shared by document tests. */
object TestElements {

    val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(11))

    fun stroke(
        id: ElementId = ElementId(ulid.next()),
        z: FractionalIndex = FractionalIndex.first(),
        anchor: WorldPoint = WorldPoint(0.0, 0.0),
        version: Int = 1,
    ): StrokeElement = StrokeElement(
        id = id,
        anchor = anchor,
        frameLevel = 0,
        z = z,
        bounds = WorldRect(anchor.x, anchor.y, anchor.x + 10.0, anchor.y + 10.0),
        version = version,
        versionNonce = 1,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        brush = BrushSpec(familyId = "ink:pressure-pen", size = 4f, colorArgb = 0xFF000000, opacity = 1f, epsilon = 0.025f),
        points = StrokePoints.of(
            x = floatArrayOf(0f, 5f, 10f),
            y = floatArrayOf(0f, 5f, 10f),
            pressure = floatArrayOf(0.5f, 0.5f, 0.5f),
            tiltRad = floatArrayOf(0f, 0f, 0f),
            orientationRad = floatArrayOf(0f, 0f, 0f),
            timeMillis = floatArrayOf(0f, 8f, 16f),
        ),
    )

    fun shape(
        id: ElementId = ElementId(ulid.next()),
        z: FractionalIndex = FractionalIndex.first(),
    ): ShapeElement = ShapeElement(
        id = id,
        anchor = WorldPoint(100.0, 100.0),
        frameLevel = 0,
        z = z,
        bounds = WorldRect(100.0, 100.0, 150.0, 120.0),
        version = 1,
        versionNonce = 1,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        shape = ShapeKind.RECTANGLE,
        geometry = ShapeGeometry(LocalPoint(0f, 0f), LocalPoint(50f, 20f)),
        style = ShapeStyle(strokeWidth = 2f, colorArgb = 0xFF000000),
    )
}
