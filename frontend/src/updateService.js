import { UPDATE_MANIFEST_URL } from './updateConfig'

export function isAndroidApp() {
  return typeof window.AndroidBridge !== 'undefined'
}

export function getCurrentVersionCode() {
  if (!isAndroidApp() || typeof window.AndroidBridge.getVersionCode !== 'function') {
    return 0
  }
  return Number(window.AndroidBridge.getVersionCode()) || 0
}

export function getCurrentVersionName() {
  if (!isAndroidApp() || typeof window.AndroidBridge.getVersionName !== 'function') {
    return ''
  }
  return window.AndroidBridge.getVersionName()
}

export async function checkForUpdate() {
  const response = await fetch(`${UPDATE_MANIFEST_URL}?t=${Date.now()}`, {
    cache: 'no-store',
  })
  if (!response.ok) {
    throw new Error('检查更新失败')
  }

  const manifest = await response.json()
  const currentVersionCode = getCurrentVersionCode()
  return {
    manifest,
    hasUpdate: isAndroidApp() && Number(manifest.versionCode) > currentVersionCode,
    currentVersionCode,
  }
}

export function installUpdate(downloadUrl) {
  if (!isAndroidApp() || typeof window.AndroidBridge.installApkFromUrl !== 'function') {
    window.open(downloadUrl, '_blank', 'noopener,noreferrer')
    return
  }
  window.AndroidBridge.installApkFromUrl(downloadUrl)
}
