import { createHash, createHmac } from 'node:crypto'
import { readFile, stat } from 'node:fs/promises'
import { basename, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

const rootDir = resolve(import.meta.dirname, '..')
const androidDir = resolve(rootDir, 'android')
const apkPath = resolve(androidDir, 'app/build/outputs/apk/release/app-release.apk')
const bucket = process.env.INTERVIEW_MINIO_BUCKET || 'interview-practice-platform'
const prefix = process.env.INTERVIEW_MINIO_PREFIX || 'android/releases'
const endpoint = process.env.INTERVIEW_MINIO_ENDPOINT || 'http://127.0.0.1:8084'
const publicBaseUrl = process.env.INTERVIEW_MINIO_PUBLIC_BASE_URL || 'http://frp.kangnasi.xyz:8084'
const accessKeyId = process.env.MINIO_ROOT_USER || process.env.INTERVIEW_MINIO_ACCESS_KEY
const secretAccessKey = process.env.MINIO_ROOT_PASSWORD || process.env.INTERVIEW_MINIO_SECRET_KEY
const region = process.env.INTERVIEW_MINIO_REGION || 'us-east-1'
const versionName = process.env.INTERVIEW_ANDROID_VERSION_NAME || readGradleValue('versionName')
const versionCode = Number(process.env.INTERVIEW_ANDROID_VERSION_CODE || readGradleValue('versionCode'))
const releaseNotes = process.env.INTERVIEW_RELEASE_NOTES || '新增版本更新能力，优化 Android 多页面体验。'
const expectedSignerSha256 = normalizeCertDigest(
  process.env.INTERVIEW_RELEASE_CERT_SHA256 ||
    '2cbb2532e6794f1706cd93407254939055b9ce35768a4b31d332cacf511ae6f2',
)

if (!accessKeyId || !secretAccessKey) {
  throw new Error('Missing MinIO credentials. Set MINIO_ROOT_USER/MINIO_ROOT_PASSWORD or INTERVIEW_MINIO_ACCESS_KEY/INTERVIEW_MINIO_SECRET_KEY.')
}

run('gradle', ['assembleRelease'], { cwd: androidDir })
verifyReleaseSignature()

const apk = await readFile(apkPath)
const apkStat = await stat(apkPath)
const apkSha256 = createHash('sha256').update(apk).digest('hex')
const versionDir = `${prefix}/v${versionCode}`
const apkObjectKey = `${versionDir}/interview-practice-${versionName}-${versionCode}.apk`
const latestObjectKey = `${prefix}/latest.json`
const downloadUrl = `${publicBaseUrl.replace(/\/+$/, '')}/${bucket}/${apkObjectKey}`
const manifest = {
  appId: 'xyz.kangnasi.interviewpractice',
  versionCode,
  versionName,
  releaseNotes,
  fileName: basename(apkObjectKey),
  fileSize: apkStat.size,
  sha256: apkSha256,
  downloadUrl,
  publishedAt: new Date().toISOString(),
}

await ensureBucket()
await putObject(apkObjectKey, apk, 'application/vnd.android.package-archive')
await putObject(latestObjectKey, Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`), 'application/json; charset=utf-8')
await putObject(`${versionDir}/latest.json`, Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`), 'application/json; charset=utf-8')
await setPublicPolicy()

console.log(JSON.stringify(manifest, null, 2))

function readGradleValue(name) {
  const buildGradle = spawnSync('sed', ['-n', '1,80p', resolve(androidDir, 'app/build.gradle')], {
    encoding: 'utf8',
  }).stdout
  const match = buildGradle.match(new RegExp(`${name}\\s+['"]?([^'"\\n]+)['"]?`))
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

async function ensureBucket() {
  const response = await signedFetch('', { method: 'HEAD' })
  if (response.status === 404) {
    const createResponse = await signedFetch('', { method: 'PUT' })
    if (!createResponse.ok) {
      throw new Error(`Create bucket failed: ${createResponse.status} ${await createResponse.text()}`)
    }
    return
  }
  if (!response.ok) {
    throw new Error(`Check bucket failed: ${response.status} ${await response.text()}`)
  }
}

async function putObject(key, body, contentType) {
  const response = await signedFetch(key, {
    method: 'PUT',
    body,
    headers: {
      'content-type': contentType,
      'content-length': String(body.length),
    },
  })
  if (!response.ok) {
    throw new Error(`Upload ${key} failed: ${response.status} ${await response.text()}`)
  }
}

async function setPublicPolicy() {
  const policy = {
    Version: '2012-10-17',
    Statement: [
      {
        Effect: 'Allow',
        Principal: { AWS: ['*'] },
        Action: ['s3:GetObject'],
        Resource: [`arn:aws:s3:::${bucket}/${prefix}/*`],
      },
    ],
  }
  const response = await signedFetch('?policy', {
    method: 'PUT',
    body: Buffer.from(JSON.stringify(policy)),
    headers: {
      'content-type': 'application/json',
    },
  })
  if (!response.ok) {
    throw new Error(`Set public policy failed: ${response.status} ${await response.text()}`)
  }
}

async function signedFetch(objectKey, options = {}) {
  const endpointUrl = new URL(endpoint)
  const url = new URL(endpointUrl)
  if (objectKey.startsWith('?')) {
    url.pathname = `/${bucket}`
    url.search = objectKey
  } else {
    url.pathname = `/${bucket}${objectKey ? `/${objectKey}` : ''}`
  }
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
  const canonicalQueryString = canonicalQuery(url.searchParams)
  const signedHeaders = [...headers.keys()].map((key) => key.toLowerCase()).sort()
  const canonicalHeaders = signedHeaders
    .map((key) => `${key}:${headers.get(key).trim().replace(/\s+/g, ' ')}\n`)
    .join('')
  const canonicalRequest = [
    method,
    canonicalUri,
    canonicalQueryString,
    canonicalHeaders,
    signedHeaders.join(';'),
    payloadHash,
  ].join('\n')
  const scope = `${dateStamp}/${region}/s3/aws4_request`
  const stringToSign = [
    'AWS4-HMAC-SHA256',
    amzDate,
    scope,
    createHash('sha256').update(canonicalRequest).digest('hex'),
  ].join('\n')
  const signingKey = getSignatureKey(secretAccessKey, dateStamp, region, 's3')
  const signature = createHmac('sha256', signingKey).update(stringToSign).digest('hex')
  headers.set(
    'authorization',
    `AWS4-HMAC-SHA256 Credential=${accessKeyId}/${scope}, SignedHeaders=${signedHeaders.join(';')}, Signature=${signature}`,
  )

  return fetch(url, {
    method,
    headers,
    body: method === 'HEAD' ? undefined : body,
  })
}

function getSignatureKey(key, dateStamp, regionName, serviceName) {
  const kDate = hmac(`AWS4${key}`, dateStamp)
  const kRegion = hmac(kDate, regionName)
  const kService = hmac(kRegion, serviceName)
  return hmac(kService, 'aws4_request')
}

function hmac(key, data) {
  return createHmac('sha256', key).update(data).digest()
}

function canonicalQuery(searchParams) {
  return [...searchParams.entries()]
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .sort()
    .join('&')
}
