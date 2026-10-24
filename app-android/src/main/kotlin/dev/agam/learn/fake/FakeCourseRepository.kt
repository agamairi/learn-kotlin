package dev.agam.learn.fake

import android.content.Context
import dev.agam.learn.content.CourseLoader
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.model.Course

/**
 * In-memory implementation of [CourseRepository] for development and testing.
 * Loads courses from Android assets (e.g. assets/courses directory) when available,
 * falling back to [SampleCourseData.defaultCourse].
 */
class FakeCourseRepository(
    initialCourses: List<Course> = emptyList()
) : CourseRepository {

    private val coursesMap = mutableMapOf<String, Course>()

    init {
        for (course in initialCourses) {
            coursesMap[course.id] = course
        }
    }

    override suspend fun getCourse(courseId: String): Course? {
        return coursesMap[courseId]
    }

    override suspend fun listCourses(): List<Course> {
        return coursesMap.values.toList()
    }

    fun addCourse(course: Course) {
        coursesMap[course.id] = course
    }

    companion object {
        /**
         * Attempts to discover and deserialize course JSON bundles in Android assets.
         * If no files are found or an error occurs, falls back to [SampleCourseData.defaultCourse].
         */
        fun fromAssetsOrSample(
            context: Context? = null,
            courseLoader: CourseLoader = CourseLoader()
        ): FakeCourseRepository {
            val courses = mutableListOf<Course>()
            if (context != null) {
                try {
                    val assetManager = context.assets
                    val files = assetManager.list("courses") ?: emptyArray()
                    for (file in files) {
                        if (file.endsWith(".json")) {
                            val json = assetManager.open("courses/$file").bufferedReader().use { it.readText() }
                            val course = courseLoader.decodeCourse(json)
                            courses.add(course)
                        }
                    }
                } catch (e: Exception) {
                    // Ignored: fallback to sample course
                }
            }
            if (courses.isEmpty()) {
                courses.add(SampleCourseData.defaultCourse)
            }
            return FakeCourseRepository(courses)
        }
    }
}
