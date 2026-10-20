package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class FrameLevelsTest {

    @Test
    fun `the frame level is the power of ten below the zoom`() {
        val expectations = mapOf(
            0.001 to -3,
            0.0099 to -3,
            0.01 to -2,
            0.999 to -1,
            1.0 to 0,
            9.99 to 0,
            10.0 to 1,
            12_345.0 to 4,
            100_000.0 to 5,
        )

        expectations.forEach { (zoom, level) -> assertEquals("zoom $zoom", level, FrameLevels.levelFor(zoom)) }
    }

    @Test
    fun `one local unit is ten to the minus level world units`() {
        assertEquals(1.0, FrameLevels.worldUnitsPerLocalUnit(0), 0.0)
        assertEquals(0.01, FrameLevels.worldUnitsPerLocalUnit(2), 1e-15)
        assertEquals(1_000.0, FrameLevels.worldUnitsPerLocalUnit(-3), 1e-9)
    }

    @Test
    fun `world and local coordinates round trip`() {
        val random = Random(11)
        repeat(1_000) {
            val anchor = WorldPoint(random.nextDouble(-1e6, 1e6), random.nextDouble(-1e6, 1e6))
            val level = random.nextInt(-3, 6)
            val local = LocalPoint(random.nextFloat() * 2_000f - 1_000f, random.nextFloat() * 2_000f - 1_000f)

            val world = FrameLevels.localToWorld(local, anchor, level)
            val back = FrameLevels.worldToLocal(world, anchor, level)

            assertEquals(local.x, back.x, 1e-3f)
            assertEquals(local.y, back.y, 1e-3f)
        }
    }

    @Test
    fun `a world offset of one unit at level two is one hundred local units`() {
        val anchor = WorldPoint(10.0, 20.0)

        val local = FrameLevels.worldToLocal(WorldPoint(11.0, 19.5), anchor, level = 2)

        assertEquals(100f, local.x, 1e-6f)
        assertEquals(-50f, local.y, 1e-6f)
    }
}
