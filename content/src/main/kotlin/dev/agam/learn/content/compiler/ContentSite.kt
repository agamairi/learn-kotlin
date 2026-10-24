package dev.agam.learn.content.compiler

/**
 * The place a rule runs: the source [file], the exercise it belongs to when there is one, and the [log] that receives
 * its findings. Rules pass the JSON path of the node they check, so every message points at the exact field.
 */
internal class ContentSite(
    private val log: ContentIssueLog,
    val file: String,
    val exerciseId: String? = null
) {

    fun reportError(path: String, message: String) {
        log.error(file, path, message, exerciseId)
    }

    fun reportWarning(path: String, message: String) {
        log.warning(file, path, message, exerciseId)
    }

    /** Reports a blank [value]. [label] names the field in the message, for example `explanation`. */
    fun requireText(path: String, value: String, label: String) {
        if (value.isBlank()) reportError(path, "$label cannot be blank")
    }

    /** Like [requireText], but only checks a field that the schema allows to be absent. */
    fun requireOptionalText(path: String, value: String?, label: String) {
        if (value != null) requireText(path, value, label)
    }

    /** Checks a hints list: at most [MAX_HINTS] entries, none of them blank. */
    fun requireHints(path: String, hints: List<String>) {
        if (hints.size > MAX_HINTS) {
            reportError(path, "has ${hints.size} hints; use at most $MAX_HINTS, in increasing order of help")
        }
        hints.forEachIndexed { index, hint -> requireText("$path[$index]", hint, "hint") }
    }

    companion object {
        /** The schema allows 0 to 3 hints on a write-function exercise or a multi-part problem part. */
        const val MAX_HINTS = 3
    }
}
