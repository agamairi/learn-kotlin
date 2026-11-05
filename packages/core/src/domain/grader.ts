import type {
  CodePuzzle,
  ClozeExercise,
  MultipleChoiceExercise,
  PredictOutputExercise,
} from '../models/exercise.ts';

/** Per-blank result returned by the detailed cloze grader. */
export interface ClozeBlankFeedback {
  placeholder: string;
  answer: string | null;
  isCorrect: boolean;
  message: string;
}

/** Complete cloze result, including feedback in exercise blank order. */
export interface ClozeGradeResult {
  isCorrect: boolean;
  allCorrect: boolean;
  feedback: ClozeBlankFeedback[];
}

/** Per-blank result returned by code puzzle grading. */
export interface CodePuzzleBlankFeedback {
  placeholder: string;
  answer: string | null;
  isCorrect: boolean;
  message: string;
}

/** Complete code puzzle result, including feedback in blank order. */
export interface CodePuzzleGradeResult {
  isCorrect: boolean;
  allCorrect: boolean;
  feedback: CodePuzzleBlankFeedback[];
}

/**
 * Local deterministic grading engine for non-code exercises and tap-to-fill code puzzles.
 */
export interface AnswerGrader {
  gradeMultipleChoice(
    exercise: MultipleChoiceExercise,
    selectedIndex: number
  ): boolean;

  gradeCloze(
    exercise: ClozeExercise,
    answers: Record<string, string>
  ): boolean;

  gradeClozeWithFeedback(
    exercise: ClozeExercise,
    answers: Record<string, string>
  ): ClozeGradeResult;

  gradePredictOutput(
    exercise: PredictOutputExercise,
    userOutput: string
  ): boolean;

  gradeCodePuzzle(
    puzzle: CodePuzzle,
    answers: Record<string, string>
  ): boolean;

  gradeCodePuzzleWithFeedback(
    puzzle: CodePuzzle,
    answers: Record<string, string>
  ): CodePuzzleGradeResult;
}

/** Grades a multiple-choice answer by its option index. */
export class MultipleChoiceGrader {
  grade(exercise: MultipleChoiceExercise, selectedIndex: number): boolean {
    const targetIndex = exercise.correctIndex ?? exercise.correct_index;
    return (
      selectedIndex >= 0 &&
      selectedIndex === targetIndex &&
      selectedIndex < exercise.options.length
    );
  }
}

/** Grades each cloze blank independently, including case and accepted alternatives. */
export class ClozeGrader {
  grade(exercise: ClozeExercise, answers: Record<string, string>): boolean {
    return this.gradeWithFeedback(exercise, answers).isCorrect;
  }

  gradeWithFeedback(
    exercise: ClozeExercise,
    answers: Record<string, string>
  ): ClozeGradeResult {
    const feedback: ClozeBlankFeedback[] = exercise.blanks.map((blank) => {
      const raw = answers[blank.placeholder];
      const answer = raw !== undefined ? raw.trim() : null;
      const caseSensitive = blank.caseSensitive ?? blank.case_sensitive ?? true;
      const correct =
        answer !== null &&
        blank.accepted.some((accepted) =>
          caseSensitive
            ? answer === accepted.trim()
            : answer.toLowerCase() === accepted.trim().toLowerCase()
        );
      return {
        placeholder: blank.placeholder,
        answer: raw !== undefined ? raw : null,
        isCorrect: correct,
        message: correct
          ? 'Correct'
          : `Expected one of: ${blank.accepted.join(', ')}`,
      };
    });

    const isCorrect = feedback.every((f) => f.isCorrect);
    return {
      isCorrect,
      allCorrect: isCorrect,
      feedback,
    };
  }
}

/** Grades predicted output while preserving all non-trailing whitespace exactly. */
export class PredictOutputGrader {
  grade(exercise: PredictOutputExercise, userOutput: string): boolean {
    const expected = exercise.expectedOutput ?? exercise.expected_output ?? '';
    return this.normalize(expected) === this.normalize(userOutput);
  }

  normalize(output: string): string {
    return output
      .replace(/\r\n/g, '\n')
      .replace(/\r/g, '\n')
      .split('\n')
      .map((line) => line.replace(/[ \t]+$/, ''))
      .join('\n')
      .replace(/\n+$/, '');
  }
}

/** Grades each code puzzle blank independently, including case, options, and accepted alternatives. */
export class CodePuzzleGrader {
  grade(puzzle: CodePuzzle, answers: Record<string, string>): boolean {
    return this.gradeWithFeedback(puzzle, answers).isCorrect;
  }

  gradeWithFeedback(
    puzzle: CodePuzzle,
    answers: Record<string, string>
  ): CodePuzzleGradeResult {
    if (!puzzle.blanks || puzzle.blanks.length === 0) {
      return {
        isCorrect: false,
        allCorrect: false,
        feedback: [],
      };
    }

    const feedback: CodePuzzleBlankFeedback[] = puzzle.blanks.map((blank) => {
      const rawAnswer = answers[blank.placeholder];
      const trimmedAnswer = rawAnswer !== undefined ? rawAnswer.trim() : null;
      const offeredOptions = (blank.options ?? []).map((o) => o.trim());
      const isOffered =
        trimmedAnswer !== null && offeredOptions.includes(trimmedAnswer);
      const caseSensitive = blank.caseSensitive ?? blank.case_sensitive ?? true;
      const matchesAccepted =
        trimmedAnswer !== null &&
        blank.accepted.some((accepted) =>
          caseSensitive
            ? trimmedAnswer === accepted.trim()
            : trimmedAnswer.toLowerCase() === accepted.trim().toLowerCase()
        );
      const correct = Boolean(isOffered && matchesAccepted);

      return {
        placeholder: blank.placeholder,
        answer: rawAnswer !== undefined ? rawAnswer : null,
        isCorrect: correct,
        message: correct
          ? 'Correct'
          : `Expected one of: ${blank.accepted.join(', ')}`,
      };
    });

    const isCorrect = feedback.length > 0 && feedback.every((f) => f.isCorrect);
    return {
      isCorrect,
      allCorrect: isCorrect,
      feedback,
    };
  }
}

/**
 * Deterministic graders for multiple-choice, cloze, predict-output, and tap-to-fill code puzzles.
 */
export class DefaultAnswerGrader implements AnswerGrader {
  private readonly multipleChoice = new MultipleChoiceGrader();
  private readonly cloze = new ClozeGrader();
  private readonly predictOutput = new PredictOutputGrader();
  private readonly codePuzzle = new CodePuzzleGrader();

  gradeMultipleChoice(
    exercise: MultipleChoiceExercise,
    selectedIndex: number
  ): boolean {
    return this.multipleChoice.grade(exercise, selectedIndex);
  }

  gradeCloze(
    exercise: ClozeExercise,
    answers: Record<string, string>
  ): boolean {
    return this.cloze.grade(exercise, answers);
  }

  gradeClozeWithFeedback(
    exercise: ClozeExercise,
    answers: Record<string, string>
  ): ClozeGradeResult {
    return this.cloze.gradeWithFeedback(exercise, answers);
  }

  gradePredictOutput(
    exercise: PredictOutputExercise,
    userOutput: string
  ): boolean {
    return this.predictOutput.grade(exercise, userOutput);
  }

  gradeCodePuzzle(
    puzzle: CodePuzzle,
    answers: Record<string, string>
  ): boolean {
    return this.codePuzzle.grade(puzzle, answers);
  }

  gradeCodePuzzleWithFeedback(
    puzzle: CodePuzzle,
    answers: Record<string, string>
  ): CodePuzzleGradeResult {
    return this.codePuzzle.gradeWithFeedback(puzzle, answers);
  }
}
