package io.github.subhaneetshrestha.afnopadd.tools

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the device: DataStore's atomic rename does not overwrite on a Windows host JVM. */
@RunWith(AndroidJUnit4::class)
class ToolStylesStoreTest {

    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "tool-styles-${System.nanoTime()}")

    /** Opens the file as a fresh process would, runs [block], and closes it again. */
    private fun <T> withStore(block: suspend CoroutineScope.(DataStore<Preferences>) -> T): T {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            return runBlocking { block(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }) }
        } finally {
            runBlocking { job.cancelAndJoin() }
        }
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aChangedStyleIsThereAfterARestartAndTheRestKeepTheirDefaults() {
        withStore { store ->
            val styles = ToolStyles(BuiltInPresets.defaultStyles)
            val syncing = launch { ToolStylesStore(store).sync(styles, settleMillis = 50) }
            delay(200) // let it restore before changing anything
            styles.update(BuiltInTools.PEN) { it.copy(colorArgb = 0xFF2244CC, widthPx = 9f) }
            delay(500)
            syncing.cancelAndJoin()
        }

        val restored = ToolStyles(BuiltInPresets.defaultStyles)
        withStore { store ->
            val syncing = launch { ToolStylesStore(store).sync(restored) }
            delay(200)
            syncing.cancelAndJoin()
        }

        assertEquals(ToolStyle(0xFF2244CC, 9f), restored.styleFor(BuiltInTools.PEN))
        assertEquals(BuiltInPresets.defaultStyles.getValue(BuiltInTools.ELLIPSE), restored.styleFor(BuiltInTools.ELLIPSE))
    }

    @Test
    fun aBurstOfChangesIsSavedOnceItSettles() {
        withStore { store ->
            val styles = ToolStyles(BuiltInPresets.defaultStyles)
            val syncing = launch { ToolStylesStore(store).sync(styles, settleMillis = 300) }
            delay(200)
            for (width in 1..20) {
                styles.update(BuiltInTools.PEN) { it.copy(widthPx = width.toFloat()) }
                delay(10)
            }
            // Nothing is written while the slider is still moving.
            assertEquals(emptyMap<String, ToolStyle>(), ToolStylesStore(store).load())
            delay(600)
            assertEquals(20f, ToolStylesStore(store).load().getValue(BuiltInTools.PEN).widthPx, 0f)
            syncing.cancelAndJoin()
        }
    }
}
