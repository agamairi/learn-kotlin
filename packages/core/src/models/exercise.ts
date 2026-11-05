/**
 * Exercise model hierarchy for learn-kotlin.
 * Exercises are polymorphic discriminated unions on the `type` property.
 */

export interface Blank {
  placeholder: string;
  accepted: string[];
  caseSensitive?: boolean;
  case_sensitive?: boolean;
  options?: string[];
}

export interface CodePuzzle {
  template: string;
  blanks: Blank[];
  explanation?: string | null;
}

export interface ProblemPart {
  id: string;
  prompt: string;
  starterCode?: string | null;
  starter_code?: string | null;
  hiddenTests: string;
  hidden_tests?: string;
  referenceSolution: string;
  reference_solution?: string;
  expected: string;
  hints?: string[];
  guided?: CodePuzzle | null;
}

export interface FaqItem {
  question: string;
  answer: string;
}

export type ExerciseType =
  | 'multiple_choice'
  | 'cloze'
  | 'predict_output'
  | 'write_function'
  | 'multi_part_problem';

export interface BaseExercise {
  id: string;
  prompt: string;
  type: ExerciseType;
}

export interface MultipleChoiceExercise extends BaseExercise {
  type: 'multiple_choice';
  code?: string | null;
  options: string[];
  correctIndex: number;
  correct_index?: number;
  explanation: string;
  aiGenerated?: boolean;
  ai_generated?: boolean;
}

export interface ClozeExercise extends BaseExercise {
  type: 'cloze';
  template: string;
  blanks: Blank[];
  explanation: string;
  aiGenerated?: boolean;
  ai_generated?: boolean;
}

export interface PredictOutputExercise extends BaseExercise {
  type: 'predict_output';
  code: string;
  expectedOutput: string;
  expected_output?: string;
  explanation: string;
}

export interface WriteFunctionExercise extends BaseExercise {
  type: 'write_function';
  starterCode: string;
  starter_code?: string;
  hiddenTests: string;
  hidden_tests?: string;
  referenceSolution: string;
  reference_solution?: string;
  hints?: string[];
  explanation?: string | null;
  guided?: CodePuzzle | null;
}

export interface MultiPartProblemExercise extends BaseExercise {
  type: 'multi_part_problem';
  story: string;
  parts: ProblemPart[];
  faq?: FaqItem[];
  discussion?: string | null;
  sourceNote?: string | null;
  source_note?: string | null;
}

export type Exercise =
  | MultipleChoiceExercise
  | ClozeExercise
  | PredictOutputExercise
  | WriteFunctionExercise
  | MultiPartProblemExercise;

export function isMultipleChoice(exercise: Exercise): exercise is MultipleChoiceExercise {
  return exercise.type === 'multiple_choice';
}

export function isCloze(exercise: Exercise): exercise is ClozeExercise {
  return exercise.type === 'cloze';
}

export function isPredictOutput(exercise: Exercise): exercise is PredictOutputExercise {
  return exercise.type === 'predict_output';
}

export function isWriteFunction(exercise: Exercise): exercise is WriteFunctionExercise {
  return exercise.type === 'write_function';
}

export function isMultiPartProblem(exercise: Exercise): exercise is MultiPartProblemExercise {
  return exercise.type === 'multi_part_problem';
}
