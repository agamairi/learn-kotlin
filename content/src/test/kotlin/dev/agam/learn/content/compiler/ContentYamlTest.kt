package dev.agam.learn.content.compiler

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentYamlTest {

    @Test
    fun everyScalarStaysAStringSoTrickyOptionTextSurvives() {
        val root = ContentYaml.parse(
            """
            options:
              - "0"
              - true
              - null
              - 3.5
              - ~
              - 1_000
            correct_index: 0
            empty_field:
            """.trimIndent()
        )

        val fields = assertIs<JsonObject>(root)
        val options = fields.getValue("options").jsonArray.map { assertIs<JsonPrimitive>(it) }
        assertEquals(listOf("0", "true", "null", "3.5", "~", "1_000"), options.map { it.content })
        assertTrue(options.all { it.isString }, "every option must be a string, not a YAML number, boolean or null")
        assertEquals("0", fields.getValue("correct_index").jsonPrimitive.content)
        assertEquals("", fields.getValue("empty_field").jsonPrimitive.content)
    }

    @Test
    fun emptyDocumentIsJsonNull() {
        assertEquals(JsonNull, ContentYaml.parse(""))
    }

    @Test
    fun repeatedMappingKeysAreRejected() {
        assertFailsWith<YamlEngineException> {
            ContentYaml.parse("id: first\nid: second")
        }
    }
}
