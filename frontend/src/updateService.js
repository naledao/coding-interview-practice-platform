import { APP_RELEASE_BASE_URL, UPDATE_RESOLVE_URL } from './updateConfig'

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
  const currentVersionCode = getCurrentVersionCode()
  const response = await fetch(UPDATE_RESOLVE_URL, {
    method: 'POST',
    cache: 'no-store',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      platform: 'android',
      architecture: 'universal',
      channel: 'stable',
      currentSequence: currentVersionCode,
      currentVersion: getCurrentVersionName(),
      packageTypes: ['apk'],
      supportedInstallerSchemaVersions: [1],
      osVersion: null,
    }),
  })
  if (!response.ok) {
    throw new Error('检查更新失败')
  }

  const resolved = await response.json()
  if (!resolved.updateAvailable || !resolved.release || !resolved.artifact) {
    return {
      manifest: null,
      hasUpdate: false,
      currentVersionCode,
    }
  }

  const { release, artifact } = resolved
  const downloadUrl = new URL(artifact.downloadUrl, APP_RELEASE_BASE_URL).toString()
  const manifest = {
    versionCode: Number(release.sequence),
    versionName: release.versionName,
    releaseNotes: release.releaseNotes,
    mandatory: Boolean(resolved.mandatory),
    publishedAt: release.publishedAt,
    artifactId: artifact.artifactId,
    fileName: artifact.fileName,
    fileSize: artifact.fileSize,
    sha256: artifact.sha256,
    installerSchemaVersion: artifact.installerSchemaVersion,
    downloadUrl,
  }

  return {
    manifest,
    hasUpdate: isAndroidApp() && manifest.versionCode > currentVersionCode,
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
