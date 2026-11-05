export const lightColors = {
  primary: "#4C6B5A",
  onPrimary: "#FFFFFF",
  primaryContainer: "#E7EBE6",
  onPrimaryContainer: "#27362C",
  secondary: "#946654",
  secondaryContainer: "#F0E7E1",
  tertiary: "#806126",
  tertiaryContainer: "#F1EBDD",
  background: "#F7F4EE",
  surface: "#FFFDFa",
  text: "#2D302C",
  textMuted: "#595B55",
  outline: "#D8D3C9",
  success: "#35684C",
  successContainer: "#E5EEE6",
  error: "#A23B33",
  errorContainer: "#F4E5E2",
  codeSurface: "#F2EFE9",
} as const

export const darkColors = {
  primary: "#A3B5A4",
  onPrimary: "#27352B",
  primaryContainer: "#3B493F",
  onPrimaryContainer: "#DDE7DD",
  secondary: "#D1A28E",
  secondaryContainer: "#514139",
  tertiary: "#D7BD7F",
  tertiaryContainer: "#4B412D",
  background: "#232824",
  surface: "#2B302B",
  text: "#ECECE5",
  textMuted: "#C4C5BC",
  outline: "#4B514B",
  success: "#A0CDA9",
  successContainer: "#3A5140",
  error: "#F0A59B",
  errorContainer: "#593832",
  codeSurface: "#1E231F",
} as const

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const

export const radii = {
  small: 8,
  medium: 12,
  large: 16,
  extraLarge: 20,
} as const

export type LearnColors = typeof lightColors
