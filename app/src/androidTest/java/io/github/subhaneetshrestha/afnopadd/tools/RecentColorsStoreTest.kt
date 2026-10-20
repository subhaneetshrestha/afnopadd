package io.github.subhaneetshrestha.afnopadd.tools

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.tools.color.RecentColorsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the device: DataStore's atomic rename does not overwrite on a Windows host JVM. */
@RunWith(AndroidJUnit4::class)
class RecentColorsStoreTest {

    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "recent-colors-${System.nanoTime()}")

    private fun <T> withStore(block: suspend (RecentColorsStore) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val store = RecentColorsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
            return runBlocking { block(store) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun recentColoursSurviveARestart() {
        withStore { store ->
            store.add(0xFFFF0000)
            store.add(0xFF00FF00)
        }

        assertEquals(listOf(0xFF00FF00, 0xFFFF0000), withStore { it.colors.first() })
        dir.deleteRecursively()
    }
}
