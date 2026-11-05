import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '../../theme/colors';
import { Typography } from '../../theme/typography';
import { Radius, Spacing } from '../../theme/spacing';
import type { TabRoute } from '../../types/navigation';

export interface BottomTabBarProps {
  currentTab: TabRoute;
  onSelectTab: (tab: TabRoute) => void;
  reviewDueCount?: number;
}

interface TabConfig {
  id: TabRoute;
  label: string;
  icon: string;
}

const TABS: TabConfig[] = [
  { id: 'home', label: 'Home', icon: '⌂' },
  { id: 'path', label: 'Path', icon: '❖' },
  { id: 'review', label: 'Review', icon: '↻' },
  { id: 'stats', label: 'Stats', icon: '∿' },
  { id: 'settings', label: 'Settings', icon: '⚙' },
];

export const BottomTabBar: React.FC<BottomTabBarProps> = ({
  currentTab,
  onSelectTab,
  reviewDueCount = 0,
}) => {
  return (
    <View style={styles.container} accessible={true} accessibilityRole="tablist">
      {TABS.map((tab) => {
        const isActive = currentTab === tab.id;
        const isReview = tab.id === 'review';

        return (
          <TouchableOpacity
            key={tab.id}
            onPress={() => onSelectTab(tab.id)}
            style={[styles.tabButton, isActive && styles.tabButtonActive]}
            activeOpacity={0.7}
            accessible={true}
            accessibilityRole="tab"
            accessibilityState={{ selected: isActive }}
            accessibilityLabel={`${tab.label} tab`}
          >
            <View style={styles.iconWrapper}>
              <Text
                style={[
                  styles.tabIcon,
                  { color: isActive ? Colors.primary : Colors.textSecondary },
                ]}
              >
                {tab.icon}
              </Text>
              {isReview && reviewDueCount > 0 && (
                <View style={styles.dueBadge}>
                  <Text style={styles.dueBadgeText}>
                    {reviewDueCount > 9 ? '9+' : reviewDueCount}
                  </Text>
                </View>
              )}
            </View>
            <Text
              style={[
                styles.tabLabel,
                {
                  color: isActive ? Colors.primary : Colors.textSecondary,
                  fontWeight: isActive ? '700' : '500',
                },
              ]}
            >
              {tab.label}
            </Text>
            {isActive && <View style={styles.activeIndicator} />}
          </TouchableOpacity>
        );
      })}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    left: Spacing.lg,
    right: Spacing.lg,
    bottom: Spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    borderWidth: 1,
    borderColor: Colors.border,
    borderRadius: Radius.xl + 8,
    paddingVertical: Spacing.sm,
    paddingHorizontal: Spacing.xs,
    height: 70,
    shadowColor: Colors.text,
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.12,
    shadowRadius: 12,
    elevation: 8,
    zIndex: 20,
  },
  tabButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    height: 56,
    borderRadius: Radius.lg,
    paddingVertical: Spacing.xxs,
    position: 'relative',
  },
  tabButtonActive: {
    backgroundColor: Colors.primaryContainer,
  },
  iconWrapper: {
    position: 'relative',
    alignItems: 'center',
    justifyContent: 'center',
    height: 24,
  },
  tabIcon: {
    fontSize: 20,
    lineHeight: 22,
  },
  tabLabel: {
    ...Typography.labelSmall,
    fontSize: 10,
    marginTop: 2,
  },
  dueBadge: {
    position: 'absolute',
    top: -3,
    right: -10,
    backgroundColor: Colors.secondary,
    borderRadius: 999,
    minWidth: 14,
    height: 14,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 2,
  },
  dueBadgeText: {
    color: Colors.onSecondary,
    fontSize: 8,
    fontWeight: '700',
  },
  activeIndicator: {
    position: 'absolute',
    bottom: 2,
    width: 5,
    height: 5,
    borderRadius: Radius.round,
    backgroundColor: Colors.primary,
  },
});
