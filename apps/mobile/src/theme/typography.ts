import type { TextStyle } from 'react-native';

export const Typography: Record<string, TextStyle> = {
  display: {
    fontSize: 32,
    lineHeight: 40,
    fontWeight: '700',
    letterSpacing: -0.5,
  },
  headlineLarge: {
    fontSize: 26,
    lineHeight: 34,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  headlineMedium: {
    fontSize: 20,
    lineHeight: 28,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  headlineSmall: {
    fontSize: 17,
    lineHeight: 24,
    fontWeight: '600',
  },
  bodyLarge: {
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '400',
  },
  bodyMedium: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '400',
  },
  bodySmall: {
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '400',
  },
  labelLarge: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
  labelMedium: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
  },
  labelSmall: {
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '600',
    letterSpacing: 0.5,
  },
  code: {
    fontSize: 13,
    lineHeight: 19,
    fontFamily: 'monospace',
    fontWeight: '400',
  },
};
