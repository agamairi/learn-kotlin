/**
 * Warm ivory, sage, clay, ochre, and warm charcoal theme palette.
 * Sourced directly from Color.kt and Stitch design system specifications.
 */
export const Colors = {
  // Light surfaces & backgrounds
  background: '#F7F4EE', // LightIvoryBackground
  surface: '#FFFDFA', // LightOffWhiteSurface
  surfaceDim: '#EAE6DF',
  surfaceVariant: '#F0ECE5', // LightNeutralSurfaceVariant
  surfaceContainerLow: '#FCFAF6',
  surfaceContainer: '#F6F3ED',
  surfaceContainerHigh: '#F1EEE7',

  // Sage primary (tactile, natural accent)
  primary: '#4C6B5A', // LightSagePrimary
  onPrimary: '#FFFFFF',
  primaryContainer: '#E7EBE6', // LightSageContainer
  onPrimaryContainer: '#27362C', // LightOnSageContainer
  primaryDark: '#345343',

  // Clay secondary (warm terra-cotta for chapters, mistakes, highlights)
  secondary: '#946654', // LightClaySecondary
  onSecondary: '#FFFFFF',
  secondaryContainer: '#F0E7E1', // LightClayContainer
  onSecondaryContainer: '#46352D',

  // Ochre tertiary (golden warm accents, badges, flames)
  tertiary: '#806126', // LightOchreTertiary
  onTertiary: '#FFFFFF',
  tertiaryContainer: '#F1EBDD',
  onTertiaryContainer: '#45391F',

  // Ink & text
  text: '#2D302C', // LightWarmCharcoal
  textMuted: '#6C7069',
  textSecondary: '#787D75',
  textInverse: '#F3F1EA',

  // Borders & outlines
  outline: '#817E74',
  outlineVariant: '#E8E4DC',
  border: '#E8E4DC',

  // Feedback & semantics
  success: '#35684C',
  onSuccess: '#FFFFFF',
  successContainer: '#E5EEE6',
  onSuccessContainer: '#294734',

  error: '#A23B33',
  onError: '#FFFFFF',
  errorContainer: '#F4E5E2',
  onErrorContainer: '#4A2926',

  warning: '#D98A48',
  warningContainer: '#FDF1E6',

  // Code surface & syntax
  codeSurface: '#F2EFE9',
  codeBorder: '#E0DDD4',
  codeKeyword: '#315744',
  codeString: '#915141',
  codeNumber: '#805E21',
  codeComment: '#575A54',
  codeText: '#2D302C',

  // Mascot & companion
  kodaTeal: '#36746E',
  kodaTealLight: '#E8F3F1',
  kodaTealDark: '#234C48',
  kodaOrange: '#D97A38',
  kodaCream: '#FFF6E9',

  // Octo Coach mascot palette
  octopusPurple: '#6856A6',
  octopusPurpleDark: '#493878',
  octopusPurpleLight: '#EEEAF8',
  octopusPink: '#F39AB4',
  octopusGold: '#F2B84B',
  octopusSuction: '#D9CFF2',
} as const;

export type ThemeColors = typeof Colors;
