import { createHash, createHmac } from 'node:crypto'

const appReleaseBaseUrl = trimTrailingSlash(
  process.env.APP_RELEASE_PUBLIC_BASE_URL || 'http://frp.kangnasi.xyz:8960',
)
const appKey = process.env.APP_RELEASE_APP_KEY || 'interview-practice-platform'
const applicationIdentifier = 'xyz.kangnasi.interviewpractice'
const minioEndpoint = process.env.INTERVIEW_MINIO_ENDPOINT || 'http://127.0.0.1:8084'
const minioBucket = process.env.INTERVIEW_MINIO_BUCKET || 'interview-practice-platform'
const minioPrefix = trimSlashes(process.env.INTERVIEW_MINIO_PREFIX || 'android/releases')
const minioAccessKey = process.env.MINIO_ROOT_USER || process.env.INTERVIEW_MINIO_ACCESS_KEY
const minioSecretKey = process.env.MINIO_ROOT_PASSWORD || process.env.INTERVIEW_MINIO_SECRET_KEY
const minioRegion = process.env.INTERVIEW_MINIO_REGION || 'us-east-1'

if (!minioAccessKey || !minioSecretKey) {
  throw new Error(
    'Missing MinIO credentials. Set MINIO_ROOT_USER/MINIO_ROOT_PASSWORD '
      + 'or INTERVIEW_MINIO_ACCESS_KEY/INTERVIEW_MINIO_SECRET_KEY.',
  )
}

const resolved = await resolveLatestRelease()
if (!resolved.updateAvailable || !resolved.release || !resolved.artifact) {
  throw new Error(`No published Android APK release found for ${appKey}.`)
}

const { release, artifact } = resolved
const versionCode = Number(release.sequence)
if (!Number.isSafeInteger(versionCode) || versionCode <= 0) {
  throw new Error(`Invalid release sequence returned by app-release-service: ${release.sequence}`)
}

const downloadUrl = new URL(artifact.downloadUrl, `${appReleaseBaseUrl}/`).toString()
const manifest = {
  appId: applicationIdentifier,
  versionCode,
  versionName: release.versionName,
  releaseNotes: release.releaseNotes,
  fileName: artifact.fileName,
  fileSize: artifact.fileSize,
  sha256: artifact.sha256,
  downloadUrl,
  publishedAt: release.publishedAt,
}
const manifestBody = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`)

await ensureBucketExists()
await putObject(`${minioPrefix}/latest.json`, manifestBody, 'application/json; charset=utf-8')
await putObject(
  `${minioPrefix}/v${versionCode}/latest.json`,
  manifestBody,
  'application/json; charset=utf-8',
)

console.log(JSON.stringify({
  legacyManifestSynced: true,
  appKey,
  versionCode,
  versionName: release.versionName,
  downloadUrl,
}, null, 2))

async function resolveLatestRelease() {
  const response = await fetch(
    `${appReleaseBaseUrl}/api/app/v1/apps/${encodeURIComponent(appKey)}/updates/resolve`,
    {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        platform: 'android',
        architecture: 'universal',
        channel: 'stable',
        currentSequence: 0,
        currentVersion: '',
        packageTypes: ['apk'],
        supportedInstallerSchemaVersions: [1],
        osVersion: null,
      }),
    },
  )
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`Resolve latest app release failed: HTTP ${response.status} ${text}`)
  }
  return JSON.parse(text)
}

async function ensureBucketExists() {
  const response = await signedFetch('', { method: 'HEAD' })
  if (response.status === 404) {
    const createResponse = await signedFetch('', { method: 'PUT' })
    if (!createResponse.ok) {
      throw new Error(`Create MinIO bucket failed: HTTP ${createResponse.status} ${await createResponse.text()}`)
    }
    return
  }
  if (!response.ok) {
    throw new Error(`Check MinIO bucket failed: HTTP ${response.status} ${await response.text()}`)
  }
}

async function putObject(objectKey, body, contentType) {
  const response = await signedFetch(objectKey, {
    method: 'PUT',
    body,
    headers: {
      'content-type': contentType,
      'content-length': String(body.length),
    },
  })
  if (!response.ok) {
    throw new Error(`Upload ${objectKey} failed: HTTP ${response.status} ${await response.text()}`)
  }
}

async function signedFetch(objectKey, options = {}) {
  const url = new URL(minioEndpoint)
  url.pathname = `/${minioBucket}${objectKey ? `/${objectKey}` : ''}`

  const headers = new Headers(options.headers || {})
  const body = options.body || Buffer.alloc(0)
  const payloadHash = createHash('sha256').update(body).digest('hex')
  const now = new Date()
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, '')
  const dateStamp = amzDate.slice(0, 8)

  headers.set('host', url.host)
  headers.set('x-amz-content-sha256', payloadHash)
  headers.set('x-amz-date', amzDate)

  const method = options.method || 'GET'
  const canonicalUri = encodeURI(url.pathname).replace(/%2F/g, '/')
  const signedHeaders = [...headers.keys()].map((key) => key.toLowerCase()).sort()
  const canonicalHeaders = signedHeaders
    .map((key) => `${key}:${headers.get(key).trim().replace(/\s+/g, ' ')}\n`)
    .join('')
  const scope = `${dateStamp}/${minioRegion}/s3/aws4_request`
  const canonicalRequest = [
    method,
    canonicalUri,
    '',
    canonicalHeaders,
    signedHeaders.join(';'),
    payloadHash,
  ].join('\n')
  const stringToSign = [
    'AWS4-HMAC-SHA256',
    amzDate,
    scope,
    createHash('sha256').update(canonicalRequest).digest('hex'),
  ].join('\n')
  const signature = createHmac('sha256', signatureKey(dateStamp))
    .update(stringToSign)
    .digest('hex')
  headers.set(
    'authorization',
    `AWS4-HMAC-SHA256 Credential=${minioAccessKey}/${scope}, SignedHeaders=${signedHeaders.join(';')}, Signature=${signature}`,
  )

  return fetch(url, {
    method,
    headers,
    body: method === 'HEAD' ? undefined : body,
  })
}

function signatureKey(dateStamp) {
  const dateKey = hmac(`AWS4${minioSecretKey}`, dateStamp)
  const regionKey = hmac(dateKey, minioRegion)
  const serviceKey = hmac(regionKey, 's3')
  return hmac(serviceKey, 'aws4_request')
}

function hmac(key, data) {
  return createHmac('sha256', key).update(data).digest()
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, '')
}

function trimSlashes(value) {
  return value.replace(/^\/+|\/+$/g, '')
}
