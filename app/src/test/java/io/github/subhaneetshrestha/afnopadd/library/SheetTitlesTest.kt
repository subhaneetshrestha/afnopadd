package io.github.subhaneetshrestha.afnopadd.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetTitlesTest {

    @Test
    fun `titles are trimmed`() {
        assertEquals("Plans", SheetTitles.normalize("  Plans  "))
    }

    @Test
    fun `blank and over-long titles are rejected`() {
        assertNull(SheetTitles.normalize("   "))
        assertNull(SheetTitles.normalize("x".repeat(81)))
        assertEquals(80, SheetTitles.normalize("x".repeat(80))?.length)
    }

    @Test
    fun `the next default title follows the highest existing number`() {
        assertEquals("Sheet 1", SheetTitles.nextDefault("Sheet", emptyList()))
        assertEquals("Sheet 4", SheetTitles.nextDefault("Sheet", listOf("Sheet 1", "Sheet 3", "Plans", "Sheet x")))
    }
}
