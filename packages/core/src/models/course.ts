import type { Chapter } from './chapter.ts';

export type LanguageId = 'kotlin' | string;

/**
 * Top-level course model representing a complete curriculum for a language.
 */
export interface Course {
  id: string;
  language: LanguageId;
  title: string;
  description: string;
  chapters: Chapter[];
}
