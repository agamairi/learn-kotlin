import React, { useEffect, useRef } from 'react';
import { Animated, Text, TouchableOpacity, View, StyleSheet } from 'react-native';
import { Colors } from '../../theme/colors';
import { Radius, Spacing } from '../../theme/spacing';
import { Typography } from '../../theme/typography';
import { OctopusMascot } from '../octopus/OctopusMascot';

export interface StreakCelebrationProps {
  streak: number | null;
  onDismiss: () => void;
}

export const StreakCelebration: React.FC<StreakCelebrationProps> = ({ streak, onDismiss }) => {
  const slide = useRef(new Animated.Value(-24)).current;
  const opacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (streak === null) return undefined;
    slide.setValue(-24);
    opacity.setValue(0);
    const entrance = Animated.parallel([
      Animated.timing(slide, { toValue: 0, duration: 300, useNativeDriver: true }),
      Animated.timing(opacity, { toValue: 1, duration: 220, useNativeDriver: true }),
    ]);
    entrance.start();
    const timer = setTimeout(onDismiss, 2600);
    return () => {
      clearTimeout(timer);
      entrance.stop();
    };
  }, [onDismiss, opacity, slide, streak]);

  if (streak === null) return null;

  const headline = streak >= 5 ? `${streak} in a row!` : '3 in a row!';
  const message = streak >= 5 ? 'Your reasoning is on fire.' : 'Nice start. Keep the chain going.';

  return (
    <Animated.View style={[styles.container, { opacity, transform: [{ translateY: slide }] }]}>
      <View style={styles.sparkleLeft}><Text style={styles.sparkleText}>✦</Text></View>
      <OctopusMascot mood="celebrating" size={58} />
      <View style={styles.copy}>
        <Text style={styles.eyebrow}>Answer streak</Text>
        <Text style={styles.headline}>{headline}</Text>
        <Text style={styles.message}>{message}</Text>
      </View>
      <View style={styles.sparkleRight}><Text style={styles.sparkleText}>✧</Text></View>
      <TouchableOpacity onPress={onDismiss} style={styles.dismiss} accessibilityRole="button" accessibilityLabel="Dismiss streak celebration">
        <Text style={styles.dismissText}>×</Text>
      </TouchableOpacity>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: { flexDirection: 'row', alignItems: 'center', marginHorizontal: Spacing.lg, marginTop: Spacing.sm, padding: Spacing.sm, borderRadius: Radius.lg, borderWidth: 1, borderColor: Colors.octopusPink, backgroundColor: Colors.octopusPurpleLight, position: 'relative' },
  copy: { flex: 1, paddingHorizontal: Spacing.xs },
  eyebrow: { ...Typography.labelSmall, color: Colors.octopusPurpleDark, textTransform: 'uppercase' },
  headline: { ...Typography.headlineSmall, color: Colors.octopusPurpleDark },
  message: { ...Typography.bodySmall, color: Colors.textMuted },
  sparkleLeft: { position: 'absolute', left: 8, top: 5 },
  sparkleRight: { position: 'absolute', right: 28, bottom: 5 },
  sparkleText: { color: Colors.octopusGold, fontSize: 16, fontWeight: '800' },
  dismiss: { paddingHorizontal: Spacing.xs, paddingVertical: Spacing.xs },
  dismissText: { color: Colors.textMuted, fontSize: 20, lineHeight: 20 },
});
