import type { Lesson } from './lesson.ts';

/**
 * A chapter containing a group of related lessons.
 */
export interface Chapter {
  id: string;
  title: string;
  summary: string;
  lessons: Lesson[];
}
