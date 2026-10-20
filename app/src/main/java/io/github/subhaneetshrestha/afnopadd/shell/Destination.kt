package io.github.subhaneetshrestha.afnopadd.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Keys of the navigation back stack. Each one is serialisable so the stack survives rotation and
 * process death through [androidx.navigation3.runtime.rememberNavBackStack].
 */
@Serializable
sealed interface Destination : NavKey {

    @Serializable
    data object Library : Destination

    @Serializable
    data class Editor(val sheetId: String) : Destination
}
