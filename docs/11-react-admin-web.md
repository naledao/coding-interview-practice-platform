# 功能：React 网页管理端

## 1. 功能目标

在保留 Android App 管理功能的同时，提供适合桌面浏览器使用的 React 管理控制台。App 与网页端共用同一套 Spring Boot API、管理员权限、文档、任务、日志和题库数据。

## 2. 访问方式

- 开发模式：`http://127.0.0.1:5174/admin/`
- 后端托管：`http://127.0.0.1:8904/admin`

React 使用 hash 路由，因此详情页刷新和直接访问不需要额外的服务端路由配置。

## 3. 当前功能

- 管理员邮箱验证码登录和 JWT 鉴权。
- 管理总览：文档数、题目数、执行中与失败任务数。
- 上传 Markdown、Markdown 扩展名文件或 ZIP 压缩包。
- 配置上传后是否自动创建 Codex 产题任务。
- 查看上传解析状态、跳过文件和批次内文档，并自动刷新后台解析进度。
- 查看知识文档列表、Markdown 原文和最近导入任务。
- 手动为已有文档创建新的导入任务。
- 按状态、文档名称和时间筛选导入任务。
- 查看任务状态、生成题目、Codex Session、失败原因和执行日志。
- 重试失败任务，并自动刷新等待中或执行中的任务。
- 按状态和来源任务筛选题目。
- 查看题干、选项、正确答案、解析和 Codex Review，并上下线题目。

## 4. 后端职责

React 端只负责展示和提交操作。以下能力继续由后端统一负责：

- 管理员身份与接口权限校验。
- 文件类型、大小、ZIP 条目和 Markdown 内容校验。
- 上传文件持久化与文档元数据管理。
- RabbitMQ 解析任务和 Codex 任务调度。
- MCP 工具访问控制、题目校验与数据库写入。
- 任务状态、失败原因、日志和生成题目关联。

因此从 Android App 或 React 网页上传的数据可以在两个入口中统一查看和管理。

## 5. 构建集成

React 源码位于 `admin-web/`。Maven 的 `generate-resources` 阶段会执行：

```bash
npm ci --prefer-offline --no-audit --no-fund
npm run build
```

Vite 构建产物随后被复制到 `target/classes/static/admin`，最终进入 Spring Boot JAR 或 Native Image 的静态资源。`/admin` 会重定向到 `/admin/index.html`。

## 6. 开发命令

```bash
npm --prefix admin-web ci
npm --prefix admin-web run dev
```

通过 `VITE_API_TARGET` 可以修改 Vite 开发代理目标：

```bash
VITE_API_TARGET=http://127.0.0.1:8904 npm --prefix admin-web run dev
```

生产环境默认使用同源 `/api`。如果确实需要把管理页面和 API 分开部署，可以在构建时设置 `VITE_API_BASE_URL`。

## 7. 安全规则

- `/admin` 静态页面可以公开加载，但所有 `/api/admin/**` 接口必须具有 `ADMIN` 角色。
- JWT 失效时网页端清理本地登录状态并返回登录页。
- 非管理员账号即使成功登录，也不能进入控制台或访问管理 API。
- 网页端不保存服务端数据库、RabbitMQ、邮件、Codex 或对象存储凭据。
- 文件校验不能只依赖浏览器，后端必须始终执行完整校验。
