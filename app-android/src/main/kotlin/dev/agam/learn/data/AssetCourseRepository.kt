package dev.agam.learn.data

import android.content.Context
import dev.agam.learn.content.CourseLoader
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.model.Course

/**
 * Android assets-backed implementation of [CourseRepository] that loads course bundles
 * (such as kotlin-stripe.json) via the shared [CourseLoader].
 */
class AssetCourseRepository(
    private val context: Context,
    private val courseLoader: CourseLoader = CourseLoader()
) : CourseRepository {

    private val coursesMap: Map<String, Course> by lazy {
        val map = mutableMapOf<String, Course>()
        val files = runCatching { context.assets.list("courses") }.getOrNull() ?: emptyArray()
        for (file in files) {
            if (file.endsWith(".json")) {
                try {
                    val json = context.assets.open("courses/$file").bufferedReader().use { it.readText() }
                    val course = courseLoader.decodeCourse(json)
                    map[course.id] = course
                } catch (e: Exception) {
                    // Skip invalid bundles
                }
            }
        }
        map
    }

    override suspend fun getCourse(courseId: String): Course? {
        return coursesMap[courseId]
    }

    override suspend fun listCourses(): List<Course> {
        return coursesMap.values.toList()
    }
}
