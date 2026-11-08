import Purchases from 'react-native-purchases'

const apiKey = process.env.EXPO_PUBLIC_REVENUECAT_ANDROID_API_KEY
const entitlementId = process.env.EXPO_PUBLIC_REVENUECAT_ENTITLEMENT_ID || 'remove_ads_lifetime'
const productId = process.env.EXPO_PUBLIC_REMOVE_ADS_PRODUCT_ID || 'remove_ads_lifetime'
let configured = false

export const isBillingConfigured = (): boolean => Boolean(apiKey)

async function ensureConfigured(): Promise<boolean> {
  if (!apiKey) return false
  if (!configured) {
    await Purchases.configure({ apiKey })
    configured = true
  }
  return true
}

function hasAdFreeEntitlement(info: { entitlements: { active: Record<string, unknown> } }): boolean {
  return Boolean(info.entitlements.active[entitlementId])
}

export async function getAdFreeEntitlement(): Promise<boolean> {
  if (!(await ensureConfigured())) return false
  try {
    return hasAdFreeEntitlement(await Purchases.getCustomerInfo())
  } catch {
    return false
  }
}

export async function purchaseAdRemoval(): Promise<boolean> {
  if (!(await ensureConfigured())) return false
  try {
    const offerings = await Purchases.getOfferings()
    const packageToBuy = offerings.current?.availablePackages.find((item) => item.product.identifier === productId)
    if (!packageToBuy) return false
    const result = await Purchases.purchasePackage(packageToBuy)
    return hasAdFreeEntitlement(result.customerInfo)
  } catch {
    return false
  }
}

export async function restoreAdRemoval(): Promise<boolean> {
  if (!(await ensureConfigured())) return false
  try {
    return hasAdFreeEntitlement(await Purchases.restorePurchases())
  } catch {
    return false
  }
}
