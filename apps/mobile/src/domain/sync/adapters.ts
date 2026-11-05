/**
 * Native Platform Adapters Status Matrix
 *
 * This file explicitly marks which native capabilities are implemented in the
 * lightweight TypeScript mobile shell versus those that require native bindings
 * (Expo modules, Android NDK/Kotlin, or iOS CocoaPods).
 */

export type AdapterStatus = 'stubbed_memory' | 'unimplemented_native' | 'mock_ready';

export interface NativeAdapterDescriptor {
  name: string;
  category: 'storage' | 'security' | 'haptics' | 'audio' | 'network';
  status: AdapterStatus;
  fallbackBehavior: string;
  requiredNativeModule: string;
}

export const NATIVE_ADAPTERS: NativeAdapterDescriptor[] = [
  {
    name: 'SQLite Storage Adapter',
    category: 'storage',
    status: 'stubbed_memory',
    fallbackBehavior: 'In-memory LocalSyncStore with transient state',
    requiredNativeModule: 'expo-sqlite / Android SQLiteOpenHelper',
  },
  {
    name: 'SecureStore Keychain Adapter',
    category: 'security',
    status: 'stubbed_memory',
    fallbackBehavior: 'In-memory token cache for guest/account session',
    requiredNativeModule: 'expo-secure-store / Android EncryptedSharedPreferences',
  },
  {
    name: 'Haptic Feedback Engine',
    category: 'haptics',
    status: 'unimplemented_native',
    fallbackBehavior: 'Silent operation (no physical vibration trigger)',
    requiredNativeModule: 'expo-haptics / Android Vibrator / VibrationEffect',
  },
  {
    name: 'Companion Audio & SFX',
    category: 'audio',
    status: 'unimplemented_native',
    fallbackBehavior: 'Visual cues & badges only; no audio playback',
    requiredNativeModule: 'expo-av / Android SoundPool',
  },
  {
    name: 'Network Reachability Monitor',
    category: 'network',
    status: 'mock_ready',
    fallbackBehavior: 'Assumes online state with manual toggle for offline simulation',
    requiredNativeModule: '@react-native-community/netinfo',
  },
];
