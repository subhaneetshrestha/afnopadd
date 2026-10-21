package io.github.subhaneetshrestha.afnopadd.shell

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.canvas.Background
import io.github.subhaneetshrestha.afnopadd.shell.settings.DataStoreSettingsRepository
import io.github.subhaneetshrestha.afnopadd.shell.settings.Settings
import io.github.subhaneetshrestha.afnopadd.tools.FingerMode
import io.github.subhaneetshrestha.afnopadd.tools.color.RecentColorsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the device: DataStore's atomic rename does not overwrite on a Windows host JVM. */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "settings-${System.nanoTime()}")

    /** Opens the file as a fresh process would, runs [block], and closes it again. */
    private fun <T> withStore(block: suspend (DataStore<Preferences>) -> T): T {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            return runBlocking { block(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }) }
        } finally {
            // Wait for the store to let go of the file; the next one would otherwise find it still active.
            runBlocking { job.cancelAndJoin() }
        }
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun settingsAndTheStylusFlagSurviveARestart() {
        withStore { store ->
            val settings = DataStoreSettingsRepository(store)
            settings.update { it.copy(fingerMode = FingerMode.DRAW, defaultBackground = Background.GRID, keepScreenOn = false) }
            settings.update { it.copy(stylusSeen = true) }
        }

        val restored = withStore { DataStoreSettingsRepository(it).settings.first() }

        assertEquals(Settings(fingerMode = FingerMode.DRAW, defaultBackground = Background.GRID, keepScreenOn = false, stylusSeen = true), restored)
    }

    @Test
    fun settingsAndRecentColoursShareTheFileWithoutClobberingEachOther() {
        withStore { store ->
            RecentColorsStore(store).add(0xFFFF0000)
            DataStoreSettingsRepository(store).update { it.copy(leftHanded = true) }
            RecentColorsStore(store).add(0xFF00FF00)
        }

        withStore { store ->
            assertEquals(listOf(0xFF00FF00, 0xFFFF0000), RecentColorsStore(store).colors.first())
            assertEquals(true, DataStoreSettingsRepository(store).settings.first().leftHanded)
        }
    }
}
