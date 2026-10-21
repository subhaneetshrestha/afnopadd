package io.github.subhaneetshrestha.afnopadd.tools

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

/** A tool's colour and width; width is in screen pixels at the moment of drawing. */
data class ToolStyle(val colorArgb: Long, val widthPx: Float)

/**
 * Per-tool styles, so switching from pen to highlighter and back restores each tool's choices.
 * Held in memory so tools read them synchronously; [ToolStylesStore] keeps them across restarts.
 */
class ToolStyles(private val defaults: Map<String, ToolStyle>) {

    private val _styles = MutableStateFlow(defaults)
    val styles: StateFlow<Map<String, ToolStyle>> = _styles.asStateFlow()

    fun styleFor(toolId: String): ToolStyle = _styles.value[toolId] ?: FALLBACK

    fun update(toolId: String, transform: (ToolStyle) -> ToolStyle) {
        _styles.update { current ->
            val next = transform(current[toolId] ?: FALLBACK)
            current + (toolId to next.copy(widthPx = clampWidth(next.widthPx)))
        }
    }

    /** Lays styles saved by an earlier session over the current ones. */
    fun restore(saved: Map<String, ToolStyle>) {
        _styles.update { current -> current + saved }
    }

    fun reset() {
        _styles.value = defaults
    }

    companion object {
        const val MIN_WIDTH_PX = 0.5f
        const val MAX_WIDTH_PX = 64f
        val WIDTH_PRESETS_PX = listOf(1f, 2f, 4f, 8f, 16f)
        val FALLBACK = ToolStyle(colorArgb = 0xFF000000, widthPx = 4f)

        fun clampWidth(widthPx: Float): Float = if (widthPx.isFinite()) widthPx.coerceIn(MIN_WIDTH_PX, MAX_WIDTH_PX) else FALLBACK.widthPx
    }
}

/** The stored form of [ToolStyles]: a JSON object from tool id to colour and width. */
internal object ToolStylesCodec {

    @Serializable
    private data class Stored(val colorArgb: Long, val widthPx: Float)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Stored.serializer())

    fun encode(styles: Map<String, ToolStyle>): String =
        json.encodeToString(serializer, styles.mapValues { (_, style) -> Stored(style.colorArgb, style.widthPx) })

    /** Whatever cannot be read restores nothing; the defaults then stand. */
    fun decode(text: String): Map<String, ToolStyle> = try {
        json.decodeFromString(serializer, text).mapValues { (_, stored) -> ToolStyle(stored.colorArgb, ToolStyles.clampWidth(stored.widthPx)) }
    } catch (e: IllegalArgumentException) { // SerializationException is one
        emptyMap()
    }
}

/**
 * Keeps [ToolStyles] in the app's preferences file. Styles are a convenience like the recent
 * colours: a read or write failure is logged and the session carries on with what it has.
 */
class ToolStylesStore(
    private val dataStore: DataStore<Preferences>,
    private val onError: (String, IOException) -> Unit = { message, e -> Log.w(TAG, message, e) },
) {
    suspend fun load(): Map<String, ToolStyle> = try {
        dataStore.data.first()[KEY]?.let(ToolStylesCodec::decode).orEmpty()
    } catch (e: IOException) {
        onError("Could not read tool styles", e)
        emptyMap()
    }

    suspend fun save(styles: Map<String, ToolStyle>) {
        try {
            dataStore.edit { it[KEY] = ToolStylesCodec.encode(styles) }
        } catch (e: IOException) {
            onError("Could not save tool styles", e)
        }
    }

    /**
     * Restores the saved styles into [styles], then saves every later change once it has settled
     * for [settleMillis] (a width slider drag is one save, not one per frame). Runs until cancelled.
     */
    suspend fun sync(styles: ToolStyles, settleMillis: Long = 300L) {
        styles.restore(load())
        styles.styles.drop(1).collectLatest { current ->
            delay(settleMillis)
            save(current)
        }
    }

    private companion object {
        const val TAG = "ToolStylesStore"
        val KEY = stringPreferencesKey("tool_styles")
    }
}
