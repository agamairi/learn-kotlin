import React from 'react';
import { View, StyleSheet, ViewStyle } from 'react-native';
import { Colors } from '../../theme/colors';
import { Radius } from '../../theme/spacing';

export interface ProgressBarProps {
  progress: number; // 0.0 to 1.0
  height?: number;
  color?: string;
  trackColor?: string;
  style?: ViewStyle;
}

export const ProgressBar: React.FC<ProgressBarProps> = ({
  progress,
  height = 8,
  color = Colors.primary,
  trackColor = Colors.border,
  style,
}) => {
  const clampedProgress = Math.max(0, Math.min(1, isNaN(progress) ? 0 : progress));
  const percentage = `${Math.round(clampedProgress * 100)}%`;

  return (
    <View
      style={[
        styles.track,
        { height, borderRadius: height / 2, backgroundColor: trackColor },
        style,
      ]}
      accessible={true}
      accessibilityRole="progressbar"
      accessibilityLabel={`Progress: ${percentage}`}
    >
      <View
        style={[
          styles.fill,
          {
            width: percentage,
            height,
            borderRadius: height / 2,
            backgroundColor: color,
          },
        ]}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  track: {
    width: '100%',
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
  },
});
