package io.github.subhaneetshrestha.afnopadd.shell

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.preferencesDataStore
import io.github.subhaneetshrestha.afnopadd.tools.color.RecentColorsStore
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.Renderer
import io.github.subhaneetshrestha.afnopadd.canvas.SoftwareRenderer
import io.github.subhaneetshrestha.afnopadd.document.SheetStore
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import io.github.subhaneetshrestha.afnopadd.library.SheetLibrary
import io.github.subhaneetshrestha.afnopadd.tools.ToolStyles
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.library.internal.RoomSheetLibrary
import io.github.subhaneetshrestha.afnopadd.library.internal.openLibraryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Manual dependency container created once per process by [AfnopaddApp]. Composables reach it
 * through [LocalAppContainer] instead of constructing dependencies themselves.
 */
class AppContainer(val applicationContext: Context) {

    /** Process-wide scope for work that must outlive a screen, such as closing a sheet. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sheetStore: SheetStore by lazy { RoomSheetStore(applicationContext) }

    val renderer: Renderer by lazy { SoftwareRenderer() }

    val thumbnails: ThumbnailWriter by lazy { ThumbnailWriter(renderer) }

    /** Per-tool colour and width, kept for the whole session so they carry across sheets. */
    val toolStyles: ToolStyles = ToolStyles(BuiltInPresets.defaultStyles)

    val recentColors: RecentColorsStore by lazy { RecentColorsStore(applicationContext.settingsDataStore) }

    val library: SheetLibrary by lazy {
        RoomSheetLibrary(
            store = sheetStore,
            database = openLibraryDatabase(applicationContext, File(applicationContext.filesDir, "library.db")),
            root = sheetsRoot(applicationContext),
            trash = trashRoot(applicationContext),
            defaultTitlePrefix = applicationContext.getString(R.string.sheet_default_title_prefix),
            copyTitle = { title -> applicationContext.getString(R.string.sheet_copy_title, title) },
        )
    }
}

/** The app's single preferences file (recent colours now; settings in Task 19). */
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided; wrap the content in CompositionLocalProvider(LocalAppContainer provides ...)")
}
