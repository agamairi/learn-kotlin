package dev.agam.learn.content

import dev.agam.learn.model.Course

/**
 * Access layer for discovering and loading published courses.
 */
interface CourseRepository {
    /**
     * Loads a course bundle by its unique course identifier.
     */
    suspend fun getCourse(courseId: String): Course?

    /**
     * Lists all available courses.
     */
    suspend fun listCourses(): List<Course>
}
