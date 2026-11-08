import type { Course, Exercise } from '@learn/core';
import { validateCoursePack, type CoursePack } from '@learn/content';
import kotlinPack from '../../../../packages/content/packs/kotlin-foundations.json';
import swiftPack from '../../../../packages/content/packs/swift-foundations.json';
import flutterPack from '../../../../packages/content/packs/flutter-dart-interviews.json';
import reactNativePack from '../../../../packages/content/packs/react-native-typescript-interviews.json';
import javaPack from '../../../../packages/content/packs/java-android-interviews.json';
import type { SupportedLanguage } from '../types/languages';

export interface LanguageOption {
  id: SupportedLanguage;
  label: string;
  shortLabel: string;
  icon: string;
  description: string;
}

export const LANGUAGE_OPTIONS: LanguageOption[] = [
  { id: 'kotlin', label: 'Kotlin for Android', shortLabel: 'Kotlin', icon: '◇', description: 'Kotlin, Compose, and production Android interviews' },
  { id: 'swift', label: 'Swift for iOS', shortLabel: 'Swift', icon: '✦', description: 'Swift, SwiftUI, and production iOS interviews' },
  { id: 'flutter', label: 'Flutter & Dart', shortLabel: 'Flutter', icon: '◆', description: 'Dart, widgets, state, and cross-platform interviews' },
  { id: 'react-native', label: 'React Native & TypeScript', shortLabel: 'React Native', icon: '⚛', description: 'Type-safe React Native mobile interviews' },
  { id: 'java', label: 'Java for Android', shortLabel: 'Java', icon: '☕', description: 'Java, concurrency, and Android interviews' },
];

type PackFile = CoursePack;

function courseFromPack(pack: PackFile): Course {
  const course = pack.courses[0];
  return {
    id: course.id,
    language: pack.language,
    title: course.title,
    description: course.description,
    // The content package is validated before this boundary. The two packages
    // intentionally share the same JSON contract but have slightly different
    // optional fields for authoring-only exercise variants.
    chapters: course.chapters as unknown as Course['chapters'],
  };
}

function validatedPack(raw: unknown, filename: string): PackFile {
  const result = validateCoursePack(raw, { clientVersion: '0.1.0' });
  if (!result.valid) {
    const details = result.errors.map((issue) => `${issue.path}: ${issue.message}`).join('; ');
    throw new Error(`Bundled course pack ${filename} is invalid: ${details}`);
  }
  return raw as PackFile;
}

const BUNDLED_PACKS: Record<SupportedLanguage, PackFile> = {
  kotlin: validatedPack(kotlinPack, 'kotlin-foundations.json'),
  swift: validatedPack(swiftPack, 'swift-foundations.json'),
  flutter: validatedPack(flutterPack, 'flutter-dart-interviews.json'),
  'react-native': validatedPack(reactNativePack, 'react-native-typescript-interviews.json'),
  java: validatedPack(javaPack, 'java-android-interviews.json'),
};

export const BUNDLED_PACK_METADATA = Object.values(BUNDLED_PACKS).map((pack) => ({
  packId: pack.packId,
  version: pack.version,
}));

export const COURSE_CATALOG: Record<SupportedLanguage, Course> = {
  kotlin: courseFromPack(BUNDLED_PACKS.kotlin),
  swift: courseFromPack(BUNDLED_PACKS.swift),
  flutter: courseFromPack(BUNDLED_PACKS.flutter),
  'react-native': courseFromPack(BUNDLED_PACKS['react-native']),
  java: courseFromPack(BUNDLED_PACKS.java),
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
