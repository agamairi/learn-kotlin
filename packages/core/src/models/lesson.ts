import type { Exercise } from './exercise.ts';

/**
 * A single bite-sized lesson composed of an introductory explanation and exercises.
 */
export interface Lesson {
  id: string;
  title: string;
  explanation: string;
  exercises: Exercise[];
}
