package io.github.subhaneetshrestha.afnopadd.document.internal.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.document.SHEET_SCHEMA_VERSION
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migration harness. Room exports one JSON file per schema version into `app/schemas`, and the
 * build ships that directory as a test asset so future migration tests can replay old versions.
 * Until schema version 2 exists there is nothing to migrate; this test only proves the harness is wired.
 */
@RunWith(AndroidJUnit4::class)
class SheetDatabaseSchemaTest {

    @Test
    fun exportedSchemaForTheCurrentVersionIsAvailableToTests() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schemaFiles = assets.list(SheetDatabase::class.java.name).orEmpty().toList()

        assertTrue("found $schemaFiles", "$SHEET_SCHEMA_VERSION.json" in schemaFiles)
    }
}
