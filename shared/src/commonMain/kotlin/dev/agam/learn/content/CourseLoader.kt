package dev.agam.learn.content

import dev.agam.learn.model.Course
import kotlinx.serialization.json.Json

/**
 * Decodes course JSON bundles into strongly-typed @Serializable models.
 */
class CourseLoader(
    val json: Json = DefaultJson
) {
    companion object {
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = false
            classDiscriminator = "type"
        }
    }

    /**
     * Decodes a course JSON string into a [Course] instance.
     */
    fun decodeCourse(jsonString: String): Course {
        return json.decodeFromString(Course.serializer(), jsonString)
    }
}
