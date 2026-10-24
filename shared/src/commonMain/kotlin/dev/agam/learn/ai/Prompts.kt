package dev.agam.learn.ai

import dev.agam.learn.model.Exercise

/** Prompt templates used by every AI tutor operation. */
internal object Prompts {
    const val SYSTEM =
        "You are a patient Kotlin tutor. Be plain, friendly, and brief. " +
            "Teach the learner without assuming advanced knowledge."

    fun explainMistake(exercise: Exercise, userAnswer: String, expectedResult: String): String =
        """
        Explain this learner's Kotlin mistake in 3 short sentences or fewer.
        Exercise: ${exerciseSummary(exercise)}
        Learner answer:
        $userAnswer
        Expected result:
        $expectedResult
        Point out the key idea and one concrete correction. Do not rewrite a whole solution.
        """.trimIndent()

    fun hint(exercise: Exercise, level: Int): String =
        """
        Give a level $level hint for this Kotlin exercise: ${exerciseSummary(exercise)}
        Level 1 is a small nudge, level 2 gives the useful approach, and level 3 is nearly
        the solution but still leaves a small step for the learner. Use at most 3 short sentences.
        Never give unrelated advice or a complete solution.
        """.trimIndent()

    fun generateCards(topic: String, count: Int): String =
        """
        Create exactly $count short Kotlin practice cards about "$topic".
        Return only strict JSON, with no Markdown fences or commentary, in this shape:
        {"cards":[{"type":"multiple_choice","prompt":"...","code":null,"options":["...","..."],"correctIndex":0,"explanation":"..."}]}
        A card type must be "multiple_choice" or "cloze". Multiple-choice cards need 2-5
        options and one correctIndex. Cloze cards need template, blanks, and each blank has
        placeholder, accepted, and optional caseSensitive. Keep explanations short.
        """.trimIndent()

    fun reviewCode(exercise: Exercise, code: String): String =
        """
        Review this passing Kotlin code for readability and idiomatic Kotlin.
        Exercise: ${exerciseSummary(exercise)}
        Code:
        $code
        Give at most 3 brief, encouraging bullets. Mention what is already good and only
        suggest changes that matter. Do not question correctness or provide a full rewrite.
        """.trimIndent()

    private fun exerciseSummary(exercise: Exercise): String = when (exercise) {
        is Exercise.MultipleChoice ->
            "${exercise.prompt}; options=${exercise.options.joinToString()}; code=${exercise.code.orEmpty()}"
        is Exercise.Cloze ->
            "${exercise.prompt}; template=${exercise.template}"
        is Exercise.PredictOutput ->
            "${exercise.prompt}; code=${exercise.code}; expected=${exercise.expectedOutput}"
        is Exercise.WriteFunction ->
            "${exercise.prompt}; starter code=${exercise.starterCode}"
        is Exercise.MultiPartProblem ->
            "${exercise.prompt}; story=${exercise.story}"
    }
}
