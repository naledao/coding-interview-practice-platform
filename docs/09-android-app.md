# 功能：Android WebView APP

## 1. 功能目标

安卓 APP 使用原生 Android WebView 加载 Vue 多页面应用，让用户以原生 APP 的形式完成刷题、复习和统计。

## 2. 用户故事

作为用户，我希望安装一个安卓 APP，打开后就能登录刷 Java 面试题，不需要通过浏览器访问网站。

## 3. 功能范围

### P0

- Android APP 启动。
- WebView 加载 Vue 静态资源。
- 支持登录态保存。
- 支持访问后端接口。
- 支持返回键。
- 支持基础错误页。

### P1

- 文件选择，用于管理员上传 md。
- APP 版本展示。
- APK 更新入口。
- 网络错误重试页。

### P2

- 原生推送。
- 原生下载管理。
- 离线题库。
- 原生安全存储 token。

## 4. APP 页面

APP 内页面由 Vue 实现：

- 登录页。
- 首页。
- 标签页。
- 刷题页。
- 错题本页。
- 收藏页。
- 搜索页。
- 统计页。
- 我的页。
- 管理员上传页。
- 导入任务页。

## 5. WebView 配置

必须支持：

- JavaScript。
- DOM Storage。
- 加载本地 assets。
- 访问后端 HTTP API。
- 返回键导航。

建议：

- 禁止不必要的任意文件访问。
- 限制 JS Bridge 暴露面。
- 对 WebView 错误进行友好提示。

## 6. 路由规则

Vue Router 使用 hash 模式，适配本地静态资源：

```js
import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes
})
```

Vite 使用相对路径：

```js
export default defineConfig({
  base: './'
})
```

## 7. 登录态规则

一期使用 localStorage 保存 token。

规则：

- 登录成功后保存 token。
- 请求接口时带 token。
- 401 时清理 token 并跳转登录页。
- 退出登录时清理 token。

后续可以迁移到 Android 原生安全存储。

## 8. 返回键规则

- WebView 有历史记录时，返回上一页。
- 当前是首页时，再按一次退出 APP。
- 当前是刷题已答题状态时，返回上一页不丢失已展示结果。
- 当前有弹窗时，先关闭弹窗。

## 9. 文件上传规则

管理员上传 md 时：

- Vue 调用 `<input type="file">`。
- Android WebView 处理文件选择。
- 只允许选择 `.md` 文件。
- 上传到后端文档接口。

## 10. 网络规则

如果后端使用 HTTP，需要 Android 允许明文流量。

要求：

- 开发环境可访问本机或局域网后端。
- 生产环境使用配置的后端地址。
- 网络失败时展示重试。

## 11. JS Bridge 规则

一期尽量少用 JS Bridge。

可暴露方法：

```text
AndroidBridge.getAppVersion()
AndroidBridge.openInstallPermissionSettings()
```

禁止：

- 暴露任意 shell 执行能力。
- 暴露任意文件读取能力。
- 暴露敏感配置。

## 12. 构建流程

1. 构建 Vue 静态资源。
2. 复制到 Android assets。
3. Gradle 构建 APK。
4. 安装到真机测试。

## 13. 真机验收标准

- APP 可以安装。
- APP 可以启动。
- 首页不是白屏。
- 登录功能正常。
- 可以拉取题目。
- 可以提交答案。
- 可以查看解析。
- 错题本和收藏可用。
- 返回键行为正常。
- 网络异常时有提示。

## 14. 当前实现状态

已新增原生 Android WebView 工程：

- Android 工程路径：`android/`
- 包名：`xyz.kangnasi.interviewpractice`
- 本地入口：`https://appassets.androidplatform.net/assets/web/index.html`
- APK 输出：`android/app/build/outputs/apk/debug/app-debug.apk`

已完成：

- WebView 启动并加载 Vue 静态资源。
- Gradle 构建前自动执行前端构建并同步到 Android assets。
- JavaScript、DOM Storage、HTTP 明文访问和 localStorage 登录态。
- Vue hash 路由和 Vite 相对路径适配。
- Android WebView 返回键：有历史返回上一页，无历史二次返回退出。
- 基础页面加载错误页和重试。
- 管理员上传页 `<input type="file">` 的 Android 文件选择，限制 Markdown/ZIP 文件。
- JS Bridge：`AndroidBridge.getAppVersion()`、`AndroidBridge.openInstallPermissionSettings()`。
- 我的页展示 APP 版本、后端地址配置和安装权限入口。

构建命令：

```bash
cd frontend
npm run android:debug
```

安装并启动：

```bash
adb connect 127.0.0.1:5555
adb -s 127.0.0.1:5555 install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:5555 shell monkey -p xyz.kangnasi.interviewpractice -c android.intent.category.LAUNCHER 1
```
