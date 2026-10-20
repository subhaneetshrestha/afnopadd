package io.github.subhaneetshrestha.afnopadd.tools.color

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The eight most recent colours, kept in the app's preferences so they survive restarts. They are a
 * convenience, so an unreadable or unwritable settings file degrades to "no recents" instead of an error.
 */
class RecentColorsStore(
    private val dataStore: DataStore<Preferences>,
    private val onError: (String, IOException) -> Unit = { message, e -> Log.w(TAG, message, e) },
) {

    val colors: Flow<List<Long>> = dataStore.data
        .map { prefs -> prefs[KEY]?.let(RecentColors::decode).orEmpty() }
        .catch { e ->
            if (e !is IOException) throw e
            onError("Could not read recent colours", e)
            emit(emptyList())
        }

    suspend fun add(color: Long) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY] = RecentColors.encode(RecentColors.add(prefs[KEY]?.let(RecentColors::decode).orEmpty(), color))
            }
        } catch (e: IOException) {
            onError("Could not save recent colour", e)
        }
    }

    private companion object {
        const val TAG = "RecentColorsStore"
        val KEY = stringPreferencesKey("recent_colors")
    }
}
