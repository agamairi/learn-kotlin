package dev.agam.learn.fake

import dev.agam.learn.content.CourseRepository
import dev.agam.learn.model.Course

/** In-memory implementation of [CourseRepository] for unit tests. Courses are supplied explicitly. */
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
}
