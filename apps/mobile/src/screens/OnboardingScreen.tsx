import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  TouchableOpacity,
} from 'react-native';
import { Colors } from '../theme/colors';
import { Typography } from '../theme/typography';
import { Spacing, Radius } from '../theme/spacing';
import { Button } from '../components/common/Button';
import { Card } from '../components/common/Card';
import { Badge } from '../components/common/Badge';
import { OctopusMascot } from '../components/octopus/OctopusMascot';
import { LANGUAGE_OPTIONS, getLanguageOption } from '../data/courseCatalog';
import type { SupportedLanguage } from '../types/languages';
import { useApp } from '../domain/state/AppContext';

type OnboardingStep = 'welcome' | 'language' | 'account';

export const OnboardingScreen: React.FC = () => {
  const { continueAsGuest, signIn, selectedLanguage, setSelectedLanguage } = useApp();
  const [step, setStep] = useState<OnboardingStep>('welcome');
  const [showSignInForm, setShowSignInForm] = useState(false);
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const selectedOption = getLanguageOption(selectedLanguage);

  const handleSignIn = () => {
    if (email.trim().length > 0) {
      signIn(email.trim(), displayName.trim() || undefined);
    }
  };

  const selectLanguage = (language: SupportedLanguage) => {
    setSelectedLanguage(language);
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
      <View style={styles.progressRow}>
        {(['welcome', 'language', 'account'] as OnboardingStep[]).map((item, index) => (
          <View key={item} style={[styles.progressDot, index <= ['welcome', 'language', 'account'].indexOf(step) && styles.progressDotActive]} />
        ))}
        <Text style={styles.progressLabel}>{step === 'welcome' ? 'Welcome' : step === 'language' ? 'Choose a track' : 'Make it yours'}</Text>
      </View>

      <View style={styles.heroSection}>
        <OctopusMascot mood={step === 'language' ? 'thinking' : step === 'account' ? 'encouraging' : 'greeting'} size={86} showBadge={true} />
        <Text style={styles.appTitle}>{step === 'welcome' ? 'Learn with Octo' : step === 'language' ? 'Pick your first coast' : 'Ready when you are'}</Text>
        <Text style={styles.tagline}>
          {step === 'welcome'
            ? 'Bite-sized programming and interview practice. Guided reasoning, no intimidating IDEs.'
            : step === 'language'
              ? 'Choose one language to shape your first path. You can switch tracks any time.'
              : 'Start locally as a guest, or sign in now so your streak can follow you to the cloud.'}
        </Text>
      </View>

      {step === 'welcome' ? (
        <>
          <Card style={styles.pillarsCard}>
            <Pillar icon="🧠" title="Guided reasoning" description="Learn the method behind the answer with examples and small decisions." />
            <Pillar icon="↻" title="Spaced review" description="Return to the ideas that need another retrieval attempt." />
            <Pillar icon="☁️" title="Offline-first sync" description="Practice locally, then sync progress when your account is online." />
          </Card>
          <View style={styles.actionSection}>
            <Button title="Choose my track" onPress={() => setStep('language')} variant="primary" size="lg" />
            <Text style={styles.guestNote}>It takes less than a minute. No coding environment required.</Text>
            <Button title="I already have an account" onPress={() => { setStep('account'); setShowSignInForm(true); }} variant="outline" size="lg" />
          </View>
        </>
      ) : null}

      {step === 'language' ? (
        <>
          <View style={styles.languageGrid}>
            {LANGUAGE_OPTIONS.map((option) => {
              const selected = option.id === selectedLanguage;
              return (
                <TouchableOpacity
                  key={option.id}
                  style={[styles.languageCard, selected && styles.languageCardSelected]}
                  onPress={() => selectLanguage(option.id)}
                  activeOpacity={0.8}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  accessibilityLabel={`${option.label}, ${option.description}`}
                >
                  <View style={[styles.languageIcon, selected && styles.languageIconSelected]}><Text style={styles.languageIconText}>{option.icon}</Text></View>
                  <Text style={styles.languageTitle}>{option.label}</Text>
                  <Text style={styles.languageDescription}>{option.description}</Text>
                  {selected ? <Badge label="Selected" variant="success" /> : null}
                </TouchableOpacity>
              );
            })}
          </View>
          <View style={styles.actionSection}>
            <Button title={`Continue with ${selectedOption.label}`} onPress={() => setStep('account')} variant="primary" size="lg" />
            <Button title="Back" onPress={() => setStep('welcome')} variant="ghost" size="sm" />
          </View>
        </>
      ) : null}

      {step === 'account' ? (
        !showSignInForm ? (
          <>
            <Card style={styles.trackCard}>
              <View style={styles.trackCardRow}>
                <Text style={styles.trackCardIcon}>{selectedOption.icon}</Text>
                <View style={styles.pillarTextWrapper}>
                  <Text style={styles.trackCardTitle}>{selectedOption.label} coast selected</Text>
                  <Text style={styles.pillarDesc}>{selectedOption.description}. You can change this later in Settings.</Text>
                </View>
                <Badge label="Ready" variant="sage" />
              </View>
            </Card>
            <View style={styles.actionSection}>
              <Button title="Start as Guest" onPress={continueAsGuest} variant="primary" size="lg" />
              <Text style={styles.guestNote}>Your progress stays on this device until you create an account.</Text>
              <Button title="Sign In / Create Account" onPress={() => setShowSignInForm(true)} variant="outline" size="lg" />
              <Button title="Change track" onPress={() => setStep('language')} variant="ghost" size="sm" />
            </View>
          </>
        ) : (
          <Card style={styles.authCard}>
            <View style={styles.authHeadingRow}>
              <View style={styles.flexOne}>
                <Text style={styles.authTitle}>Create your learner account</Text>
                <Text style={styles.authSubtitle}>Your {selectedOption.label} progress can sync across devices.</Text>
              </View>
              <Badge label={selectedOption.shortLabel} variant="ochre" />
            </View>
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Email address</Text>
              <TextInput style={styles.input} placeholder="alex.dev@example.com" placeholderTextColor={Colors.textSecondary} value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
            </View>
            <View style={styles.inputGroup}>
              <Text style={styles.inputLabel}>Learner name (optional)</Text>
              <TextInput style={styles.input} placeholder="Alex" placeholderTextColor={Colors.textSecondary} value={displayName} onChangeText={setDisplayName} />
            </View>
            <View style={styles.authActions}>
              <Button title="Sign In & Start Learning" onPress={handleSignIn} variant="primary" size="md" disabled={email.trim().length === 0} />
              <Button title="Use Guest Mode Instead" onPress={continueAsGuest} variant="outline" size="sm" />
              <Button title="Back" onPress={() => setShowSignInForm(false)} variant="ghost" size="sm" />
            </View>
          </Card>
        )
      ) : null}

      <View style={styles.footerNote}>
        <Text style={styles.footerText}>Local grading · Cloud sync when you want it · BYOK stays on this device</Text>
      </View>
    </ScrollView>
  );
};

function Pillar({ icon, title, description }: { icon: string; title: string; description: string }) {
  return (
    <View style={styles.pillarRow}>
      <Text style={styles.pillarIcon}>{icon}</Text>
      <View style={styles.pillarTextWrapper}>
        <Text style={styles.pillarTitle}>{title}</Text>
        <Text style={styles.pillarDesc}>{description}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  content: { paddingHorizontal: Spacing.xl, paddingTop: Spacing.lg, paddingBottom: Spacing.huge, alignItems: 'center', gap: Spacing.lg },
  progressRow: { width: '100%', flexDirection: 'row', alignItems: 'center', gap: Spacing.xs },
  progressDot: { width: 28, height: 5, borderRadius: Radius.round, backgroundColor: Colors.border },
  progressDotActive: { backgroundColor: Colors.octopusPurple },
  progressLabel: { ...Typography.labelSmall, color: Colors.textMuted, marginLeft: Spacing.xs },
  heroSection: { alignItems: 'center', gap: Spacing.sm, marginTop: Spacing.sm },
  appTitle: { ...Typography.display, color: Colors.text, textAlign: 'center' },
  tagline: { ...Typography.bodyMedium, color: Colors.textMuted, textAlign: 'center', maxWidth: 330, lineHeight: 22 },
  pillarsCard: { width: '100%', maxWidth: 380, gap: Spacing.md, backgroundColor: Colors.surface },
  pillarRow: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.md },
  pillarIcon: { fontSize: 22, lineHeight: 26 },
  pillarTextWrapper: { flex: 1 },
  pillarTitle: { ...Typography.headlineSmall, fontSize: 15, color: Colors.text, marginBottom: 2 },
  pillarDesc: { ...Typography.bodySmall, color: Colors.textMuted, lineHeight: 18 },
  actionSection: { width: '100%', maxWidth: 380, gap: Spacing.sm, marginTop: Spacing.sm },
  guestNote: { ...Typography.bodySmall, fontSize: 11, color: Colors.textSecondary, textAlign: 'center', marginBottom: Spacing.xs },
  languageGrid: { width: '100%', maxWidth: 390, flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, justifyContent: 'space-between' },
  languageCard: { width: '48%', minHeight: 148, gap: Spacing.xs, padding: Spacing.md, borderRadius: Radius.lg, borderWidth: 1, borderColor: Colors.border, backgroundColor: Colors.surface },
  languageCardSelected: { borderColor: Colors.octopusPurple, backgroundColor: Colors.octopusPurpleLight },
  languageIcon: { width: 36, height: 36, borderRadius: Radius.md, alignItems: 'center', justifyContent: 'center', backgroundColor: Colors.surfaceVariant },
  languageIconSelected: { backgroundColor: Colors.octopusPurple },
  languageIconText: { color: Colors.octopusPurpleDark, fontSize: 18, fontWeight: '800' },
  languageTitle: { ...Typography.headlineSmall, color: Colors.text, fontSize: 16 },
  languageDescription: { ...Typography.bodySmall, color: Colors.textMuted, lineHeight: 17, flex: 1 },
  trackCard: { width: '100%', maxWidth: 380, backgroundColor: Colors.octopusPurpleLight },
  trackCardRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm },
  trackCardIcon: { fontSize: 30, color: Colors.octopusPurpleDark },
  trackCardTitle: { ...Typography.headlineSmall, color: Colors.octopusPurpleDark, marginBottom: Spacing.xxs },
  authCard: { width: '100%', maxWidth: 380, gap: Spacing.md, backgroundColor: Colors.surface },
  authHeadingRow: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.sm },
  authTitle: { ...Typography.headlineMedium, color: Colors.text },
  authSubtitle: { ...Typography.bodySmall, color: Colors.textMuted, marginTop: Spacing.xs },
  inputGroup: { gap: Spacing.xs },
  inputLabel: { ...Typography.labelMedium, color: Colors.text },
  input: { height: 44, borderWidth: 1, borderColor: Colors.border, borderRadius: Radius.md, paddingHorizontal: Spacing.md, backgroundColor: Colors.surfaceContainerLow, color: Colors.text, ...Typography.bodyMedium },
  authActions: { gap: Spacing.xs, marginTop: Spacing.xs },
  flexOne: { flex: 1 },
  footerNote: { marginTop: Spacing.md },
  footerText: { ...Typography.bodySmall, fontSize: 11, color: Colors.textSecondary, textAlign: 'center' },
});
