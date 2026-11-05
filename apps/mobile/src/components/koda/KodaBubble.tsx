import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Colors } from '../../theme/colors';
import { Typography } from '../../theme/typography';
import { Spacing, Radius } from '../../theme/spacing';

export interface KodaBubbleProps {
  message: string;
  title?: string;
  variant?: 'sage' | 'clay' | 'neutral';
}

/**
 * Speech / guidance bubble representing Octo Coach's helpful notes and advice.
 */
export const KodaBubble: React.FC<KodaBubbleProps> = ({
  message,
  title = 'Octo Coach',
  variant = 'sage',
}) => {
  const getContainerStyle = () => {
    switch (variant) {
      case 'clay':
        return {
          backgroundColor: Colors.secondaryContainer,
          borderColor: Colors.secondary,
        };
      case 'neutral':
        return {
          backgroundColor: Colors.surfaceVariant,
          borderColor: Colors.border,
        };
      case 'sage':
      default:
        return {
          backgroundColor: Colors.primaryContainer,
          borderColor: Colors.primary,
        };
    }
  };

  const getTitleColor = () => {
    switch (variant) {
      case 'clay':
        return Colors.secondary;
      case 'neutral':
        return Colors.text;
      case 'sage':
      default:
        return Colors.primaryDark;
    }
  };

  return (
    <View style={[styles.bubble, getContainerStyle()]}>
      <View style={styles.headerRow}>
        <View style={[styles.dot, { backgroundColor: getTitleColor() }]} />
        <Text style={[styles.title, { color: getTitleColor() }]}>{title}</Text>
      </View>
      <Text style={styles.message}>{message}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  bubble: {
    borderRadius: Radius.md,
    borderWidth: 1,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm + 2,
    marginVertical: Spacing.xs,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs + 2,
    marginBottom: Spacing.xxs,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  title: {
    ...Typography.labelSmall,
    textTransform: 'uppercase',
  },
  message: {
    ...Typography.bodySmall,
    color: Colors.text,
    lineHeight: 18,
  },
});
