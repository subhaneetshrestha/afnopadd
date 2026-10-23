package io.github.subhaneetshrestha.afnopadd.shell.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.subhaneetshrestha.afnopadd.canvas.Background
import io.github.subhaneetshrestha.afnopadd.tools.FingerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Where the toolbars go: AUTO is a bottom bar on compact widths and a side rail on wider ones (app-shell spec decision 5). */
enum class ToolbarPlacement { AUTO, BOTTOM, SIDE }

/**
 * The user's settings (app-shell spec), plus [stylusSeen], the persisted flag behind finger mode
 * AUTO: once any stylus has touched the canvas on this device, a single finger pans.
 */
data class Settings(
    val fingerMode: FingerMode = FingerMode.AUTO,
    val toolbarPlacement: ToolbarPlacement = ToolbarPlacement.AUTO,
    val defaultBackground: Background = Background.PLAIN,
    val keepScreenOn: Boolean = true,
    val leftHanded: Boolean = false,
    val stylusSeen: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun update(block: (Settings) -> Settings)
}

/**
 * Settings in the app's preferences file, next to the recent colours. Like the recents, they are
 * not worth an error screen: an unreadable file reads as the defaults and a failed write is logged.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val onError: (String, IOException) -> Unit = { message, e -> Log.w(TAG, message, e) },
) : SettingsRepository {

    override val settings: Flow<Settings> = dataStore.data
        .map(SettingsPreferences::read)
        .catch { e ->
            if (e !is IOException) throw e
            onError("Could not read settings", e)
            emit(Settings())
        }
        .distinctUntilChanged()

    override suspend fun update(block: (Settings) -> Settings) {
        try {
            dataStore.edit { prefs -> SettingsPreferences.write(prefs, block(SettingsPreferences.read(prefs))) }
        } catch (e: IOException) {
            onError("Could not save settings", e)
        }
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}

/** The mapping between [Settings] and preference keys. Enums are stored by name; a name this version does not know reads as the default. */
internal object SettingsPreferences {
    private val FINGER_MODE = stringPreferencesKey("finger_mode")
    private val TOOLBAR_PLACEMENT = stringPreferencesKey("toolbar_placement")
    private val DEFAULT_BACKGROUND = stringPreferencesKey("default_background")
    private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val LEFT_HANDED = booleanPreferencesKey("left_handed")
    private val STYLUS_SEEN = booleanPreferencesKey("stylus_seen")

    private val defaults = Settings()

    fun read(prefs: Preferences) = Settings(
        fingerMode = prefs[FINGER_MODE].toEnum(defaults.fingerMode),
        toolbarPlacement = prefs[TOOLBAR_PLACEMENT].toEnum(defaults.toolbarPlacement),
        defaultBackground = prefs[DEFAULT_BACKGROUND].toEnum(defaults.defaultBackground),
        keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        leftHanded = prefs[LEFT_HANDED] ?: defaults.leftHanded,
        stylusSeen = prefs[STYLUS_SEEN] ?: defaults.stylusSeen,
    )

    fun write(prefs: MutablePreferences, settings: Settings) {
        prefs[FINGER_MODE] = settings.fingerMode.name
        prefs[TOOLBAR_PLACEMENT] = settings.toolbarPlacement.name
        prefs[DEFAULT_BACKGROUND] = settings.defaultBackground.name
        prefs[KEEP_SCREEN_ON] = settings.keepScreenOn
        prefs[LEFT_HANDED] = settings.leftHanded
        prefs[STYLUS_SEEN] = settings.stylusSeen
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
