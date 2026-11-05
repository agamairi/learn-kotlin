import React from 'react';
import { View, Text, StyleSheet, Modal, TouchableOpacity } from 'react-native';
import { Colors } from '../../theme/colors';
import { Typography } from '../../theme/typography';
import { Spacing, Radius } from '../../theme/spacing';
import { Button } from '../common/Button';
import type { SyncStatus } from '../../types/navigation';

export interface SyncModalProps {
  visible: boolean;
  status: SyncStatus;
  pendingCount: number;
  lastSyncedAt: string | null;
  userType: 'guest' | 'account';
  userEmail?: string;
  onClose: () => void;
  onTriggerSync: () => void;
  onGoToSettings: () => void;
}

export const SyncModal: React.FC<SyncModalProps> = ({
  visible,
  status,
  pendingCount,
  lastSyncedAt,
  userType,
  userEmail,
  onClose,
  onTriggerSync,
  onGoToSettings,
}) => {
  return (
    <Modal visible={visible} transparent={true} animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View style={styles.sheet}>
          <View style={styles.header}>
            <View>
              <Text style={styles.title}>Cloud & Offline Sync</Text>
              <Text style={styles.subtitle}>Keep your progress safe across sessions</Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.content}>
            <View style={styles.statusRow}>
              <Text style={styles.rowLabel}>Current Status:</Text>
              <View style={[styles.statusPill, { backgroundColor: Colors.primaryContainer }]}>
                <Text style={styles.statusPillText}>
                  {status === 'synced'
                    ? 'All synced'
                    : status === 'syncing'
                    ? 'Syncing now...'
                    : status === 'pending'
                    ? `${pendingCount} items in outbox`
                    : status === 'guest'
                    ? 'Local-only guest'
                    : 'Offline'}
                </Text>
              </View>
            </View>

            <View style={styles.statusRow}>
              <Text style={styles.rowLabel}>Account:</Text>
              <Text style={styles.rowValue}>
                {userType === 'guest' ? 'Guest (Local Store)' : userEmail || 'Signed-in learner'}
              </Text>
            </View>

            <View style={styles.statusRow}>
              <Text style={styles.rowLabel}>Last Sync:</Text>
              <Text style={styles.rowValue}>
                {lastSyncedAt ? new Date(lastSyncedAt).toLocaleTimeString() : 'Not yet synced'}
              </Text>
            </View>

            <View style={styles.infoBox}>
              <Text style={styles.infoText}>
                {userType === 'guest'
                  ? 'You are learning in local guest mode. Your streak and progress are stored safely on this device. Sign in from Settings anytime to sync across devices.'
                  : 'Your progress is automatically saved to your local device outbox and flushed to the backend when online.'}
              </Text>
            </View>
          </View>

          <View style={styles.actions}>
            {userType === 'account' ? (
              <Button
                title={status === 'syncing' ? 'Syncing...' : 'Sync Now'}
                onPress={onTriggerSync}
                loading={status === 'syncing'}
                variant="primary"
                size="md"
              />
            ) : (
              <Button
                title="Sign In to Enable Cloud Sync"
                onPress={() => {
                  onClose();
                  onGoToSettings();
                }}
                variant="primary"
                size="md"
              />
            )}
            <Button
              title="Close"
              onPress={onClose}
              variant="outline"
              size="md"
            />
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(45, 48, 44, 0.45)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.lg,
  },
  sheet: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: Colors.surface,
    borderRadius: Radius.xl,
    padding: Spacing.xl,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: Spacing.md,
  },
  title: {
    ...Typography.headlineSmall,
    color: Colors.text,
  },
  subtitle: {
    ...Typography.bodySmall,
    color: Colors.textMuted,
    marginTop: 2,
  },
  closeBtn: {
    padding: Spacing.xs,
  },
  closeText: {
    fontSize: 16,
    color: Colors.textMuted,
    fontWeight: '600',
  },
  content: {
    gap: Spacing.sm,
    marginVertical: Spacing.sm,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.xs,
  },
  rowLabel: {
    ...Typography.bodyMedium,
    color: Colors.textMuted,
  },
  rowValue: {
    ...Typography.bodyMedium,
    fontWeight: '600',
    color: Colors.text,
  },
  statusPill: {
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xxs,
    borderRadius: Radius.round,
  },
  statusPillText: {
    ...Typography.labelSmall,
    color: Colors.primaryDark,
  },
  infoBox: {
    backgroundColor: Colors.surfaceVariant,
    padding: Spacing.md,
    borderRadius: Radius.md,
    marginTop: Spacing.xs,
  },
  infoText: {
    ...Typography.bodySmall,
    color: Colors.text,
    lineHeight: 18,
  },
  actions: {
    gap: Spacing.sm,
    marginTop: Spacing.md,
  },
});
