import React from 'react'

export const isAdsFeatureConfigured = (): boolean => false
export async function prepareAds(): Promise<boolean> { return false }
export async function showAdPrivacyOptions(): Promise<boolean> { return false }
export async function showRewardedHeartAd(): Promise<boolean> { return false }
export function AdBanner(_props: { isAdFree?: boolean }) { return null }
