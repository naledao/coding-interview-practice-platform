# Android App 发布流程

## 1. 更新版本号

编辑 `android/app/build.gradle`：

- `versionCode` 必须高于线上版本。
- `versionName` 按展示版本递增。

已发布的 `versionCode` 不可覆盖；重新构建必须使用更高版本号。

## 2. 准备私有环境

发布需要以下环境变量，建议分别保存在权限为 `600` 的 `/tmp/*.sh` 文件中：

- 正式签名：`XQQ_RELEASE_STORE_FILE`、`XQQ_RELEASE_STORE_PASSWORD`、`XQQ_RELEASE_KEY_ALIAS`、`XQQ_RELEASE_KEY_PASSWORD`。
- 发布服务：`APP_RELEASE_INTERNAL_TOKEN`。
- MinIO：`MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD`。

`APP_RELEASE_INTERNAL_TOKEN` 来自 Nacos 的 `app-release-service.yaml`。不得将令牌、密码、keystore 或私有环境文件写入仓库。

必须使用与线上 APK 相同的正式签名。若发布脚本提示证书指纹不匹配，应停止并核对 keystore，不得修改或绕过证书校验。

## 3. 执行发布

```bash
cd /root/codes/coding-interview-practice-platform/frontend

source /tmp/xqq-release-signing-env.sh
source /tmp/xqq-minio-env.sh
source /tmp/interview-app-release-env.sh

export INTERVIEW_RELEASE_NOTES='本次版本的简短更新说明'
npm run android:publish
```

发布脚本会依次完成：

1. 构建前端并同步到 Android assets。
2. 构建并校验正式签名的 Release APK。
3. 上传制品并发布到 `app-release-service` 的 Android stable 轨道。
4. 同步 MinIO 中供旧客户端使用的 `latest.json`。

APK 本地输出位置：

```text
android/app/build/outputs/apk/release/app-release.apk
```

## 4. 发布后验证

使用上一个版本号请求公开更新接口，确认返回的新 `sequence`、版本名、SHA-256 和下载地址正确。下面的 `25 / 0.2.23` 仅为示例，发布时替换为实际的上一版本：

```bash
curl --noproxy '*' -fsS \
  -H 'Content-Type: application/json' \
  -d '{
    "platform":"android",
    "architecture":"universal",
    "channel":"stable",
    "currentSequence":25,
    "currentVersion":"0.2.23",
    "packageTypes":["apk"],
    "supportedInstallerSchemaVersions":[1],
    "osVersion":null
  }' \
  http://frp.kangnasi.xyz:8960/api/app/v1/apps/interview-practice-platform/updates/resolve | jq
```

确认旧版兼容清单已经同步：

```bash
curl --noproxy '*' -fsS \
  http://frp.kangnasi.xyz:8084/interview-practice-platform/android/releases/latest.json | jq
```

最后下载发布结果中的 `downloadUrl`，核对 APK：

```bash
curl --noproxy '*' -fLsS "$DOWNLOAD_URL" -o /tmp/interview-practice-release.apk
sha256sum /tmp/interview-practice-release.apk
apksigner verify --print-certs /tmp/interview-practice-release.apk
aapt dump badging /tmp/interview-practice-release.apk | sed -n '1p'
```

发布过程不需要连接、安装或调试 Android 真机。
