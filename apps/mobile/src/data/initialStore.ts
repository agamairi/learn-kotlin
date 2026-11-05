import type { ReviewCard } from '@learn/core';

export interface DemoFlashcard {
  id: string;
  question: string;
  answer: string;
  codeSnippet?: string;
  box: number;
  dueToday: boolean;
}

export const INITIAL_REVIEW_CARDS: DemoFlashcard[] = [
  {
    id: 'rc-1',
    question: 'What is the key difference between val and var in Kotlin?',
    answer: 'val is a read-only reference (cannot be reassigned). var is mutable and can be reassigned.',
    codeSnippet: 'val immutableRef = 100L\nvar mutableCounter = 0',
    box: 1, // 1-day interval
    dueToday: true,
  },
  {
    id: 'rc-2',
    question: 'How do you perform safe null navigation without throwing NPE?',
    answer: 'Use the safe call operator `?.`, which returns null if the target is null.',
    codeSnippet: 'val customerName = order?.customer?.name',
    box: 2, // 2-day interval
    dueToday: true,
  },
  {
    id: 'rc-3',
    question: 'What does the Elvis operator ?: do?',
    answer: 'It provides a default fallback expression if the left-hand operand evaluates to null.',
    codeSnippet: 'val port = customPort ?: 8080',
    box: 2, // 2-day interval
    dueToday: true,
  },
  {
    id: 'rc-4',
    question: 'Why store financial amounts in Long cents rather than Double?',
    answer: 'IEEE-754 floating-point decimals introduce rounding inaccuracies. Long cents store exact integer counts.',
    codeSnippet: 'val priceInCents: Long = 19_99L',
    box: 3, // 4-day interval
    dueToday: true,
  },
  {
    id: 'rc-5',
    question: 'What does a Kotlin data class automatically implement?',
    answer: 'equals(), hashCode(), toString(), copy(), and componentN() functions.',
    codeSnippet: 'data class Account(val id: String, val balanceCents: Long)',
    box: 4, // 8-day interval
    dueToday: false,
  },
  {
    id: 'rc-6',
    question: 'What requirement applies to when used as an expression?',
    answer: 'It must be exhaustive: either cover every sealed subclass / enum constant or provide an else branch.',
    box: 5, // 16-day interval
    dueToday: false,
  },
];
