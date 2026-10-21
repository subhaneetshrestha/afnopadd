package io.github.subhaneetshrestha.afnopadd.tools

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.tools.color.RecentColorsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            val store = RecentColorsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
            return runBlocking { block(store) }
        } finally {
            // Wait for the store to let go of the file; the next one would otherwise find it still active.
            runBlocking { job.cancelAndJoin() }
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
