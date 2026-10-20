package io.github.subhaneetshrestha.afnopadd.document

import kotlin.random.Random

/** Deterministic element fixtures for instrumented document tests (the JVM tests have their own copy). */
object DeviceFixtures {

    val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(21))

    fun stroke(
        z: FractionalIndex,
        pointCount: Int = 3,
        seed: Int = 1,
        id: ElementId = ElementId(ulid.next()),
    ): StrokeElement {
        val random = Random(seed)
        return StrokeElement(
            id = id,
            anchor = WorldPoint(random.nextDouble() * 1_000, random.nextDouble() * 1_000),
            frameLevel = 0,
            z = z,
            bounds = WorldRect(0.0, 0.0, 10.0, 10.0),
            version = 1,
            versionNonce = random.nextInt(),
            createdAt = 1_000L,
            updatedAt = 1_000L,
            brush = BrushSpec(familyId = "ink:pressure-pen", size = 4f, colorArgb = 0xFF000000, opacity = 1f, epsilon = 0.025f),
            points = StrokePoints.of(
                x = FloatArray(pointCount) { random.nextFloat() * 100 },
                y = FloatArray(pointCount) { random.nextFloat() * 100 },
                pressure = FloatArray(pointCount) { random.nextFloat() },
                tiltRad = FloatArray(pointCount) { 0f },
                orientationRad = FloatArray(pointCount) { 0f },
                timeMillis = FloatArray(pointCount) { it * 8f },
            ),
        )
    }

    fun shape(z: FractionalIndex, id: ElementId = ElementId(ulid.next())): ShapeElement = ShapeElement(
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
