package io.github.subhaneetshrestha.afnopadd.shell

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.subhaneetshrestha.afnopadd.canvas.Background
import io.github.subhaneetshrestha.afnopadd.shell.settings.Settings
import io.github.subhaneetshrestha.afnopadd.shell.settings.SettingsPreferences
import io.github.subhaneetshrestha.afnopadd.shell.settings.ToolbarPlacement
import io.github.subhaneetshrestha.afnopadd.tools.FingerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesTest {

    @Test
    fun `an empty file gives the defaults`() {
        val settings = SettingsPreferences.read(emptyPreferences())

        assertEquals(Settings(), settings)
        assertEquals(FingerMode.AUTO, settings.fingerMode)
        assertEquals(true, settings.keepScreenOn)
        assertEquals(false, settings.stylusSeen)
    }

    @Test
    fun `every setting round trips`() {
        val changed = Settings(
            fingerMode = FingerMode.NAVIGATE,
            toolbarPlacement = ToolbarPlacement.SIDE,
            defaultBackground = Background.DOTS,
            keepScreenOn = false,
            leftHanded = true,
            stylusSeen = true,
        )
        val prefs = mutablePreferencesOf()

        SettingsPreferences.write(prefs, changed)

        assertEquals(changed, SettingsPreferences.read(prefs))
    }

    @Test
    fun `a value this version does not know falls back to its default and leaves the rest alone`() {
        val prefs = mutablePreferencesOf()
        SettingsPreferences.write(prefs, Settings(fingerMode = FingerMode.DRAW, leftHanded = true))
        prefs[stringPreferencesKey("finger_mode")] = "TELEPATHY"

        val settings = SettingsPreferences.read(prefs)

        assertEquals(FingerMode.AUTO, settings.fingerMode)
        assertEquals(true, settings.leftHanded)
    }

    @Test
    fun `writing settings keeps keys that belong to others, such as recent colours`() {
        val recents = stringPreferencesKey("recent_colors")
        val prefs = mutablePreferencesOf(recents to "ff000000")

        SettingsPreferences.write(prefs, Settings(leftHanded = true))

        assertEquals("ff000000", prefs[recents])
    }
}
