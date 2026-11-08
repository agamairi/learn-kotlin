import React, { useEffect, useRef } from 'react';
import { Animated, Text, View, StyleSheet } from 'react-native';
import type { SvgProps } from 'react-native-svg';
import AskingMascot from '../../../assets/octopus/octopus-asking.svg';
import PerfectLessonMascot from '../../../assets/octopus/octopus-perfect-lesson.svg';
import SuperAccuracyMascot from '../../../assets/octopus/octopus-super-accuracy.svg';
import { Colors } from '../../theme/colors';

export type OctopusMood = 'greeting' | 'thinking' | 'celebrating' | 'studying' | 'encouraging' | 'surprised';

export interface OctopusMascotProps {
  mood?: OctopusMood;
  size?: number;
  showBadge?: boolean;
  animate?: boolean;
  message?: string;
}

const MOOD_LABEL: Record<OctopusMood, string> = {
  greeting: 'Hi, learner!',
  thinking: 'Think it through',
  celebrating: 'You did it!',
  studying: 'Focus mode',
  encouraging: 'You got this!',
  surprised: 'Nice move!',
};

const ART_BY_MOOD: Record<OctopusMood, React.ComponentType<SvgProps>> = {
  greeting: AskingMascot,
  thinking: AskingMascot,
  studying: AskingMascot,
  celebrating: PerfectLessonMascot,
  encouraging: SuperAccuracyMascot,
  surprised: SuperAccuracyMascot,
};

export const OctopusMascot: React.FC<OctopusMascotProps> = ({
  mood = 'greeting',
  size = 72,
  showBadge = false,
  animate = true,
  message,
}) => {
  const bob = useRef(new Animated.Value(0)).current;
  const artHeight = size * (2 / 3);
  const MascotArt = ART_BY_MOOD[mood];

  useEffect(() => {
    if (!animate) {
      bob.setValue(0);
      return undefined;
    }

    const animation = Animated.loop(
      Animated.sequence([
        Animated.timing(bob, { toValue: -4, duration: 850, useNativeDriver: true }),
        Animated.timing(bob, { toValue: 0, duration: 850, useNativeDriver: true }),
        Animated.timing(bob, { toValue: 3, duration: 700, useNativeDriver: true }),
        Animated.timing(bob, { toValue: 0, duration: 700, useNativeDriver: true }),
      ]),
    );
    animation.start();
    return () => animation.stop();
  }, [animate, bob]);

  return (
    <View style={[styles.wrapper, { minWidth: message ? size + 104 : size, minHeight: artHeight + (showBadge ? 18 : 0) }]}>
      <Animated.View
        style={[styles.container, { width: size, height: artHeight, transform: [{ translateY: bob }] }]}
        accessible={true}
        accessibilityLabel={`Octo Coach, ${MOOD_LABEL[mood]}`}
        accessibilityRole="image"
      >
        <MascotArt width={size} height={artHeight} />
      </Animated.View>
      {message ? (
        <View style={styles.messageBubble}>
          <Text style={styles.messageText}>{message}</Text>
        </View>
      ) : null}
      {showBadge ? (
        <View style={styles.nameTag}>
          <Text style={styles.nameTagText}>Octo Coach</Text>
        </View>
      ) : null}
    </View>
  );
};

const styles = StyleSheet.create({
  wrapper: { alignItems: 'center', justifyContent: 'center', position: 'relative', flexDirection: 'row', gap: 8 },
  container: { alignItems: 'center', justifyContent: 'center', position: 'relative' },
  nameTag: { position: 'absolute', bottom: -1, alignSelf: 'center', paddingHorizontal: 7, paddingVertical: 2, backgroundColor: Colors.surfaceVariant, borderRadius: 8 },
  nameTagText: { fontSize: 10, fontWeight: '700', color: Colors.octopusPurpleDark },
  messageBubble: { maxWidth: 150, paddingHorizontal: 10, paddingVertical: 8, backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 14 },
  messageText: { color: Colors.text, fontSize: 12, lineHeight: 16, fontWeight: '600' },
});
