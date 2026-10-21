package io.github.subhaneetshrestha.afnopadd.tools

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

enum class Placement { TOP, BOTTOM, LEFT, RIGHT }

/** A declarative toolbar layout: which tools, presets and commands appear, grouped and ordered. */
data class ToolbarSpec(
    val schemaVersion: Int,
    val source: String,
    val toolbars: List<Toolbar>,
)

data class Toolbar(
    val id: String,
    val placement: Placement,
    val items: List<ToolbarItem>,
)

sealed interface ToolbarItem {
    /** A tool (`tool:`), preset (`preset:`) or command (`cmd:`) by id; unknown ids render nothing. */
    data class Ref(val id: String) : ToolbarItem

    data object Separator : ToolbarItem

    data class Group(val id: String, val label: String, val items: List<ToolbarItem>) : ToolbarItem
}

/** The layout file could not be understood: expected for hand-written or newer files, never a crash. */
class ToolbarSpecException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * JSON form of [ToolbarSpec] (schema version 1). Unknown keys and unknown item shapes are ignored
 * so a layout from a newer app still loads; a newer `schemaVersion` is refused outright.
 */
object ToolbarSpecJson {

    const val SCHEMA_VERSION = 1

    // Layouts will be imported from users and plugins, so the parser bounds everything it reads.
    /** Longest layout file accepted, in characters; the built-in layout is under 1,000. */
    const val MAX_LAYOUT_CHARS = 64 * 1024
    /** Deepest group nesting accepted; a toolbar is a row with folders in it, not a tree. */
    const val MAX_GROUP_DEPTH = 4
    /** Most items accepted in one toolbar or group. */
    const val MAX_ITEMS = 128
    /** Longest id or label accepted. */
    const val MAX_ID_LENGTH = 128

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ToolbarSpec {
        if (text.length > MAX_LAYOUT_CHARS) throw ToolbarSpecException("Layout is larger than $MAX_LAYOUT_CHARS characters")
        val root = try {
            json.parseToJsonElement(text) as? JsonObject ?: throw ToolbarSpecException("Layout must be a JSON object")
        } catch (e: SerializationException) {
            throw ToolbarSpecException("Layout is not valid JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ToolbarSpecException("Layout is not valid JSON: ${e.message}", e)
        }
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: throw ToolbarSpecException("Layout is missing a numeric 'schemaVersion'")
        if (schemaVersion < 1) throw ToolbarSpecException("Layout schema version $schemaVersion is not valid")
        if (schemaVersion > SCHEMA_VERSION) {
            throw ToolbarSpecException("Layout schema version $schemaVersion is newer than this app supports ($SCHEMA_VERSION)")
        }
        val toolbars = root["toolbars"] as? JsonArray ?: throw ToolbarSpecException("Layout is missing 'toolbars'")
        return ToolbarSpec(
            schemaVersion = schemaVersion,
            source = root.string("source") ?: "unknown",
            toolbars = toolbars.mapNotNull { it as? JsonObject }.map { toolbar ->
                Toolbar(
                    id = toolbar.boundedString("id") ?: throw ToolbarSpecException("A toolbar is missing 'id'"),
                    placement = toolbar.string("placement")?.let { name ->
                        Placement.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    } ?: Placement.BOTTOM,
                    items = parseItems(toolbar["items"], depth = 0),
                )
            },
        )
    }

    fun encode(spec: ToolbarSpec): String = buildJsonObject {
        put("schemaVersion", spec.schemaVersion)
        put("source", spec.source)
        put(
            "toolbars",
            buildJsonArray {
                spec.toolbars.forEach { toolbar ->
                    add(
                        buildJsonObject {
                            put("id", toolbar.id)
                            put("placement", toolbar.placement.name.lowercase())
                            put("items", encodeItems(toolbar.items))
                        },
                    )
                }
            },
        )
    }.toString()

    /** [depth] counts the groups around these items; deeper nesting is refused rather than recursed into. */
    private fun parseItems(element: JsonElement?, depth: Int): List<ToolbarItem> {
        val array = element as? JsonArray ?: return emptyList()
        if (array.size > MAX_ITEMS) throw ToolbarSpecException("A toolbar or group has more than $MAX_ITEMS items")
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val ref = obj.boundedString("ref")
            val group = obj["group"] as? JsonObject
            when {
                ref != null -> ToolbarItem.Ref(ref)
                (obj["separator"] as? JsonPrimitive)?.booleanOrNull == true -> ToolbarItem.Separator
                group != null -> {
                    if (depth >= MAX_GROUP_DEPTH) throw ToolbarSpecException("Groups are nested more than $MAX_GROUP_DEPTH deep")
                    ToolbarItem.Group(
                        id = group.boundedString("id") ?: return@mapNotNull null,
                        label = group.boundedString("label") ?: "",
                        items = parseItems(group["items"], depth + 1),
                    )
                }
                else -> null
            }
        }
    }

    private fun JsonObject.boundedString(key: String): String? = string(key)?.also {
        if (it.length > MAX_ID_LENGTH) throw ToolbarSpecException("'$key' is longer than $MAX_ID_LENGTH characters")
    }

    private fun encodeItems(items: List<ToolbarItem>): JsonArray = buildJsonArray {
        items.forEach { item ->
            add(
                when (item) {
                    is ToolbarItem.Ref -> buildJsonObject { put("ref", item.id) }
                    ToolbarItem.Separator -> buildJsonObject { put("separator", true) }
                    is ToolbarItem.Group -> buildJsonObject {
                        put(
                            "group",
                            buildJsonObject {
                                put("id", item.id)
                                put("label", item.label)
                                put("items", encodeItems(item.items))
                            },
                        )
                    }
                },
            )
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
