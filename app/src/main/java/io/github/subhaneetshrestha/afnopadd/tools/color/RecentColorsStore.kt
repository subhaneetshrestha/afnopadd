package io.github.subhaneetshrestha.afnopadd.tools.color

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The eight most recent colours, kept in the app's preferences so they survive restarts. */
class RecentColorsStore(private val dataStore: DataStore<Preferences>) {

    val colors: Flow<List<Long>> = dataStore.data.map { prefs -> prefs[KEY]?.let(RecentColors::decode).orEmpty() }

    suspend fun add(color: Long) {
        dataStore.edit { prefs ->
            prefs[KEY] = RecentColors.encode(RecentColors.add(prefs[KEY]?.let(RecentColors::decode).orEmpty(), color))
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("recent_colors")
    }
}
