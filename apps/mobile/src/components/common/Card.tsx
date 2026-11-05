import React from 'react';
import { View, StyleSheet, ViewStyle, StyleProp } from 'react-native';
import { Colors } from '../../theme/colors';
import { Radius, Spacing } from '../../theme/spacing';

export interface CardProps {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  variant?: 'surface' | 'container' | 'muted';
}

export const Card: React.FC<CardProps> = ({
  children,
  style,
  variant = 'surface',
}) => {
  const getVariantStyle = (): ViewStyle => {
    switch (variant) {
      case 'container':
        return {
          backgroundColor: Colors.surfaceContainer,
          borderColor: Colors.border,
        };
      case 'muted':
        return {
          backgroundColor: Colors.surfaceVariant,
          borderColor: Colors.outlineVariant,
        };
      case 'surface':
      default:
        return {
          backgroundColor: Colors.surface,
          borderColor: Colors.border,
        };
    }
  };

  return (
    <View style={[styles.card, getVariantStyle(), style]}>
      {children}
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.lg,
  },
});
