package io.github.subhaneetshrestha.afnopadd.shell

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.subhaneetshrestha.afnopadd.document.SheetStore
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container created once per process by [AfnopaddApp]. Services are added here
 * as their modules land; composables reach it through [LocalAppContainer] instead of constructing
 * dependencies themselves.
 */
class AppContainer(val applicationContext: Context) {

    /** Process-wide scope for work that must outlive a screen, such as closing a sheet. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sheetStore: SheetStore by lazy { RoomSheetStore(applicationContext) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided; wrap the content in CompositionLocalProvider(LocalAppContainer provides ...)")
}
