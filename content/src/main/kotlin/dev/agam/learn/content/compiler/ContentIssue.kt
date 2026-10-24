package dev.agam.learn.content.compiler

/**
 * How serious a content problem is. Errors fail the build; warnings are printed and the build still passes.
 */
enum class Severity { ERROR, WARNING }

/**
 * One problem found in the content sources.
 *
 * @property file source file relative to the content folder, for example `kotlin/chapters/ch01-syntax.yaml`
 * @property path JSON path inside that file, for example `$.lessons[0].exercises[2].correct_index`
 * @property exerciseId id of the exercise the problem belongs to, when it is inside one
 */
data class ContentIssue(
    val severity: Severity,
    val file: String,
    val path: String,
    val message: String,
    val exerciseId: String? = null
) {
    /** One console line, for example `kotlin/course.yaml: ERROR at $.id: id cannot be blank`. */
    fun format(): String {
        val where = if (exerciseId == null) path else "$path (exercise '$exerciseId')"
        return "$file: ${severity.name} at $where: $message"
    }
}

/**
 * Collects issues from every stage, so a single run reports all of them instead of stopping at the first.
 */
class ContentIssueLog {
    private val entries = mutableListOf<ContentIssue>()

    /** Every issue recorded so far, in the order they were found. */
    val issues: List<ContentIssue>
        get() = entries.toList()

    /** True when at least one error (not only warnings) was recorded. */
    val hasErrors: Boolean
        get() = entries.any { it.severity == Severity.ERROR }

    /** Records an error that fails the build. */
    fun error(file: String, path: String, message: String, exerciseId: String? = null) {
        entries += ContentIssue(Severity.ERROR, file, path, message, exerciseId)
    }

    /** Records a warning that is reported but does not fail the build. */
    fun warning(file: String, path: String, message: String, exerciseId: String? = null) {
        entries += ContentIssue(Severity.WARNING, file, path, message, exerciseId)
    }
}
