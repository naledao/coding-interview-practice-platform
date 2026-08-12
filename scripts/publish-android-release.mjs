import { createHash } from 'node:crypto'
import { readFile, stat } from 'node:fs/promises'
import { resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

const rootDir = resolve(import.meta.dirname, '..')
const androidDir = resolve(rootDir, 'android')
const apkPath = resolve(androidDir, 'app/build/outputs/apk/release/app-release.apk')
const legacyManifestSyncScript = resolve(rootDir, 'scripts/sync-legacy-android-release.mjs')
const serviceBaseUrl = trimTrailingSlash(
  process.env.APP_RELEASE_INTERNAL_BASE_URL || 'http://127.0.0.1:8962',
)
const internalApiBaseUrl = `${serviceBaseUrl}/api/app/internal/v1`
const publicBaseUrl = trimTrailingSlash(
  process.env.APP_RELEASE_PUBLIC_BASE_URL || 'http://frp.kangnasi.xyz:8960',
)
const internalToken = process.env.APP_RELEASE_INTERNAL_TOKEN
const appKey = process.env.APP_RELEASE_APP_KEY || 'interview-practice-platform'
const appName = process.env.APP_RELEASE_APP_NAME || 'Java 面试刷题'
const publisher = process.env.APP_RELEASE_PUBLISHER || 'kangnasi'
const applicationIdentifier = 'xyz.kangnasi.interviewpractice'
const versionName = process.env.INTERVIEW_ANDROID_VERSION_NAME || readGradleValue('versionName')
const versionCode = Number(process.env.INTERVIEW_ANDROID_VERSION_CODE || readGradleValue('versionCode'))
const releaseNotes = process.env.INTERVIEW_RELEASE_NOTES
  || '新增 AI 助教，支持本机 Codex Chat Service、动态模型与 Effort 选择。'
const apkFileName = `interview-practice-${versionName}-${versionCode}.apk`
const expectedSignerSha256 = normalizeCertDigest(
  process.env.INTERVIEW_RELEASE_CERT_SHA256
    || '2cbb2532e6794f1706cd93407254939055b9ce35768a4b31d332cacf511ae6f2',
)
const legacyManifestSyncEnabled = process.env.INTERVIEW_LEGACY_MANIFEST_SYNC !== 'false'

if (!internalToken) {
  throw new Error('Missing APP_RELEASE_INTERNAL_TOKEN.')
}
if (!Number.isSafeInteger(versionCode) || versionCode <= 0) {
  throw new Error(`Invalid Android versionCode: ${versionCode}`)
}

run('gradle', ['assembleRelease'], { cwd: androidDir })
verifyReleaseSignature()

const apk = await readFile(apkPath)
const apkStat = await stat(apkPath)
const apkSha256 = createHash('sha256').update(apk).digest('hex')

const app = await findOrCreateApp()
const track = await findOrCreateTrack(app.appId)
const release = await findOrCreateRelease(track.trackId)
const artifact = await findOrCreateArtifact(release)
const verifiedArtifact = await uploadArtifactIfNeeded(release, artifact, apk, apkStat.size, apkSha256)
const publishedRelease = await publishReleaseIfNeeded(release)
const downloadUrl = `${publicBaseUrl}/api/app/v1/apps/${appKey}/artifacts/${verifiedArtifact.artifactId}/download`

if (legacyManifestSyncEnabled) {
  run(process.execPath, [legacyManifestSyncScript])
}

console.log(JSON.stringify({
  appKey,
  platform: track.platform,
  channel: track.channel,
  releaseId: publishedRelease.releaseId,
  artifactId: verifiedArtifact.artifactId,
  versionCode,
  versionName,
  releaseNotes,
  fileName: verifiedArtifact.fileName,
  fileSize: verifiedArtifact.fileSize,
  sha256: verifiedArtifact.sha256,
  downloadUrl,
  status: publishedRelease.status,
  legacyManifestSynced: legacyManifestSyncEnabled,
}, null, 2))

async function findOrCreateApp() {
  const apps = await api('/apps')
  const existing = apps.find((item) => item.appKey === appKey)
  if (existing) {
    return existing
  }
  return api('/apps', {
    method: 'POST',
    body: { appKey, name: appName, publisher },
  })
}

async function findOrCreateTrack(appId) {
  const tracks = await api(`/apps/${appId}/tracks`)
  const existing = tracks.find((item) => item.platform === 'android' && item.channel === 'stable')
  if (existing) {
    if (existing.applicationIdentifier !== applicationIdentifier
        || existing.verificationMode !== 'platform-signature') {
      throw new Error('Existing Android stable track configuration does not match this app.')
    }
    return existing
  }
  return api(`/apps/${appId}/tracks`, {
    method: 'POST',
    body: {
      platform: 'android',
      channel: 'stable',
      applicationIdentifier,
      verificationMode: 'platform-signature',
    },
  })
}

async function findOrCreateRelease(trackId) {
  const releases = await api(`/tracks/${trackId}/releases`)
  const existing = releases.find((item) => Number(item.releaseSequence) === versionCode)
  if (existing) {
    if (existing.versionName !== versionName) {
      throw new Error(`Release sequence ${versionCode} already has versionName ${existing.versionName}.`)
    }
    return existing
  }
  return api(`/tracks/${trackId}/releases`, {
    method: 'POST',
    body: {
      releaseSequence: versionCode,
      versionName,
      releaseNotes,
      mandatory: false,
      minimumSupportedSequence: null,
    },
  })
}

async function findOrCreateArtifact(release) {
  const artifacts = await api(`/releases/${release.releaseId}/artifacts`)
  const existing = artifacts.find((item) => item.architecture === 'universal'
    && item.packageType === 'apk' && item.variant === 'default')
  if (existing) {
    return existing
  }
  if (release.status !== 'draft') {
    throw new Error(`Published release ${versionCode} has no universal APK artifact.`)
  }
  return api(`/releases/${release.releaseId}/artifacts`, {
    method: 'POST',
    body: {
      architecture: 'universal',
      packageType: 'apk',
      variant: 'default',
      fileName: apkFileName,
      fileExtension: 'apk',
      contentType: 'application/vnd.android.package-archive',
      minimumOsVersion: null,
      maximumOsVersion: null,
      installerSchemaVersion: 1,
    },
  })
}

async function uploadArtifactIfNeeded(release, artifact, apk, apkSize, apkSha256) {
  if (artifact.status === 'verified') {
    if (artifact.fileSize !== apkSize || artifact.sha256 !== apkSha256) {
      throw new Error(`Verified artifact ${artifact.artifactId} does not match the local APK.`)
    }
    return artifact
  }
  if (release.status !== 'draft' || artifact.status !== 'draft') {
    throw new Error(`Artifact ${artifact.artifactId} cannot be uploaded from status ${artifact.status}.`)
  }

  const ticket = await api(`/artifacts/${artifact.artifactId}/upload-ticket`, { method: 'POST' })
  const uploadResponse = await fetch(ticket.uploadUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': artifact.contentType,
      'Content-Length': String(apkSize),
    },
    body: apk,
  })
  if (!uploadResponse.ok) {
    throw new Error(`Artifact upload failed: HTTP ${uploadResponse.status}`)
  }

  return api(`/artifacts/${artifact.artifactId}/complete`, {
    method: 'POST',
    body: { expectedSize: apkSize, expectedSha256: apkSha256 },
  })
}

async function publishReleaseIfNeeded(release) {
  if (release.status === 'published') {
    return release
  }
  if (release.status !== 'draft') {
    throw new Error(`Release ${release.releaseId} cannot be published from status ${release.status}.`)
  }
  return api(`/releases/${release.releaseId}/publish`, { method: 'POST' })
}

async function api(path, options = {}) {
  const response = await fetch(`${internalApiBaseUrl}${path}`, {
    method: options.method || 'GET',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-App-Internal-Token': internalToken,
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`App release API ${options.method || 'GET'} ${path} failed: HTTP ${response.status} ${text}`)
  }
  return text ? JSON.parse(text) : null
}

function readGradleValue(name) {
  const result = spawnSync('sed', ['-n', '1,80p', resolve(androidDir, 'app/build.gradle')], {
    encoding: 'utf8',
  })
  if (result.status !== 0) {
    throw new Error(`Cannot read android/app/build.gradle: ${result.stderr}`)
  }
  const match = result.stdout.match(new RegExp(`${name}\\s+['\"]?([^'\"\\n]+)['\"]?`))
  if (!match) {
    throw new Error(`Cannot read ${name} from android/app/build.gradle`)
  }
  return match[1].trim()
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    stdio: 'inherit',
    ...options,
  })
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} failed`)
  }
}

function verifyReleaseSignature() {
  const result = spawnSync('apksigner', ['verify', '--print-certs', apkPath], {
    encoding: 'utf8',
  })
  if (result.status !== 0) {
    throw new Error(`apksigner verify failed: ${result.stderr || result.stdout}`)
  }

  const match = result.stdout.match(/Signer #1 certificate SHA-256 digest:\s*([0-9a-f]+)/i)
  const signerSha256 = normalizeCertDigest(match?.[1] || '')
  if (!signerSha256) {
    throw new Error('Cannot read release APK signer SHA-256 digest.')
  }
  if (signerSha256 !== expectedSignerSha256) {
    throw new Error(`Release APK signer mismatch. Expected ${expectedSignerSha256}, got ${signerSha256}.`)
  }
}

function normalizeCertDigest(value) {
  return value.replace(/[^0-9a-f]/gi, '').toLowerCase()
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, '')
}
