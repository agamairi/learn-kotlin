import React from 'react';
import { TouchableOpacity, View, Text, StyleSheet } from 'react-native';
import { Colors } from '../../theme/colors';
import { Typography } from '../../theme/typography';
import { Spacing, Radius } from '../../theme/spacing';
import type { SyncStatus } from '../../types/navigation';

export interface SyncStatusBadgeProps {
  status: SyncStatus;
  pendingCount?: number;
  onPress: () => void;
}

/**
 * Visible sync-status affordance.
 * Positioned in the mobile header across screens to give clear visibility
 * into offline-first state, pending local changes, and cloud sync status.
 */
export const SyncStatusBadge: React.FC<SyncStatusBadgeProps> = ({
  status,
  pendingCount = 0,
  onPress,
}) => {
  const getConfig = () => {
    switch (status) {
      case 'syncing':
        return {
          icon: '🔄',
          label: 'Syncing',
          dotColor: Colors.kodaTeal,
          bg: Colors.primaryContainer,
          border: Colors.primary,
        };
      case 'pending':
        return {
          icon: '⏳',
          label: pendingCount > 0 ? `${pendingCount} queued` : 'Queued',
          dotColor: Colors.warning,
          bg: Colors.warningContainer,
          border: Colors.warning,
        };
      case 'offline':
        return {
          icon: '☁️',
          label: 'Offline',
          dotColor: Colors.textMuted,
          bg: Colors.surfaceVariant,
          border: Colors.border,
        };
      case 'guest':
        return {
          icon: '👤',
          label: 'Local guest',
          dotColor: Colors.secondary,
          bg: Colors.secondaryContainer,
          border: Colors.secondary,
        };
      case 'synced':
      default:
        return {
          icon: '✓',
          label: 'Synced',
          dotColor: Colors.success,
          bg: Colors.successContainer,
          border: Colors.success,
        };
    }
  };

  const config = getConfig();

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.7}
      style={[styles.badge, { backgroundColor: config.bg, borderColor: config.border }]}
      accessible={true}
      accessibilityRole="button"
      accessibilityLabel={`Sync Status: ${config.label}. Tap for details.`}
    >
      <View style={[styles.dot, { backgroundColor: config.dotColor }]} />
      <Text style={[styles.label, { color: Colors.text }]}>{config.label}</Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    paddingHorizontal: Spacing.sm + 2,
    paddingVertical: Spacing.xxs + 2,
    borderRadius: Radius.round,
    borderWidth: 1,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  label: {
    ...Typography.labelSmall,
    fontWeight: '600',
  },
});
