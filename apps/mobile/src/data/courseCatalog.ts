import type { Course, Exercise } from '@learn/core';
import swiftPack from '../../../../packages/content/packs/swift-foundations.json';
import reactPack from '../../../../packages/content/packs/react-foundations.json';
import typescriptPack from '../../../../packages/content/packs/typescript-foundations.json';
import { DEMO_COURSE } from './demoCourse';
import type { SupportedLanguage } from '../types/languages';

export interface LanguageOption {
  id: SupportedLanguage;
  label: string;
  shortLabel: string;
  icon: string;
  description: string;
}

export const LANGUAGE_OPTIONS: LanguageOption[] = [
  { id: 'kotlin', label: 'Kotlin', shortLabel: 'Kotlin', icon: '◇', description: 'Android and mobile foundations' },
  { id: 'swift', label: 'Swift', shortLabel: 'Swift', icon: '✦', description: 'iOS and SwiftUI reasoning' },
  { id: 'react', label: 'React', shortLabel: 'React', icon: '⚛', description: 'Component and UI thinking' },
  { id: 'typescript', label: 'TypeScript', shortLabel: 'TS', icon: 'TS', description: 'Types, APIs, and interviews' },
];

type PackCourse = {
  id: string;
  title: string;
  description: string;
  language?: string;
  chapters: Array<{
    id: string;
    title: string;
    summary: string;
    lessons: Array<{
      id: string;
      title: string;
      explanation: string;
      exercises: Exercise[];
    }>;
  }>;
};

type PackFile = {
  language: string;
  courses: PackCourse[];
};

function courseFromPack(pack: PackFile): Course {
  const course = pack.courses[0];
  return {
    id: course.id,
    language: pack.language,
    title: course.title,
    description: course.description,
    chapters: course.chapters,
  };
}

const PACK_COURSES: Record<Exclude<SupportedLanguage, 'kotlin'>, Course> = {
  swift: courseFromPack(swiftPack as unknown as PackFile),
  react: courseFromPack(reactPack as unknown as PackFile),
  typescript: courseFromPack(typescriptPack as unknown as PackFile),
};

export const COURSE_CATALOG: Record<SupportedLanguage, Course> = {
  kotlin: DEMO_COURSE,
  ...PACK_COURSES,
};

export function getCourse(language: SupportedLanguage): Course {
  return COURSE_CATALOG[language];
}

export function getLanguageOption(language: SupportedLanguage): LanguageOption {
  return LANGUAGE_OPTIONS.find((option) => option.id === language) ?? LANGUAGE_OPTIONS[0];
}

export function getPackCourse(pack: PackFile): Course {
  return courseFromPack(pack);
}
