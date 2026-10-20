package io.github.subhaneetshrestha.afnopadd.shell

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Manual dependency container created once per process by [AfnopaddApp]. Services are added here
 * as their modules land; composables reach it through [LocalAppContainer] instead of constructing
 * dependencies themselves.
 */
class AppContainer(val applicationContext: Context)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided; wrap the content in CompositionLocalProvider(LocalAppContainer provides ...)")
}
