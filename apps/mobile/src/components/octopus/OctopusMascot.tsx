import React, { useEffect, useRef } from 'react';
import { Animated, Text, View, StyleSheet } from 'react-native';
import { Colors } from '../../theme/colors';

export type OctopusMood = 'greeting' | 'thinking' | 'celebrating' | 'studying' | 'encouraging' | 'surprised';

export interface OctopusMascotProps {
  mood?: OctopusMood;
  size?: number;
  showBadge?: boolean;
  animate?: boolean;
  message?: string;
}

const MOOD_EMOJI: Record<OctopusMood, string> = {
  greeting: '👋',
  thinking: '💡',
  celebrating: '🎉',
  studying: '📖',
  encouraging: '✨',
  surprised: '⭐',
};

const MOOD_LABEL: Record<OctopusMood, string> = {
  greeting: 'Hi, learner!',
  thinking: 'Think it through',
  celebrating: 'You did it!',
  studying: 'Focus mode',
  encouraging: 'You got this!',
  surprised: 'Nice move!',
};

export const OctopusMascot: React.FC<OctopusMascotProps> = ({
  mood = 'greeting',
  size = 72,
  showBadge = false,
  animate = true,
  message,
}) => {
  const bob = useRef(new Animated.Value(0)).current;
  const scale = size / 72;

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
    <View style={[styles.wrapper, { minWidth: message ? size + 104 : size, minHeight: size + (showBadge ? 18 : 0) }]}>
      <Animated.View
        style={[styles.container, { width: size, height: size, transform: [{ translateY: bob }] }]}
        accessible={true}
        accessibilityLabel={`Octo Coach, ${MOOD_LABEL[mood]}`}
        accessibilityRole="image"
      >
        <View style={[styles.backdrop, { width: size, height: size, borderRadius: size / 2 }]}>
          <View style={[styles.sparkle, { transform: [{ scale }] }]}><Text style={{ fontSize: 12 * scale, color: Colors.octopusGold }}>✦</Text></View>
          <View style={[styles.head, { width: size * 0.68, height: size * 0.58, borderRadius: size * 0.28 }]}>
            <View style={[styles.eyeRow, { width: size * 0.28 }]}>
              <View style={[styles.eye, { width: 6 * scale, height: 8 * scale, borderRadius: 5 * scale }]} />
              <View style={[styles.eye, { width: 6 * scale, height: 8 * scale, borderRadius: 5 * scale }]} />
            </View>
            <View style={[styles.mouth, mood === 'surprised' && styles.surprisedMouth]} />
            <View style={[styles.cheek, styles.cheekLeft, { width: 8 * scale, height: 4 * scale, borderRadius: 4 * scale }]} />
            <View style={[styles.cheek, styles.cheekRight, { width: 8 * scale, height: 4 * scale, borderRadius: 4 * scale }]} />
          </View>
          <View style={[styles.tentacle, styles.tentacleOne, { width: size * 0.15, height: size * 0.32, borderRadius: size * 0.1 }]} />
          <View style={[styles.tentacle, styles.tentacleTwo, { width: size * 0.15, height: size * 0.38, borderRadius: size * 0.1 }]} />
          <View style={[styles.tentacle, styles.tentacleThree, { width: size * 0.15, height: size * 0.34, borderRadius: size * 0.1 }]} />
          <View style={[styles.tentacle, styles.tentacleFour, { width: size * 0.15, height: size * 0.28, borderRadius: size * 0.1 }]} />
          <View style={[styles.moodBubble, { width: size * 0.31, height: size * 0.31, borderRadius: size * 0.16 }]}>
            <Text style={{ fontSize: 12 * scale }}>{MOOD_EMOJI[mood]}</Text>
          </View>
        </View>
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
  backdrop: { backgroundColor: Colors.octopusPurpleLight, borderWidth: 1.5, borderColor: Colors.octopusPurple, alignItems: 'center', justifyContent: 'center', overflow: 'visible', position: 'relative' },
  head: { backgroundColor: Colors.octopusPurple, alignItems: 'center', justifyContent: 'center', marginTop: -2 },
  eyeRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 5 },
  eye: { backgroundColor: Colors.surface },
  mouth: { width: 12, height: 6, borderBottomWidth: 2, borderBottomColor: Colors.surface, borderRadius: 8 },
  surprisedMouth: { width: 8, height: 8, borderWidth: 2, borderColor: Colors.surface, borderRadius: 8 },
  cheek: { position: 'absolute', bottom: 13, backgroundColor: Colors.octopusPink },
  cheekLeft: { left: 7 },
  cheekRight: { right: 7 },
  tentacle: { position: 'absolute', bottom: 7, backgroundColor: Colors.octopusPurpleDark },
  tentacleOne: { left: '16%', transform: [{ rotate: '-15deg' }] },
  tentacleTwo: { left: '35%', transform: [{ rotate: '-4deg' }] },
  tentacleThree: { right: '35%', transform: [{ rotate: '4deg' }] },
  tentacleFour: { right: '16%', transform: [{ rotate: '15deg' }] },
  moodBubble: { position: 'absolute', right: -3, bottom: -2, backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, alignItems: 'center', justifyContent: 'center' },
  sparkle: { position: 'absolute', top: 3, right: 9, zIndex: 2 },
  nameTag: { position: 'absolute', bottom: -1, alignSelf: 'center', paddingHorizontal: 7, paddingVertical: 2, backgroundColor: Colors.surfaceVariant, borderRadius: 8 },
  nameTagText: { fontSize: 10, fontWeight: '700', color: Colors.octopusPurpleDark },
  messageBubble: { maxWidth: 150, paddingHorizontal: 10, paddingVertical: 8, backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 14 },
  messageText: { color: Colors.text, fontSize: 12, lineHeight: 16, fontWeight: '600' },
});
