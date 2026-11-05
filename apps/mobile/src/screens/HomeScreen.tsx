import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import { Colors } from '../theme/colors';
import { Typography } from '../theme/typography';
import { Spacing, Radius } from '../theme/spacing';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Badge } from '../components/common/Badge';
import { ProgressBar } from '../components/common/ProgressBar';
import { OctopusMascot } from '../components/octopus/OctopusMascot';
import { SyncStatusBadge } from '../components/sync/SyncStatusBadge';
import { SyncModal } from '../components/sync/SyncModal';
import { useApp } from '../domain/state/AppContext';
import { getCourse, getLanguageOption } from '../data/courseCatalog';
import { DefaultDailyGoalTracker } from '@learn/core';

export const HomeScreen: React.FC = () => {
  const {
    user,
    xpToday,
    dailyTargetXp,
    currentStreak,
    freezesHeld,
    reviewCards,
    syncStatus,
    pendingOutboxCount,
    lastSyncedAt,
    setTab,
    startLesson,
    triggerManualSync,
    completedLessonIds,
    selectedLanguage,
  } = useApp();

  const [syncModalVisible, setSyncModalVisible] = useState(false);

  // Daily goal calculation via @learn/core DefaultDailyGoalTracker
  const goalTracker = new DefaultDailyGoalTracker(dailyTargetXp);
  const goalRatio = goalTracker.progressRatio(xpToday, dailyTargetXp);
  const goalMet = goalTracker.isGoalMet(xpToday, dailyTargetXp);
  const remainingXp = Math.max(0, dailyTargetXp - xpToday);

  // Due review cards count
  const dueCardsCount = reviewCards.filter((card) => card.dueToday).length;

  const course = getCourse(selectedLanguage);
  const language = getLanguageOption(selectedLanguage);
  const nextChapter = course.chapters[0];
  const nextLesson = nextChapter?.lessons[0];
  const nextChapterId = nextChapter?.id ?? '';
  const nextLessonId = nextLesson?.id ?? '';
  const isLessonCompleted = completedLessonIds.includes(nextLessonId);

  return (
    <View style={styles.container}>
      {/* Top Header Bar */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Text style={styles.headerIcon}>⚡</Text>
          <Text style={styles.headerTitle}>Learn {language.label}</Text>
        </View>

        <View style={styles.headerRight}>
          {/* Streak indicator */}
          <View style={styles.indicatorPill}>
            <Text style={styles.flameIcon}>🔥</Text>
            <Text style={styles.indicatorText}>{currentStreak}</Text>
          </View>

          {/* Freeze indicator */}
          <View style={styles.indicatorPill} accessible={true} accessibilityLabel={`${freezesHeld} streak freeze available`}>
            <Text style={styles.freezeIcon}>❄</Text>
            <Text style={styles.indicatorText}>{freezesHeld}</Text>
          </View>

          {/* Visible sync status affordance */}
          <SyncStatusBadge
            status={syncStatus}
            pendingCount={pendingOutboxCount}
            onPress={() => setSyncModalVisible(true)}
          />
        </View>
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Octo Coach Greeting Card */}
        <Card style={styles.greetingCard}>
          <View style={styles.greetingTextCol}>
            <View style={styles.checkInRow}>
              <View style={styles.checkInDot} />
              <Text style={styles.checkInLabel}>Daily check-in</Text>
            </View>
            <Text style={styles.greetingTitle}>
              {user.displayName ? `Ready, ${user.displayName}?` : 'Ready to code?'}
            </Text>
            <Text style={styles.greetingBody}>
              Practice {language.description.toLowerCase()} with Octo today.
            </Text>
          </View>
          <OctopusMascot mood="greeting" size={72} />
        </Card>

        {/* Primary Next Lesson Hero Card */}
        <Card style={styles.heroLessonCard}>
          <View style={styles.heroMetaRow}>
            <Text style={styles.heroChapterTag}>{nextChapter?.title ?? `${language.label} foundations`}</Text>
            <Badge
              label={isLessonCompleted ? 'Completed' : 'Next Up'}
              variant={isLessonCompleted ? 'success' : 'sage'}
            />
          </View>

          <View style={styles.heroContent}>
            <Text style={styles.heroLessonTitle}>{nextLesson?.title ?? `Start ${language.label}`}</Text>
            <Text style={styles.heroLessonDesc}>
              {nextLesson?.explanation ?? language.description}
            </Text>
          </View>

          <View style={styles.exercisePillsRow}>
            <Badge label={`${nextLesson?.exercises.length ?? 8} Reasoning Cards`} variant="neutral" />
            <Badge label="No IDE required" variant="neutral" />
          </View>

          <Button
            title={isLessonCompleted ? 'Practice Again' : 'Continue Lesson'}
            onPress={() => startLesson(nextChapterId, nextLessonId)}
            variant="primary"
            size="lg"
            rightIcon={<Text style={styles.btnArrow}>→</Text>}
          />
        </Card>

        {/* Daily Practice Goal Card */}
        <Card style={styles.goalCard}>
          <View style={styles.goalHeaderRow}>
            <View style={styles.goalTitleGroup}>
              <Text style={styles.goalIcon}>🎯</Text>
              <Text style={styles.goalTitle}>Daily Practice Goal</Text>
            </View>
            <Text style={styles.goalNumbers}>
              {xpToday} / {dailyTargetXp} XP
            </Text>
          </View>

          <ProgressBar progress={goalRatio} height={8} />

          <View style={styles.goalFooterRow}>
            <Text style={styles.goalFooterText}>
              {goalMet
                ? 'Goal achieved! You are keeping your streak alive.'
                : `${remainingXp} XP remaining to achieve daily streak goal.`}
            </Text>
            <Text style={[styles.goalPercentage, { color: goalMet ? Colors.success : Colors.primary }]}>
              {Math.round(goalRatio * 100)}%
            </Text>
          </View>
        </Card>

        {/* Spaced Repetition / Review Queue Card */}
        <Card style={styles.reviewCard}>
          <View style={styles.reviewLeft}>
            <View style={styles.reviewIconCircle}>
              <Text style={styles.reviewCircleIcon}>↻</Text>
            </View>
            <View style={styles.reviewTextWrapper}>
              <View style={styles.reviewHeaderRow}>
                <Text style={styles.reviewTitle}>Spaced Review</Text>
                {dueCardsCount > 0 && (
                  <Badge label={`${dueCardsCount} DUE`} variant="clay" />
                )}
              </View>
              <Text style={styles.reviewSubtitle}>
                `val` vs `var`, string templates, null safety
              </Text>
            </View>
          </View>

          <Button
            title="Review"
            onPress={() => setTab('review')}
            variant="outline"
            size="sm"
          />
        </Card>

        {/* Learning Path Snapshot Card */}
        <Card style={styles.pathSnapshotCard}>
          <View style={styles.pathSnapshotHeader}>
            <View style={styles.pathSnapshotTitleRow}>
              <Text style={styles.pathTreeIcon}>❖</Text>
              <Text style={styles.pathSnapshotTitle}>Track: Kotlin Core</Text>
            </View>
            <TouchableOpacity onPress={() => setTab('path')}>
              <Text style={styles.viewPathLink}>View all chapters →</Text>
            </TouchableOpacity>
          </View>

          {/* Stepper visualizer */}
          <View style={styles.stepperContainer}>
            <View style={styles.stepNode}>
              <View style={[styles.stepCircle, styles.stepDone]}>
                <Text style={styles.stepDoneCheck}>✓</Text>
              </View>
              <Text style={styles.stepLabel}>Syntax</Text>
            </View>

            <View style={[styles.stepConnector, styles.connectorDone]} />

            <View style={styles.stepNode}>
              <View style={[styles.stepCircle, styles.stepActive]}>
                <Text style={styles.stepActiveNum}>2</Text>
              </View>
              <Text style={styles.stepLabel}>Control</Text>
            </View>

            <View style={styles.stepConnector} />

            <View style={styles.stepNode}>
              <View style={styles.stepCircle}>
                <Text style={styles.stepMutedNum}>3</Text>
              </View>
              <Text style={styles.stepLabel}>Strings</Text>
            </View>

            <View style={styles.stepConnector} />

            <View style={styles.stepNode}>
              <View style={styles.stepCircle}>
                <Text style={styles.stepMutedNum}>4</Text>
              </View>
              <Text style={styles.stepLabel}>Tests</Text>
            </View>
          </View>
        </Card>
      </ScrollView>

      {/* Sync Status Sheet Modal */}
      <SyncModal
        visible={syncModalVisible}
        status={syncStatus}
        pendingCount={pendingOutboxCount}
        lastSyncedAt={lastSyncedAt}
        userType={user.type}
        userEmail={user.email}
        onClose={() => setSyncModalVisible(false)}
        onTriggerSync={triggerManualSync}
        onGoToSettings={() => setTab('settings')}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.lg,
    paddingTop: Spacing.md,
    paddingBottom: Spacing.sm,
    backgroundColor: Colors.background,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
  },
  headerIcon: {
    fontSize: 18,
    color: Colors.primary,
  },
  headerTitle: {
    ...Typography.headlineSmall,
    color: Colors.text,
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs + 2,
  },
  indicatorPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xxs + 1,
    borderRadius: Radius.round,
    backgroundColor: Colors.surface,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  flameIcon: {
    fontSize: 12,
  },
  freezeIcon: {
    fontSize: 11,
    color: Colors.kodaTeal,
  },
  indicatorText: {
    ...Typography.labelSmall,
    color: Colors.text,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    padding: Spacing.lg,
    gap: Spacing.md,
    paddingBottom: 112,
  },
  greetingCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    padding: Spacing.md + 2,
  },
  greetingTextCol: {
    flex: 1,
    paddingRight: Spacing.sm,
  },
  checkInRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    marginBottom: Spacing.xxs,
  },
  checkInDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: Colors.primary,
  },
  checkInLabel: {
    ...Typography.labelSmall,
    color: Colors.textSecondary,
    textTransform: 'uppercase',
  },
  greetingTitle: {
    ...Typography.headlineMedium,
    color: Colors.text,
  },
  greetingBody: {
    ...Typography.bodySmall,
    color: Colors.textMuted,
    marginTop: 2,
    lineHeight: 18,
  },
  heroLessonCard: {
    backgroundColor: Colors.surface,
    gap: Spacing.md,
  },
  heroMetaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  heroChapterTag: {
    ...Typography.labelSmall,
    color: Colors.secondary,
    textTransform: 'uppercase',
  },
  heroContent: {
    gap: Spacing.xxs,
  },
  heroLessonTitle: {
    ...Typography.headlineMedium,
    color: Colors.text,
  },
  heroLessonDesc: {
    ...Typography.bodyMedium,
    color: Colors.textMuted,
    lineHeight: 20,
  },
  codeInline: {
    fontFamily: 'monospace',
    backgroundColor: Colors.surfaceVariant,
    paddingHorizontal: 4,
    borderRadius: 4,
    color: Colors.text,
  },
  exercisePillsRow: {
    flexDirection: 'row',
    gap: Spacing.xs,
  },
  btnArrow: {
    fontSize: 16,
    color: Colors.onPrimary,
  },
  goalCard: {
    backgroundColor: Colors.surface,
    gap: Spacing.sm,
  },
  goalHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  goalTitleGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
  },
  goalIcon: {
    fontSize: 16,
  },
  goalTitle: {
    ...Typography.labelLarge,
    color: Colors.text,
  },
  goalNumbers: {
    ...Typography.labelMedium,
    fontWeight: '700',
    color: Colors.text,
  },
  goalFooterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  goalFooterText: {
    ...Typography.bodySmall,
    fontSize: 11,
    color: Colors.textMuted,
    flex: 1,
    marginRight: Spacing.sm,
  },
  goalPercentage: {
    ...Typography.labelMedium,
    fontWeight: '700',
  },
  reviewCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    padding: Spacing.md,
  },
  reviewLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.md,
    flex: 1,
  },
  reviewIconCircle: {
    width: 38,
    height: 38,
    borderRadius: Radius.md,
    backgroundColor: Colors.surfaceVariant,
    borderWidth: 1,
    borderColor: Colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reviewCircleIcon: {
    fontSize: 18,
    color: Colors.primary,
  },
  reviewTextWrapper: {
    flex: 1,
  },
  reviewHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
  },
  reviewTitle: {
    ...Typography.headlineSmall,
    fontSize: 15,
    color: Colors.text,
  },
  reviewSubtitle: {
    ...Typography.bodySmall,
    color: Colors.textMuted,
    marginTop: 1,
  },
  pathSnapshotCard: {
    backgroundColor: Colors.surface,
    gap: Spacing.md,
  },
  pathSnapshotHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  pathSnapshotTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
  },
  pathTreeIcon: {
    fontSize: 16,
    color: Colors.kodaTeal,
  },
  pathSnapshotTitle: {
    ...Typography.labelLarge,
    color: Colors.text,
  },
  viewPathLink: {
    ...Typography.labelSmall,
    color: Colors.primary,
  },
  stepperContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.xs,
    paddingTop: Spacing.xs,
  },
  stepNode: {
    alignItems: 'center',
    gap: Spacing.xs,
  },
  stepCircle: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 1,
    borderColor: Colors.border,
    backgroundColor: Colors.background,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepDone: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  stepDoneCheck: {
    color: Colors.onPrimary,
    fontSize: 12,
    fontWeight: '700',
  },
  stepActive: {
    borderColor: Colors.primary,
    borderWidth: 2,
    backgroundColor: Colors.surface,
  },
  stepActiveNum: {
    color: Colors.primary,
    fontSize: 11,
    fontWeight: '700',
  },
  stepMutedNum: {
    color: Colors.textSecondary,
    fontSize: 11,
  },
  stepLabel: {
    ...Typography.labelSmall,
    fontSize: 10,
    color: Colors.textSecondary,
  },
  stepConnector: {
    flex: 1,
    height: 2,
    backgroundColor: Colors.border,
    marginBottom: 16,
  },
  connectorDone: {
    backgroundColor: Colors.primary,
  },
});
