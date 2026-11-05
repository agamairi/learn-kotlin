import React from 'react';
import {
  TouchableOpacity,
  Text,
  StyleSheet,
  ActivityIndicator,
  ViewStyle,
  TextStyle,
  StyleProp,
} from 'react-native';
import { Colors } from '../../theme/colors';
import { Typography } from '../../theme/typography';
import { Spacing, Radius } from '../../theme/spacing';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'error';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  size?: ButtonSize;
  disabled?: boolean;
  loading?: boolean;
  style?: StyleProp<ViewStyle>;
  textStyle?: StyleProp<TextStyle>;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  accessibilityLabel?: string;
}

export const Button: React.FC<ButtonProps> = ({
  title,
  onPress,
  variant = 'primary',
  size = 'md',
  disabled = false,
  loading = false,
  style,
  textStyle,
  leftIcon,
  rightIcon,
  accessibilityLabel,
}) => {
  const getContainerStyle = (): ViewStyle => {
    switch (variant) {
      case 'secondary':
        return {
          backgroundColor: Colors.secondary,
          borderColor: Colors.secondary,
        };
      case 'outline':
        return {
          backgroundColor: Colors.surface,
          borderColor: Colors.border,
          borderWidth: 1,
        };
      case 'ghost':
        return {
          backgroundColor: 'transparent',
          borderColor: 'transparent',
        };
      case 'error':
        return {
          backgroundColor: Colors.error,
          borderColor: Colors.error,
        };
      case 'primary':
      default:
        return {
          backgroundColor: Colors.primary,
          borderColor: Colors.primary,
        };
    }
  };

  const getTextStyle = (): TextStyle => {
    switch (variant) {
      case 'secondary':
        return { color: Colors.onSecondary };
      case 'outline':
        return { color: Colors.text };
      case 'ghost':
        return { color: Colors.primary };
      case 'error':
        return { color: Colors.onError };
      case 'primary':
      default:
        return { color: Colors.onPrimary };
    }
  };

  const getSizeStyle = (): { container: ViewStyle; text: TextStyle } => {
    switch (size) {
      case 'sm':
        return {
          container: {
            height: 36,
            paddingHorizontal: Spacing.md,
            borderRadius: Radius.md,
          },
          text: Typography.labelMedium,
        };
      case 'lg':
        return {
          container: {
            height: 52,
            paddingHorizontal: Spacing.xl,
            borderRadius: Radius.lg,
          },
          text: Typography.labelLarge,
        };
      case 'md':
      default:
        return {
          container: {
            height: 44,
            paddingHorizontal: Spacing.lg,
            borderRadius: Radius.md,
          },
          text: Typography.labelLarge,
        };
    }
  };

  const sizeStyle = getSizeStyle();

  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={disabled || loading}
      activeOpacity={0.8}
      style={[
        styles.base,
        sizeStyle.container,
        getContainerStyle(),
        disabled && styles.disabled,
        style,
      ]}
      accessible={true}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel || title}
    >
      {loading ? (
        <ActivityIndicator
          size="small"
          color={variant === 'outline' || variant === 'ghost' ? Colors.primary : Colors.onPrimary}
        />
      ) : (
        <>
          {leftIcon}
          <Text style={[styles.textBase, sizeStyle.text, getTextStyle(), textStyle]}>
            {title}
          </Text>
          {rightIcon}
        </>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  base: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
  },
  textBase: {
    fontWeight: '600',
    textAlign: 'center',
  },
  disabled: {
    opacity: 0.5,
  },
});
