import React, { useEffect, useMemo, useState } from 'react';
import {
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import {
  CARD_XP,
  DefaultAnswerGrader,
  DefaultXpPolicy,
  type ClozeExercise,
  type CodePuzzle,
  type Exercise,
  type Lesson,
  type MultiPartProblemExercise,
  type MultipleChoiceExercise,
  type PredictOutputExercise,
  type WriteFunctionExercise,
} from '@learn/core';
import { AppProvider, useApp, type ByokProvider } from './src/domain/state/AppContext';
import { getCourse, getLanguageOption, LANGUAGE_OPTIONS } from './src/data/courseCatalog';
import { OnboardingScreen } from './src/screens/OnboardingScreen';
import { HomeScreen } from './src/screens/HomeScreen';
import { BottomTabBar } from './src/components/navigation/BottomTabBar';
import { Badge } from './src/components/common/Badge';
import { Button } from './src/components/common/Button';
import { Card } from './src/components/common/Card';
import { ProgressBar } from './src/components/common/ProgressBar';
import { StreakCelebration } from './src/components/celebrations/StreakCelebration';
import { KodaBubble } from './src/components/koda/KodaBubble';
import { OctopusMascot } from './src/components/octopus/OctopusMascot';
import { Colors } from './src/theme/colors';
import { Radius, Spacing } from './src/theme/spacing';
import { Typography } from './src/theme/typography';
import type { TabRoute } from './src/types/navigation';

function ScreenHeader({ title, eyebrow }: { title: string; eyebrow?: string }) {
  return (
    <View style={styles.screenHeader}>
      <View>
        {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
        <Text style={styles.screenTitle}>{title}</Text>
      </View>
      <OctopusMascot mood="encouraging" size={42} animate={false} />
    </View>
  );
}

function PathScreen() {
  const { startLesson, completedLessonIds, selectedLanguage } = useApp();
  const course = getCourse(selectedLanguage);
  const language = getLanguageOption(selectedLanguage);
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.pathMapContent} showsVerticalScrollIndicator={false}>
      <ScreenHeader title="Your path" eyebrow={`${language.label} foundations`} />
      <KodaBubble message="Follow the islands from syntax to interview judgment. Every lesson repeats the ideas that belong to that unit so they stick." />
      <View style={styles.mapIntroCard}>
        <OctopusMascot mood="encouraging" size={58} />
        <View style={styles.mapIntroCopy}>
          <Text style={styles.mapIntroTitle}>{language.label} Coast</Text>
          <Text style={styles.mutedText}>{course.chapters.length} units · {course.chapters.reduce((total, chapter) => total + chapter.lessons.length, 0)} lessons · 8 cards each</Text>
        </View>
        <Badge label="MAP" variant="ochre" />
      </View>
      <View style={styles.courseMap}>
        <Text style={styles.mapLabel}>START AT THE SHORE</Text>
        <View style={styles.mapTrailSpine} />
        {course.chapters.map((chapter, chapterIndex) => {
          const completedLessons = chapter.lessons.filter((lesson) => completedLessonIds.includes(lesson.id)).length;
          const isLeft = chapterIndex % 2 === 0;
          return (
            <View key={chapter.id} style={styles.mapUnit}>
              {chapterIndex > 0 ? <View style={[styles.mapTrail, isLeft ? styles.mapTrailLeft : styles.mapTrailRight]} /> : null}
              <View style={[styles.islandCard, isLeft ? styles.islandCardLeft : styles.islandCardRight]}>
                <View style={styles.islandTopLine}>
                  <View style={styles.unitPill}>
                    <Text style={styles.unitPillText}>UNIT {chapterIndex + 1}</Text>
                  </View>
                  <Badge label={`${completedLessons}/${chapter.lessons.length}`} variant={completedLessons === chapter.lessons.length ? 'success' : 'neutral'} />
                </View>
                <Text style={styles.islandTitle}>{chapter.title.replace(/^Chapter \d+ · /, '')}</Text>
                <Text style={styles.islandSummary}>{chapter.summary}</Text>
                <View style={styles.lessonIslandRow}>
                  {chapter.lessons.map((lesson, lessonIndex) => {
                    const completed = completedLessonIds.includes(lesson.id);
                    return (
                      <TouchableOpacity
                        key={lesson.id}
                        style={styles.lessonIslandButton}
                        onPress={() => startLesson(chapter.id, lesson.id)}
                        activeOpacity={0.8}
                        accessibilityRole="button"
                        accessibilityLabel={`${lesson.title}, ${completed ? 'completed' : 'not completed'}, ${lesson.exercises.length} questions`}
                      >
                        <View style={[styles.lessonIsland, completed && styles.lessonIslandDone, lessonIndex === 0 && !completed && styles.lessonIslandCurrent]}>
                          <Text style={[styles.lessonIslandText, completed && styles.lessonIslandTextDone]}>{completed ? '✓' : lessonIndex + 1}</Text>
                        </View>
                        <Text style={styles.lessonIslandLabel} numberOfLines={1}>{lesson.title}</Text>
                      </TouchableOpacity>
                    );
                  })}
                </View>
              </View>
            </View>
          );
        })}
        <Text style={styles.mapLabelEnd}>THE NEXT SHORE IS WAITING</Text>
      </View>
    </ScrollView>
  );
}

function ReviewScreen() {
  const { reviewCards, scheduleCardReview } = useApp();
  const dueCards = reviewCards.filter((card) => card.dueToday);
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <ScreenHeader title="Spaced review" eyebrow="Keep it accessible" />
      <KodaBubble variant="clay" message="A quick recall attempt is more valuable than rereading. Answer from memory, then check the explanation." />
      {dueCards.length === 0 ? (
        <Card style={styles.emptyCard}>
          <Text style={styles.emptyIcon}>✓</Text>
          <Text style={styles.cardTitle}>Review queue clear</Text>
          <Text style={styles.mutedText}>New cards will appear as your schedule brings them back.</Text>
        </Card>
      ) : (
        dueCards.map((card) => (
          <ReviewCard key={card.id} card={card} onRecall={() => scheduleCardReview(card.id, true)} />
        ))
      )}
    </ScrollView>
  );
}

function ReviewCard({
  card,
  onRecall,
}: {
  card: { question: string; answer: string; codeSnippet?: string; box: number };
  onRecall: () => void;
}) {
  const [revealed, setRevealed] = useState(false);
  return (
    <Card style={styles.reviewItem}>
      <View style={styles.rowBetween}>
        <Badge label={`Box ${card.box}`} variant="ochre" />
        <Text style={styles.mutedText}>Recall card</Text>
      </View>
      <Text style={styles.reviewQuestion}>{card.question}</Text>
      {card.codeSnippet ? <CodeBlock code={card.codeSnippet} /> : null}
      {revealed ? (
        <View style={styles.answerBox}>
          <Text style={styles.answerLabel}>Explanation</Text>
          <Text style={styles.bodyText}>{card.answer}</Text>
        </View>
      ) : null}
      <Button title={revealed ? 'I recalled it' : 'Reveal explanation'} onPress={() => (revealed ? onRecall() : setRevealed(true))} variant={revealed ? 'primary' : 'outline'} />
    </Card>
  );
}

function StatsScreen() {
  const { totalXp, xpToday, currentStreak, longestStreak, completedLessonIds, reviewCards } = useApp();
  const dueCount = reviewCards.filter((card) => card.dueToday).length;
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <ScreenHeader title="Your progress" eyebrow="Small steps, visible proof" />
      <View style={styles.statGrid}>
        <StatTile value={`${totalXp}`} label="Total XP" accent={Colors.primary} />
        <StatTile value={`${currentStreak}`} label="Day streak" accent={Colors.secondary} />
        <StatTile value={`${completedLessonIds.length}`} label="Lessons" accent={Colors.tertiary} />
        <StatTile value={`${dueCount}`} label="Due reviews" accent={Colors.kodaTeal} />
      </View>
      <Card>
        <Text style={styles.cardTitle}>Today’s momentum</Text>
        <Text style={styles.mutedText}>{xpToday} XP earned today · longest streak {longestStreak} days</Text>
        <ProgressBar progress={Math.min(1, xpToday / 30)} style={styles.progressSpacing} />
        <Text style={styles.bodyText}>Finish a lesson faster than its baseline to unlock a capped pace bonus.</Text>
      </Card>
      <KodaBubble message="Consistency beats intensity. Come back for one focused lesson tomorrow." />
    </ScrollView>
  );
}

function StatTile({ value, label, accent }: { value: string; label: string; accent: string }) {
  return (
    <Card style={styles.statTile}>
      <Text style={[styles.statValue, { color: accent }]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </Card>
  );
}

function SettingsScreen() {
  const { user, syncStatus, lastSyncedAt, triggerManualSync, signOut, resetProgress, selectedLanguage, setSelectedLanguage, byokProvider, byokKeyConfigured, saveByokConfig, clearByokConfig } = useApp();
  const [syncing, setSyncing] = useState(false);
  const [byokOpen, setByokOpen] = useState(false);
  const [byokKey, setByokKey] = useState('');
  const [selectedProvider, setSelectedProvider] = useState<ByokProvider>(byokProvider);
  const [byokSaved, setByokSaved] = useState(false);
  useEffect(() => {
    setSelectedProvider(byokProvider);
  }, [byokProvider]);
  const handleSync = async () => {
    setSyncing(true);
    await triggerManualSync();
    setSyncing(false);
  };
  const handleSaveByok = async () => {
    const saved = await saveByokConfig(selectedProvider, byokKey);
    if (saved) {
      setByokKey('');
      setByokSaved(true);
    }
  };
  const providerOptions: Array<{ id: ByokProvider; label: string }> = [
    { id: 'openai-compatible', label: 'OpenAI-compatible' },
    { id: 'anthropic-compatible', label: 'Anthropic-compatible' },
    { id: 'custom', label: 'Custom endpoint' },
  ];
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <ScreenHeader title="Settings" eyebrow="Your learning space" />
      <Card>
        <View style={styles.accountRow}>
          <OctopusMascot mood="greeting" size={52} animate={false} />
          <View style={styles.flexOne}>
            <Text style={styles.cardTitle}>{user.displayName}</Text>
            <Text style={styles.mutedText}>{user.type === 'guest' ? 'Guest account · device only' : user.email}</Text>
          </View>
          <Badge label={user.type === 'guest' ? 'Guest' : 'Account'} variant={user.type === 'guest' ? 'neutral' : 'success'} />
        </View>
      </Card>
      <Card>
        <Text style={styles.cardTitle}>Cloud sync</Text>
        <Text style={styles.mutedText}>Progress is staged locally first, then pushed through the sync boundary when you are online.</Text>
        <Text style={styles.syncLine}>Status: {syncStatus}</Text>
        <Text style={styles.syncLine}>Last sync: {lastSyncedAt ? new Date(lastSyncedAt).toLocaleString() : 'Not yet synced'}</Text>
        <Button title={syncing ? 'Syncing…' : 'Sync now'} onPress={handleSync} disabled={syncing || user.type === 'guest'} loading={syncing} />
      </Card>
      <Card>
        <Text style={styles.cardTitle}>Learning track</Text>
        <Text style={styles.mutedText}>Switch your active course without losing your account, streak, or review queue.</Text>
        <View style={styles.settingsLanguageGrid}>
          {LANGUAGE_OPTIONS.map((option) => {
            const selected = option.id === selectedLanguage;
            return (
              <TouchableOpacity key={option.id} onPress={() => setSelectedLanguage(option.id)} style={[styles.settingsLanguageButton, selected && styles.settingsLanguageButtonSelected]} activeOpacity={0.8} accessibilityRole="radio" accessibilityState={{ selected }}>
                <Text style={[styles.settingsLanguageIcon, selected && styles.settingsLanguageIconSelected]}>{option.icon}</Text>
                <Text style={[styles.settingsLanguageLabel, selected && styles.settingsLanguageLabelSelected]}>{option.label}</Text>
                {selected ? <Text style={styles.settingsLanguageCheck}>✓</Text> : null}
              </TouchableOpacity>
            );
          })}
        </View>
      </Card>
      <Card variant="muted">
        <View style={styles.rowBetween}>
          <View style={styles.flexOne}>
            <Text style={styles.cardTitle}>BYOK · AI Coach</Text>
            <Text style={styles.mutedText}>Bring your own API key for optional explanations and hint generation.</Text>
          </View>
          <Badge label={byokKeyConfigured ? 'Configured' : 'Optional'} variant={byokKeyConfigured ? 'success' : 'neutral'} />
        </View>
        <Text style={styles.byokNotice}>Prototype storage: local device only. The key is not added to cloud sync events. Use a secure storage adapter before production.</Text>
        <Button title={byokOpen ? 'Hide BYOK setup' : byokKeyConfigured ? 'Update BYOK key' : 'Add a BYOK key'} onPress={() => setByokOpen((current) => !current)} variant="outline" />
        {byokOpen ? (
          <View style={styles.byokForm}>
            <Text style={styles.inputLabel}>Provider</Text>
            <View style={styles.providerRow}>
              {providerOptions.map((provider) => (
                <TouchableOpacity key={provider.id} onPress={() => setSelectedProvider(provider.id)} style={[styles.providerChip, selectedProvider === provider.id && styles.providerChipSelected]} activeOpacity={0.8}>
                  <Text style={[styles.providerChipText, selectedProvider === provider.id && styles.providerChipTextSelected]}>{provider.label}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Text style={styles.inputLabel}>API key</Text>
            <TextInput style={styles.input} value={byokKey} onChangeText={(value) => { setByokKey(value); setByokSaved(false); }} placeholder="Paste a key for this device" placeholderTextColor={Colors.textSecondary} secureTextEntry autoCapitalize="none" autoCorrect={false} />
            {byokSaved ? <Text style={styles.byokSavedText}>Saved locally on this device.</Text> : null}
            <Button title="Save key locally" onPress={handleSaveByok} disabled={!byokKey.trim()} variant="primary" />
            {byokKeyConfigured ? <Button title="Clear stored key" onPress={() => { void clearByokConfig(); setByokKey(''); setByokSaved(false); }} variant="ghost" size="sm" /> : null}
          </View>
        ) : null}
      </Card>
      <Card variant="muted">
        <Text style={styles.cardTitle}>Community packs</Text>
        <Text style={styles.mutedText}>The versioned schema and GitHub source lifecycle are ready in the shared layer. Moderated marketplace discovery is next.</Text>
      </Card>
      <Button title="Reset demo progress" onPress={resetProgress} variant="outline" />
      {user.type === 'account' ? <Button title="Sign out" onPress={signOut} variant="ghost" /> : null}
    </ScrollView>
  );
}

function CodeBlock({ code }: { code: string }) {
  return (
    <View style={styles.codeBlock}>
      <Text style={styles.codeText}>{code}</Text>
    </View>
  );
}

function PuzzleEditor({
  puzzle,
  answers,
  submitted,
  onAnswer,
}: {
  puzzle: CodePuzzle;
  answers: Record<string, string>;
  submitted: boolean;
  onAnswer: (placeholder: string, value: string) => void;
}) {
  const preview = puzzle.blanks.reduce((value, blank) => value.replaceAll(blank.placeholder, answers[blank.placeholder] || blank.placeholder), puzzle.template);
  return (
    <View style={styles.puzzleEditor}>
      <CodeBlock code={preview} />
      {puzzle.blanks.map((blank) => (
        <View key={blank.placeholder} style={styles.blankGroup}>
          <Text style={styles.blankLabel}>Fill {blank.placeholder}</Text>
          {blank.options?.length ? (
            <View style={styles.choiceWrap}>
              {blank.options.map((option) => (
                <TouchableOpacity key={option} onPress={() => !submitted && onAnswer(blank.placeholder, option)} disabled={submitted} style={[styles.choiceChip, answers[blank.placeholder] === option && styles.choiceChipSelected]}>
                  <Text style={styles.choiceChipText}>{option}</Text>
                </TouchableOpacity>
              ))}
            </View>
          ) : (
            <TextInput style={styles.input} value={answers[blank.placeholder] || ''} onChangeText={(value) => onAnswer(blank.placeholder, value)} editable={!submitted} placeholder="Type the missing code" placeholderTextColor={Colors.textSecondary} autoCapitalize="none" />
          )}
        </View>
      ))}
    </View>
  );
}

function ExerciseInput({
  exercise,
  selectedIndex,
  answers,
  submitted,
  onSelect,
  onAnswer,
  onText,
}: {
  exercise: Exercise;
  selectedIndex: number | null;
  answers: Record<string, string>;
  submitted: boolean;
  onSelect: (index: number) => void;
  onAnswer: (key: string, value: string) => void;
  onText: (value: string) => void;
}) {
  if (exercise.type === 'multiple_choice') {
    const current = exercise as MultipleChoiceExercise;
    return (
      <View style={styles.optionsList}>
        {current.options.map((option, index) => {
          const selected = selectedIndex === index;
          const correct = submitted && index === current.correctIndex;
          return (
            <TouchableOpacity key={`${current.id}-${index}`} style={[styles.option, selected && styles.optionSelected, correct && styles.optionCorrect]} onPress={() => !submitted && onSelect(index)} disabled={submitted} accessibilityRole="radio" accessibilityState={{ selected }}>
              <View style={[styles.optionMarker, selected && styles.optionMarkerSelected]}><Text style={styles.optionMarkerText}>{String.fromCharCode(65 + index)}</Text></View>
              <Text style={styles.optionText}>{option}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    );
  }
  if (exercise.type === 'cloze') {
    const current = exercise as ClozeExercise;
    return <PuzzleEditor puzzle={{ template: current.template, blanks: current.blanks, explanation: current.explanation }} answers={answers} submitted={submitted} onAnswer={onAnswer} />;
  }
  if (exercise.type === 'predict_output') {
    const current = exercise as PredictOutputExercise;
    return (
      <View style={styles.puzzleEditor}>
        <CodeBlock code={current.code} />
        <Text style={styles.blankLabel}>What will the program print?</Text>
        <TextInput style={[styles.input, styles.multilineInput]} value={answers.output || ''} onChangeText={onText} editable={!submitted} multiline numberOfLines={3} placeholder="Write the output" placeholderTextColor={Colors.textSecondary} />
      </View>
    );
  }
  if (exercise.type === 'write_function') {
    const current = exercise as WriteFunctionExercise;
    if (current.guided) return <PuzzleEditor puzzle={current.guided} answers={answers} submitted={submitted} onAnswer={onAnswer} />;
    return (
      <View style={styles.puzzleEditor}>
        <CodeBlock code={current.starterCode || 'Write the function shown in the prompt.'} />
        <TextInput style={[styles.input, styles.multilineInput]} value={answers.code || ''} onChangeText={onText} editable={!submitted} multiline numberOfLines={5} placeholder="Explain or write your approach" placeholderTextColor={Colors.textSecondary} />
      </View>
    );
  }
  const current = exercise as MultiPartProblemExercise;
  const part = current.parts[0];
  return (
    <View style={styles.puzzleEditor}>
      <Text style={styles.bodyText}>{current.story}</Text>
      <Text style={styles.questionText}>{part?.prompt}</Text>
      {part?.guided ? <PuzzleEditor puzzle={part.guided} answers={answers} submitted={submitted} onAnswer={onAnswer} /> : <TextInput style={[styles.input, styles.multilineInput]} value={answers.part || ''} onChangeText={onText} editable={!submitted} multiline numberOfLines={4} placeholder="Describe the safest next step" placeholderTextColor={Colors.textSecondary} />}
    </View>
  );
}

function exerciseHasAnswer(exercise: Exercise, selectedIndex: number | null, answers: Record<string, string>): boolean {
  if (exercise.type === 'multiple_choice') return selectedIndex !== null;
  if (exercise.type === 'predict_output') return Boolean(answers.output?.trim());
  if (exercise.type === 'write_function') return Boolean(exercise.guided ? exercise.guided.blanks.every((blank) => answers[blank.placeholder]?.trim()) : answers.code?.trim());
  if (exercise.type === 'multi_part_problem') {
    const part = exercise.parts[0];
    return Boolean(part?.guided ? part.guided.blanks.every((blank) => answers[blank.placeholder]?.trim()) : answers.part?.trim());
  }
  return exercise.blanks.every((blank) => answers[blank.placeholder]?.trim());
}

function gradeExercise(grader: DefaultAnswerGrader, exercise: Exercise, selectedIndex: number | null, answers: Record<string, string>): boolean {
  switch (exercise.type) {
    case 'multiple_choice': return selectedIndex !== null && grader.gradeMultipleChoice(exercise, selectedIndex);
    case 'cloze': return grader.gradeCloze(exercise, answers);
    case 'predict_output': return grader.gradePredictOutput(exercise, answers.output || '');
    case 'write_function': return exercise.guided ? grader.gradeCodePuzzle(exercise.guided, answers) : Boolean(answers.code?.trim());
    case 'multi_part_problem': {
      const part = exercise.parts[0];
      return Boolean(part?.guided ? grader.gradeCodePuzzle(part.guided, answers) : answers.part?.trim() === part?.expected.trim());
    }
  }
}

function exerciseExplanation(exercise: Exercise): string {
  if (exercise.type === 'multi_part_problem') return exercise.parts[0]?.expected || 'Break the problem into one small, testable decision at a time.';
  return exercise.explanation || 'Review the example and connect the choice to the underlying rule.';
}

interface LessonSummaryData {
  baseXp: number;
  paceBonusXp: number;
  totalXp: number;
  perfect: boolean;
  answerStreak: number;
}

function LessonSummary({ lesson, summary, onContinue }: { lesson: Lesson; summary: LessonSummaryData; onContinue: () => void }) {
  return (
    <ScrollView contentContainerStyle={styles.summaryContent} showsVerticalScrollIndicator={false}>
      <View style={styles.summaryConfetti}>
        <Text style={styles.confettiText}>✦</Text>
        <Text style={styles.confettiText}>✧</Text>
        <Text style={styles.confettiText}>✦</Text>
      </View>
      <OctopusMascot mood="celebrating" size={132} showBadge={true} message={summary.perfect ? 'Every decision landed.' : 'You kept going — that is how skill sticks.'} />
      <Text style={styles.summaryEyebrow}>{summary.perfect ? 'Perfect lesson' : 'Lesson complete'}</Text>
      <Text style={styles.summaryTitle}>{summary.perfect ? 'You are thinking like a Kotlin engineer.' : 'Nice work showing up.'}</Text>
      <Text style={styles.summaryLesson}>{lesson.title}</Text>
      <View style={styles.summaryBadges}>
        {summary.perfect ? <Badge label="Perfect run" variant="success" /> : <Badge label="Lesson complete" variant="sage" />}
        {summary.answerStreak >= 3 ? <Badge label={`${summary.answerStreak} in a row`} variant="ochre" /> : null}
      </View>
      <Card style={styles.summaryScoreCard}>
        <Text style={styles.summaryScoreLabel}>XP earned</Text>
        <Text style={styles.summaryScoreValue}>+{summary.totalXp}</Text>
        <View style={styles.summaryXpRow}>
          <Text style={styles.summaryXpLabel}>Reasoning cards</Text>
          <Text style={styles.summaryXpValue}>+{summary.baseXp} XP</Text>
        </View>
        <View style={styles.summaryXpRow}>
          <Text style={styles.summaryXpLabel}>Fast finish bonus</Text>
          <Text style={styles.summaryXpValue}>+{summary.paceBonusXp} XP</Text>
        </View>
      </Card>
      <KodaBubble
        variant="sage"
        title={summary.perfect ? 'Octo Coach says' : 'Keep the tentacles moving'}
        message={summary.perfect ? 'Perfect lessons are proof that you can explain the choice, not just recognize the syntax.' : 'Review the explanation you missed, then come back for a cleaner second pass.'}
      />
      <Button title="Continue learning" onPress={onContinue} variant="primary" size="lg" />
    </ScrollView>
  );
}

function LessonScreen({ lesson }: { lesson: Lesson }) {
  const { recordExerciseGrade, finishLesson, exitLesson } = useApp();
  const grader = useMemo(() => new DefaultAnswerGrader(), []);
  const xpPolicy = useMemo(() => new DefaultXpPolicy(), []);
  const [exerciseQueue, setExerciseQueue] = useState<Exercise[]>(() => lesson.exercises);
  const [exerciseIndex, setExerciseIndex] = useState(0);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [submitted, setSubmitted] = useState(false);
  const [isCorrect, setIsCorrect] = useState(false);
  const [passedExerciseIds, setPassedExerciseIds] = useState<string[]>([]);
  const [answerStreak, setAnswerStreak] = useState(0);
  const [milestone, setMilestone] = useState<number | null>(null);
  const [summary, setSummary] = useState<LessonSummaryData | null>(null);
  const [startedAt] = useState(() => Date.now());
  const exercise = exerciseQueue[exerciseIndex];
  const hasAnswer = exerciseHasAnswer(exercise, selectedIndex, answers);

  const submitAnswer = () => {
    if (!hasAnswer) return;
    const correct = gradeExercise(grader, exercise, selectedIndex, answers);
    setIsCorrect(correct);
    setSubmitted(true);
    const nextStreak = correct ? answerStreak + 1 : 0;
    setAnswerStreak(nextStreak);
    if (correct && (nextStreak === 3 || nextStreak === 5 || nextStreak === 10)) {
      setMilestone(nextStreak);
    }
    if (correct && !passedExerciseIds.includes(exercise.id)) {
      setPassedExerciseIds((previous) => [...previous, exercise.id]);
      const xpAwarded = xpPolicy.xpForExercise(exercise, true, true);
      recordExerciseGrade(exercise.id, true, selectedIndex ?? 0, xpAwarded);
    } else if (!correct) {
      recordExerciseGrade(exercise.id, false, selectedIndex ?? 0);
    }
  };

  const nextStep = () => {
    if (!isCorrect) {
      setExerciseQueue((previous) => [...previous, exercise]);
    }
    const completedExerciseIds = isCorrect && !passedExerciseIds.includes(exercise.id)
      ? [...passedExerciseIds, exercise.id]
      : passedExerciseIds;
    const allCorrect = completedExerciseIds.length === lesson.exercises.length;
    if (!allCorrect) {
      setExerciseIndex((previous) => previous + 1);
      setSelectedIndex(null);
      setAnswers({});
      setSubmitted(false);
      setIsCorrect(false);
      return;
    }
    const baseXp = completedExerciseIds.reduce((total, exerciseId) => {
      const passedExercise = lesson.exercises.find((candidate) => candidate.id === exerciseId);
      return total + (passedExercise ? xpPolicy.xpForExercise(passedExercise, true, true) : 0);
    }, 0) || (allCorrect ? lesson.exercises.length * CARD_XP : 0);
    const bonus = xpPolicy.calculatePaceBonus({ baseXp, activeDurationMs: Date.now() - startedAt, baselineDurationMs: xpPolicy.estimateLessonBaselineMs(lesson), allCorrect });
    setSummary({ baseXp, paceBonusXp: bonus.bonusXp, totalXp: baseXp + bonus.bonusXp, perfect: allCorrect, answerStreak });
  };

  if (summary) {
    return (
      <View style={styles.lessonScreen}>
        <View style={styles.lessonHeader}>
          <TouchableOpacity onPress={exitLesson} accessibilityRole="button" accessibilityLabel="Exit lesson"><Text style={styles.closeLesson}>×</Text></TouchableOpacity>
          <Text style={styles.summaryHeaderLabel}>Lesson results</Text>
          <Text style={styles.lessonCount}>✓</Text>
        </View>
        <LessonSummary lesson={lesson} summary={summary} onContinue={() => finishLesson(lesson.id, summary.paceBonusXp)} />
      </View>
    );
  }

  return (
    <View style={styles.lessonScreen}>
      <View style={styles.lessonHeader}>
        <TouchableOpacity onPress={exitLesson} accessibilityRole="button" accessibilityLabel="Exit lesson"><Text style={styles.closeLesson}>×</Text></TouchableOpacity>
        <View style={styles.lessonProgressTrack}><View style={[styles.lessonProgressFill, { width: `${((exerciseIndex + 1) / lesson.exercises.length) * 100}%` }]} /></View>
        <Text style={styles.lessonCount}>{exerciseIndex + 1}/{lesson.exercises.length}</Text>
      </View>
      <StreakCelebration streak={milestone} onDismiss={() => setMilestone(null)} />
      <ScrollView contentContainerStyle={styles.lessonContent} showsVerticalScrollIndicator={false}>
        <Text style={styles.eyebrow}>Guided reasoning</Text>
        <Text style={styles.lessonHeading}>{lesson.title}</Text>
        {exerciseIndex === 0 ? <Text style={styles.lessonIntro}>{lesson.explanation}</Text> : null}
        <View style={styles.lessonStreakRow}>
          <Text style={styles.lessonStreakIcon}>🔥</Text>
          <Text style={styles.lessonStreakText}>{answerStreak > 0 ? `${answerStreak} correct in a row` : 'Build your answer streak'}</Text>
        </View>
        <Card style={styles.questionCard}>
          <Badge label={exercise.type.replace('_', ' ')} variant="sage" />
          <Text style={styles.questionText}>{exercise.prompt}</Text>
          {exercise.type === 'multiple_choice' && exercise.code ? <CodeBlock code={exercise.code} /> : null}
          <ExerciseInput exercise={exercise} selectedIndex={selectedIndex} answers={answers} submitted={submitted} onSelect={setSelectedIndex} onAnswer={(key, value) => setAnswers((previous) => ({ ...previous, [key]: value }))} onText={(value) => setAnswers((previous) => ({ ...previous, output: value, code: value, part: value }))} />
          {submitted ? <KodaBubble variant={isCorrect ? 'sage' : 'clay'} title={isCorrect ? 'Good reasoning' : 'We’ll circle back'} message={isCorrect ? exerciseExplanation(exercise) : 'Not quite yet. This card is moving to the back of your practice queue.'} /> : null}
          <Button title={submitted ? (isCorrect ? (passedExerciseIds.length + (passedExerciseIds.includes(exercise.id) ? 0 : 1) === lesson.exercises.length ? 'Finish lesson' : 'Next card') : 'Next card') : 'Check answer'} onPress={submitted ? nextStep : submitAnswer} disabled={!submitted && !hasAnswer} variant="primary" size="lg" />
        </Card>
        <Text style={styles.lessonHint}>Missed cards move to the back of the queue. No code editor here: learn the decision first, then apply it in your own project.</Text>
      </ScrollView>
    </View>
  );
}

function MainNavigator() {
  const app = useApp();
  if (app.screen === 'onboarding') return <OnboardingScreen />;
  if (app.screen === 'lesson' && app.activeLessonParams) {
    const course = getCourse(app.selectedLanguage);
    const lesson = course.chapters.find((chapter) => chapter.id === app.activeLessonParams?.chapterId)?.lessons.find((candidate) => candidate.id === app.activeLessonParams?.lessonId);
    if (lesson) return <LessonScreen lesson={lesson} />;
  }
  const screens: Record<TabRoute, React.ReactNode> = {
    home: <HomeScreen />,
    path: <PathScreen />,
    review: <ReviewScreen />,
    stats: <StatsScreen />,
    settings: <SettingsScreen />,
  };
  return (
    <View style={styles.mainContainer}>
      <View style={styles.tabContent}>{screens[app.currentTab]}</View>
      <BottomTabBar currentTab={app.currentTab} onSelectTab={app.setTab} reviewDueCount={app.reviewCards.filter((card) => card.dueToday).length} />
    </View>
  );
}

export default function App() {
  return (
    <AppProvider>
      <SafeAreaView style={styles.safeArea}>
        <StatusBar style="dark" backgroundColor={Colors.background} />
        <MainNavigator />
      </SafeAreaView>
    </AppProvider>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: Colors.background },
  mainContainer: { flex: 1, backgroundColor: Colors.background },
  tabContent: { flex: 1 },
  screen: { flex: 1, backgroundColor: Colors.background },
  content: { padding: Spacing.lg, gap: Spacing.md, paddingBottom: 112 },
  pathMapContent: { padding: Spacing.lg, gap: Spacing.md, paddingBottom: 128 },
  screenHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.xs },
  eyebrow: { ...Typography.labelSmall, color: Colors.primary, textTransform: 'uppercase', marginBottom: Spacing.xs },
  screenTitle: { ...Typography.headlineLarge, color: Colors.text },
  cardTitle: { ...Typography.headlineSmall, color: Colors.text, marginBottom: Spacing.xs },
  mutedText: { ...Typography.bodySmall, color: Colors.textMuted, lineHeight: 18 },
  bodyText: { ...Typography.bodyMedium, color: Colors.text, lineHeight: 21 },
  flexOne: { flex: 1 },
  mapIntroCard: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, backgroundColor: Colors.octopusPurpleLight, borderWidth: 1, borderColor: Colors.octopusPink, borderRadius: Radius.lg, padding: Spacing.md },
  mapIntroCopy: { flex: 1, gap: Spacing.xxs },
  mapIntroTitle: { ...Typography.headlineSmall, color: Colors.octopusPurpleDark },
  courseMap: { position: 'relative', overflow: 'hidden', borderRadius: Radius.xl, padding: Spacing.md, paddingTop: Spacing.lg, paddingBottom: Spacing.xl, backgroundColor: '#EAF1EF', borderWidth: 1, borderColor: '#D6E3DE', gap: Spacing.md },
  mapLabel: { ...Typography.labelSmall, color: Colors.primary, textAlign: 'center', letterSpacing: 1.2, zIndex: 2 },
  mapLabelEnd: { ...Typography.labelSmall, color: Colors.secondary, textAlign: 'center', letterSpacing: 1.1, zIndex: 2, marginTop: Spacing.xs },
  mapTrailSpine: { position: 'absolute', top: 42, bottom: 48, left: '50%', width: 2, backgroundColor: '#C7D9D1', borderRadius: Radius.round },
  mapUnit: { position: 'relative', zIndex: 1 },
  mapTrail: { position: 'absolute', top: -Spacing.md, height: Spacing.lg, width: '50%', borderTopWidth: 2, borderTopColor: '#C7D9D1', borderStyle: 'dashed' },
  mapTrailLeft: { right: 0, transform: [{ rotate: '-8deg' }] },
  mapTrailRight: { left: 0, transform: [{ rotate: '8deg' }] },
  islandCard: { width: '88%', backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: Radius.xl, padding: Spacing.md, gap: Spacing.sm, shadowColor: Colors.text, shadowOffset: { width: 0, height: 3 }, shadowOpacity: 0.06, shadowRadius: 7, elevation: 2 },
  islandCardLeft: { alignSelf: 'flex-start' },
  islandCardRight: { alignSelf: 'flex-end' },
  islandTopLine: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  unitPill: { alignSelf: 'flex-start', backgroundColor: Colors.octopusPurpleLight, borderRadius: Radius.round, paddingHorizontal: Spacing.sm, paddingVertical: Spacing.xxs },
  unitPillText: { ...Typography.labelSmall, color: Colors.octopusPurpleDark, fontWeight: '800' },
  islandTitle: { ...Typography.headlineSmall, color: Colors.text },
  islandSummary: { ...Typography.bodySmall, color: Colors.textMuted, lineHeight: 18 },
  lessonIslandRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, paddingTop: Spacing.xs },
  lessonIslandButton: { alignItems: 'center', width: 64, gap: Spacing.xxs },
  lessonIsland: { width: 38, height: 38, borderRadius: Radius.round, backgroundColor: Colors.surfaceVariant, borderWidth: 2, borderColor: Colors.outlineVariant, alignItems: 'center', justifyContent: 'center' },
  lessonIslandCurrent: { backgroundColor: Colors.octopusPurpleLight, borderColor: Colors.octopusPurple },
  lessonIslandDone: { backgroundColor: Colors.success, borderColor: Colors.success },
  lessonIslandText: { ...Typography.labelLarge, color: Colors.textMuted },
  lessonIslandTextDone: { color: Colors.onSuccess },
  lessonIslandLabel: { ...Typography.labelSmall, color: Colors.textMuted, textAlign: 'center', width: 64 },
  chapterCard: { gap: Spacing.md },
  chapterHeading: { flexDirection: 'row', gap: Spacing.md, alignItems: 'flex-start' },
  chapterNumber: { width: 32, height: 32, borderRadius: Radius.round, backgroundColor: Colors.primaryContainer, alignItems: 'center', justifyContent: 'center' },
  chapterNumberText: { ...Typography.labelLarge, color: Colors.primaryDark },
  lessonList: { gap: Spacing.xs },
  lessonRow: { flexDirection: 'row', gap: Spacing.sm, alignItems: 'center', paddingVertical: Spacing.sm, borderTopWidth: 1, borderTopColor: Colors.border },
  lessonDot: { width: 24, height: 24, borderRadius: Radius.round, borderWidth: 1, borderColor: Colors.outline, alignItems: 'center', justifyContent: 'center' },
  lessonDotDone: { backgroundColor: Colors.success, borderColor: Colors.success },
  lessonDotText: { color: Colors.textMuted, fontWeight: '700' },
  lessonTitle: { ...Typography.labelLarge, color: Colors.text },
  chevron: { fontSize: 24, color: Colors.textMuted },
  emptyCard: { alignItems: 'center', gap: Spacing.sm, paddingVertical: Spacing.xxxl },
  emptyIcon: { color: Colors.success, fontSize: 42, fontWeight: '700' },
  reviewItem: { gap: Spacing.md },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  reviewQuestion: { ...Typography.headlineSmall, color: Colors.text, lineHeight: 25 },
  codeBlock: { backgroundColor: Colors.codeSurface, borderColor: Colors.codeBorder, borderWidth: 1, borderRadius: Radius.md, padding: Spacing.md },
  codeText: { ...Typography.code, color: Colors.codeText },
  answerBox: { backgroundColor: Colors.successContainer, borderRadius: Radius.md, padding: Spacing.md, gap: Spacing.xs },
  answerLabel: { ...Typography.labelSmall, color: Colors.success, textTransform: 'uppercase' },
  statGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm },
  statTile: { width: '48%', minHeight: 92, justifyContent: 'center' },
  statValue: { fontSize: 28, lineHeight: 34, fontWeight: '800' },
  statLabel: { ...Typography.bodySmall, color: Colors.textMuted },
  progressSpacing: { marginVertical: Spacing.md },
  accountRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.md },
  settingsLanguageGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.md },
  settingsLanguageButton: { minWidth: '47%', flexGrow: 1, flexDirection: 'row', alignItems: 'center', gap: Spacing.xs, paddingHorizontal: Spacing.sm, paddingVertical: Spacing.sm, borderRadius: Radius.md, borderWidth: 1, borderColor: Colors.border, backgroundColor: Colors.surface },
  settingsLanguageButtonSelected: { backgroundColor: Colors.octopusPurpleLight, borderColor: Colors.octopusPurple },
  settingsLanguageIcon: { width: 24, textAlign: 'center', color: Colors.textMuted, fontWeight: '800' },
  settingsLanguageIconSelected: { color: Colors.octopusPurpleDark },
  settingsLanguageLabel: { ...Typography.labelMedium, color: Colors.textMuted, flex: 1 },
  settingsLanguageLabelSelected: { color: Colors.octopusPurpleDark },
  settingsLanguageCheck: { ...Typography.labelMedium, color: Colors.success },
  byokNotice: { ...Typography.bodySmall, color: Colors.textMuted, lineHeight: 18, marginTop: Spacing.sm },
  byokForm: { gap: Spacing.sm, marginTop: Spacing.sm },
  providerRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs },
  providerChip: { paddingHorizontal: Spacing.sm, paddingVertical: Spacing.sm, borderRadius: Radius.round, borderWidth: 1, borderColor: Colors.border, backgroundColor: Colors.surface },
  providerChipSelected: { backgroundColor: Colors.octopusPurpleLight, borderColor: Colors.octopusPurple },
  providerChipText: { ...Typography.labelSmall, color: Colors.textMuted },
  providerChipTextSelected: { color: Colors.octopusPurpleDark, fontWeight: '800' },
  byokSavedText: { ...Typography.bodySmall, color: Colors.success },
  syncLine: { ...Typography.bodySmall, color: Colors.text, marginTop: Spacing.sm },
  lessonScreen: { flex: 1, backgroundColor: Colors.background },
  lessonHeader: { flexDirection: 'row', alignItems: 'center', gap: Spacing.md, padding: Spacing.lg, borderBottomWidth: 1, borderBottomColor: Colors.border },
  closeLesson: { fontSize: 30, lineHeight: 30, color: Colors.textMuted },
  lessonProgressTrack: { flex: 1, height: 8, backgroundColor: Colors.border, borderRadius: Radius.round, overflow: 'hidden' },
  lessonProgressFill: { height: '100%', backgroundColor: Colors.primary, borderRadius: Radius.round },
  lessonCount: { ...Typography.labelMedium, color: Colors.textMuted },
  lessonContent: { padding: Spacing.lg, gap: Spacing.md, paddingBottom: Spacing.xxxl },
  lessonStreakRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.xs, alignSelf: 'flex-start', paddingHorizontal: Spacing.sm, paddingVertical: Spacing.xs, borderRadius: Radius.round, backgroundColor: Colors.surfaceVariant },
  lessonStreakIcon: { fontSize: 13 },
  lessonStreakText: { ...Typography.labelSmall, color: Colors.tertiary },
  summaryHeaderLabel: { ...Typography.labelLarge, color: Colors.text, flex: 1, textAlign: 'center' },
  summaryContent: { padding: Spacing.lg, gap: Spacing.md, alignItems: 'center', paddingBottom: Spacing.huge },
  summaryConfetti: { flexDirection: 'row', gap: Spacing.xl, marginTop: Spacing.sm },
  confettiText: { color: Colors.octopusGold, fontSize: 26, fontWeight: '800' },
  summaryEyebrow: { ...Typography.labelSmall, color: Colors.octopusPurpleDark, textTransform: 'uppercase', marginTop: Spacing.xs },
  summaryTitle: { ...Typography.headlineLarge, color: Colors.text, textAlign: 'center' },
  summaryLesson: { ...Typography.bodyMedium, color: Colors.textMuted, textAlign: 'center' },
  summaryBadges: { flexDirection: 'row', gap: Spacing.xs, flexWrap: 'wrap', justifyContent: 'center' },
  summaryScoreCard: { width: '100%', alignItems: 'center', backgroundColor: Colors.surface, gap: Spacing.xs },
  summaryScoreLabel: { ...Typography.labelSmall, color: Colors.textMuted, textTransform: 'uppercase' },
  summaryScoreValue: { fontSize: 42, lineHeight: 48, fontWeight: '800', color: Colors.primary },
  summaryXpRow: { width: '100%', flexDirection: 'row', justifyContent: 'space-between', borderTopWidth: 1, borderTopColor: Colors.border, paddingTop: Spacing.sm, marginTop: Spacing.xs },
  summaryXpLabel: { ...Typography.bodySmall, color: Colors.textMuted },
  summaryXpValue: { ...Typography.labelMedium, color: Colors.text },
  lessonHeading: { ...Typography.headlineLarge, color: Colors.text },
  lessonIntro: { ...Typography.bodyMedium, color: Colors.textMuted, lineHeight: 22 },
  questionCard: { gap: Spacing.md, marginTop: Spacing.sm },
  questionText: { ...Typography.headlineSmall, color: Colors.text, lineHeight: 26 },
  optionsList: { gap: Spacing.sm },
  option: { flexDirection: 'row', alignItems: 'center', gap: Spacing.md, padding: Spacing.md, borderRadius: Radius.md, borderWidth: 1, borderColor: Colors.border, backgroundColor: Colors.surface },
  optionSelected: { borderColor: Colors.primary, backgroundColor: Colors.primaryContainer },
  optionCorrect: { borderColor: Colors.success, backgroundColor: Colors.successContainer },
  optionMarker: { width: 26, height: 26, borderRadius: Radius.round, borderWidth: 1, borderColor: Colors.outline, alignItems: 'center', justifyContent: 'center' },
  optionMarkerSelected: { borderColor: Colors.primary, backgroundColor: Colors.primary },
  optionMarkerText: { ...Typography.labelMedium, color: Colors.textMuted },
  optionText: { ...Typography.bodyMedium, color: Colors.text, flex: 1 },
  puzzleEditor: { gap: Spacing.sm },
  blankGroup: { gap: Spacing.xs },
  blankLabel: { ...Typography.labelMedium, color: Colors.text },
  choiceWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs },
  choiceChip: { borderWidth: 1, borderColor: Colors.border, borderRadius: Radius.round, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, backgroundColor: Colors.surface },
  choiceChipSelected: { borderColor: Colors.primary, backgroundColor: Colors.primaryContainer },
  choiceChipText: { ...Typography.labelMedium, color: Colors.text },
  input: { minHeight: 44, borderWidth: 1, borderColor: Colors.border, borderRadius: Radius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, backgroundColor: Colors.surfaceContainerLow, color: Colors.text, ...Typography.bodyMedium },
  multilineInput: { minHeight: 88, textAlignVertical: 'top' },
  inputLabel: { ...Typography.labelMedium, color: Colors.text },
  lessonHint: { ...Typography.bodySmall, textAlign: 'center', color: Colors.textMuted },
});
