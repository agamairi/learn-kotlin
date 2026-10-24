package dev.agam.learn.content.compiler

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.snakeyaml.engine.v2.api.ConstructNode
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.schema.FailsafeSchema

/**
 * Reads YAML content into kotlinx [JsonElement] trees using the YAML failsafe schema, so every scalar arrives as a
 * string. Authors can write `0`, `true`, `null` or `3.5` as plain option text without YAML turning them into numbers,
 * booleans or nulls. Typed fields such as `correct_index` and `case_sensitive` are converted when the tree is decoded.
 */
internal object ContentYaml {

    private val loader = Load(
        LoadSettings.builder()
            .setSchema(TextOnlySchema())
            .setAllowDuplicateKeys(false)
            .build()
    )

    /**
     * The failsafe schema plus one constructor. A key with no value (`code:`) is tagged as null, and the failsafe
     * schema has no constructor for that tag, so without this every blank field would stop the parser. The constructor
     * keeps the raw text instead, which is empty, and validation then reports the field as blank. An empty document has
     * no node at all, so it constructs to null.
     */
    private class TextOnlySchema : FailsafeSchema() {
        override fun getSchemaTagConstructors(): Map<Tag, ConstructNode> =
            super.getSchemaTagConstructors() + (Tag.NULL to ConstructNode { node -> (node as? ScalarNode)?.value })
    }

    /**
     * Parses one YAML document. An empty document yields [JsonNull].
     *
     * @throws org.snakeyaml.engine.v2.exceptions.YamlEngineException for malformed YAML or a repeated mapping key
     */
    fun parse(text: String): JsonElement = loader.loadFromString(text).toJsonElement()

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
        is List<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
}
