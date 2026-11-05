import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  DefaultAnswerGrader,
  MultipleChoiceGrader,
  ClozeGrader,
  PredictOutputGrader,
  CodePuzzleGrader,
} from '../src/domain/grader.ts';
import type {
  MultipleChoiceExercise,
  ClozeExercise,
  PredictOutputExercise,
  CodePuzzle,
} from '../src/models/exercise.ts';

describe('AnswerGrader', () => {
  const grader = new DefaultAnswerGrader();

  describe('MultipleChoice', () => {
    const exercise: MultipleChoiceExercise = {
      type: 'multiple_choice',
      id: 'mc-1',
      prompt: 'Pick correct answer',
      options: ['Alpha', 'Beta', 'Gamma'],
      correctIndex: 1,
      explanation: 'Beta is correct',
    };

    test('accepts correct index', () => {
      assert.equal(grader.gradeMultipleChoice(exercise, 1), true);
    });

    test('rejects wrong index', () => {
      assert.equal(grader.gradeMultipleChoice(exercise, 0), false);
      assert.equal(grader.gradeMultipleChoice(exercise, 2), false);
    });

    test('rejects out of bounds index', () => {
      assert.equal(grader.gradeMultipleChoice(exercise, -1), false);
      assert.equal(grader.gradeMultipleChoice(exercise, 3), false);
    });
  });

  describe('Cloze', () => {
    const exercise: ClozeExercise = {
      type: 'cloze',
      id: 'cloze-1',
      prompt: 'Fill in the blanks',
      template: '____1 name = "Koda"; ____2(name)',
      blanks: [
        {
          placeholder: '____1',
          accepted: ['val'],
          caseSensitive: true,
        },
        {
          placeholder: '____2',
          accepted: ['println', 'print'],
          caseSensitive: false,
        },
      ],
      explanation: 'val is immutable; println prints',
    };

    test('grades correctly and provides feedback for all blanks', () => {
      const result = grader.gradeClozeWithFeedback(exercise, {
        '____1': '  val  ',
        '____2': 'PRINTLN',
      });
      assert.equal(result.isCorrect, true);
      assert.equal(result.allCorrect, true);
      assert.equal(result.feedback.length, 2);
      assert.equal(result.feedback[0].isCorrect, true);
      assert.equal(result.feedback[1].isCorrect, true);
      assert.equal(result.feedback[0].message, 'Correct');
    });

    test('fails on case sensitivity when required', () => {
      const result = grader.gradeClozeWithFeedback(exercise, {
        '____1': 'VAL',
        '____2': 'println',
      });
      assert.equal(result.isCorrect, false);
      assert.equal(result.feedback[0].isCorrect, false);
      assert.match(result.feedback[0].message, /Expected one of: val/);
      assert.equal(result.feedback[1].isCorrect, true);
    });

    test('fails when blanks are missing', () => {
      assert.equal(grader.gradeCloze(exercise, { '____1': 'val' }), false);
    });
  });

  describe('PredictOutput', () => {
    const exercise: PredictOutputExercise = {
      type: 'predict_output',
      id: 'pred-1',
      prompt: 'What is printed?',
      code: 'println("line 1\\nline 2")',
      expectedOutput: 'line 1\nline 2',
      explanation: 'Two lines are printed',
    };

    test('normalizes windows newlines and trailing whitespace', () => {
      assert.equal(grader.gradePredictOutput(exercise, 'line 1  \r\nline 2\t\n\n'), true);
    });

    test('preserves leading indentation', () => {
      assert.equal(grader.gradePredictOutput(exercise, ' line 1\nline 2'), false);
      assert.equal(grader.gradePredictOutput(exercise, 'line 1\n line 2'), false);
    });
  });

  describe('Guided Code (CodePuzzle)', () => {
    const puzzle: CodePuzzle = {
      template: 'fun double(x: Int): Int = ____1\nfun add(a: Int, b: Int): Int = ____2',
      blanks: [
        {
          placeholder: '____1',
          accepted: ['x * 2'],
          options: ['x * 2', 'x + 2', 'x / 2', 'x * x'],
        },
        {
          placeholder: '____2',
          accepted: ['a + b'],
          options: ['a + b', 'a - b', 'a * b', 'b - a'],
        },
      ],
      explanation: 'Simple arithmetic',
    };

    test('grades all blanks with feedback', () => {
      const pass = grader.gradeCodePuzzleWithFeedback(puzzle, {
        '____1': 'x * 2',
        '____2': 'a + b',
      });
      assert.equal(pass.isCorrect, true);
      assert.equal(pass.feedback.length, 2);
      assert.equal(pass.feedback.every((f) => f.isCorrect), true);
    });

    test('rejects answers not offered in options even if in accepted list', () => {
      const unofferedPuzzle: CodePuzzle = {
        template: 'val result = ____1',
        blanks: [
          {
            placeholder: '____1',
            accepted: ['a + b', 'b + a'],
            options: ['a + b', 'a - b', 'a * b'], // "b + a" not offered
          },
        ],
      };

      const unoffered = grader.gradeCodePuzzleWithFeedback(unofferedPuzzle, {
        '____1': 'b + a',
      });
      assert.equal(unoffered.isCorrect, false);
      assert.equal(unoffered.feedback[0].isCorrect, false);

      const offered = grader.gradeCodePuzzleWithFeedback(unofferedPuzzle, {
        '____1': '  a + b  ',
      });
      assert.equal(offered.isCorrect, true);
      assert.equal(offered.feedback[0].isCorrect, true);
    });

    test('rejects empty puzzles with no blanks', () => {
      const emptyPuzzle: CodePuzzle = {
        template: 'val x = 1',
        blanks: [],
      };
      assert.equal(grader.gradeCodePuzzle(emptyPuzzle, {}), false);
      const res = grader.gradeCodePuzzleWithFeedback(emptyPuzzle, {});
      assert.equal(res.isCorrect, false);
      assert.equal(res.feedback.length, 0);
    });

    test('respects case sensitivity configuration', () => {
      const sensitivePuzzle: CodePuzzle = {
        template: 'val x: ____1 = 1L',
        blanks: [
          {
            placeholder: '____1',
            accepted: ['Long'],
            caseSensitive: true,
            options: ['Long', 'long', 'Int'],
          },
        ],
      };
      assert.equal(grader.gradeCodePuzzle(sensitivePuzzle, { '____1': 'Long' }), true);
      assert.equal(grader.gradeCodePuzzle(sensitivePuzzle, { '____1': 'long' }), false);

      const insensitivePuzzle: CodePuzzle = {
        template: 'val x: ____1 = 1L',
        blanks: [
          {
            placeholder: '____1',
            accepted: ['Long'],
            caseSensitive: false,
            options: ['Long', 'long', 'Int'],
          },
        ],
      };
      assert.equal(grader.gradeCodePuzzle(insensitivePuzzle, { '____1': 'long' }), true);
    });
  });
});
