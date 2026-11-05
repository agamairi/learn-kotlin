import React from 'react';
import { View, Text, StyleSheet, ViewStyle, TextStyle } from 'react-native';
import { Colors } from '../../theme/colors';
import { Typography } from '../../theme/typography';
import { Spacing, Radius } from '../../theme/spacing';

export type BadgeVariant = 'sage' | 'clay' | 'ochre' | 'neutral' | 'success' | 'error';

export interface BadgeProps {
  label: string;
  variant?: BadgeVariant;
  icon?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  label,
  variant = 'neutral',
  icon,
}) => {
  const getColors = (): { bg: string; border: string; text: string } => {
    switch (variant) {
      case 'sage':
        return {
          bg: Colors.primaryContainer,
          border: Colors.primary,
          text: Colors.primaryDark,
        };
      case 'clay':
        return {
          bg: Colors.secondaryContainer,
          border: Colors.secondary,
          text: Colors.secondary,
        };
      case 'ochre':
        return {
          bg: Colors.tertiaryContainer,
          border: Colors.tertiary,
          text: Colors.tertiary,
        };
      case 'success':
        return {
          bg: Colors.successContainer,
          border: Colors.success,
          text: Colors.success,
        };
      case 'error':
        return {
          bg: Colors.errorContainer,
          border: Colors.error,
          text: Colors.error,
        };
      case 'neutral':
      default:
        return {
          bg: Colors.surfaceVariant,
          border: Colors.border,
          text: Colors.textMuted,
        };
    }
  };

  const colors = getColors();

  return (
    <View style={[styles.badge, { backgroundColor: colors.bg, borderColor: colors.border }]}>
      {icon ? <Text style={styles.icon}>{icon}</Text> : null}
      <Text style={[styles.text, { color: colors.text }]}>{label}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    paddingHorizontal: Spacing.sm + 2,
    paddingVertical: Spacing.xxs + 1,
    borderRadius: Radius.round,
    borderWidth: 1,
    alignSelf: 'flex-start',
  },
  icon: {
    fontSize: 11,
  },
  text: {
    ...Typography.labelSmall,
    textTransform: 'uppercase',
  },
});
