import React, { useEffect, useState } from 'react'
import { StyleSheet, View } from 'react-native'
import mobileAds, {
  AdsConsent,
  AdEventType,
  BannerAd,
  BannerAdSize,
  RewardedAd,
  RewardedAdEventType,
  TestIds,
} from 'react-native-google-mobile-ads'

const enabled = process.env.EXPO_PUBLIC_ADS_ENABLED === 'true'
const useTestUnits = process.env.EXPO_PUBLIC_ADS_USE_TEST_UNITS !== 'false'
const bannerUnitId = useTestUnits ? TestIds.ADAPTIVE_BANNER : process.env.EXPO_PUBLIC_ADMOB_BANNER_UNIT_ID
const rewardedUnitId = useTestUnits ? TestIds.REWARDED : process.env.EXPO_PUBLIC_ADMOB_REWARDED_UNIT_ID
let initialized = false
let consentReady = false

export const isAdsFeatureConfigured = (): boolean => enabled && Boolean(bannerUnitId && rewardedUnitId)

export async function prepareAds(): Promise<boolean> {
  if (!isAdsFeatureConfigured()) return false
  try {
    if (!consentReady) {
      const consent = await AdsConsent.gatherConsent({ testDeviceIdentifiers: useTestUnits ? ['EMULATOR'] : undefined })
      consentReady = consent.canRequestAds
    }
    if (!consentReady) return false
    if (!initialized) {
      await mobileAds().initialize()
      initialized = true
    }
    return true
  } catch {
    return false
  }
}

export async function showAdPrivacyOptions(): Promise<boolean> {
  if (!isAdsFeatureConfigured()) return false
  try {
    await AdsConsent.showPrivacyOptionsForm()
    return true
  } catch {
    return false
  }
}

export async function showRewardedHeartAd(): Promise<boolean> {
  if (!rewardedUnitId || !(await prepareAds())) return false
  return new Promise((resolve) => {
    const ad = RewardedAd.createForAdRequest(rewardedUnitId)
    let earned = false
    let settled = false
    const finish = (value: boolean) => {
      if (settled) return
      settled = true
      unsubscribeLoaded()
      unsubscribeReward()
      unsubscribeClosed()
      unsubscribeError()
      resolve(value)
    }
    const unsubscribeLoaded = ad.addAdEventListener(RewardedAdEventType.LOADED, () => {
      void ad.show().catch(() => finish(false))
    })
    const unsubscribeReward = ad.addAdEventListener(RewardedAdEventType.EARNED_REWARD, () => {
      earned = true
    })
    const unsubscribeClosed = ad.addAdEventListener(AdEventType.CLOSED, () => finish(earned))
    const unsubscribeError = ad.addAdEventListener(AdEventType.ERROR, () => finish(false))
    ad.load()
  })
}

export function AdBanner({ isAdFree = false }: { isAdFree?: boolean }) {
  const [ready, setReady] = useState(false)
  useEffect(() => {
    let mounted = true
    void prepareAds().then((canRequest) => {
      if (mounted) setReady(canRequest)
    })
    return () => { mounted = false }
  }, [])
  if (isAdFree || !isAdsFeatureConfigured() || !ready || !bannerUnitId) return isAdsFeatureConfigured() && !isAdFree ? <View style={styles.container} /> : null
  // v17's native class declaration predates React 19's JSX constructor typing.
  // The runtime component remains the package's supported BannerAd component.
  const BannerComponent = BannerAd as unknown as React.ComponentType<{ unitId: string; size: BannerAdSize; onAdFailedToLoad: () => void }>
  return (
    <View style={styles.container} accessibilityLabel="Advertisement">
      <BannerComponent unitId={bannerUnitId} size={BannerAdSize.ANCHORED_ADAPTIVE_BANNER} onAdFailedToLoad={() => undefined} />
    </View>
  )
}

const styles = StyleSheet.create({ container: { alignItems: 'center', minHeight: 52, justifyContent: 'center', marginVertical: 8 } })
