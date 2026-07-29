# 功能：管理员知识文档上传

## 1. 功能目标

管理员上传 Java 面试相关 Markdown 文档，或上传包含 Markdown 文档的压缩包。上传成功只负责保存原始文件、创建上传批次和解析任务，并把解析任务投递到 RabbitMQ。系统只有一个解析消费者线程监听队列，消费后在 MySQL 中更新解析任务状态，识别并保存可处理的 Markdown 文档，为每个 Markdown 文档创建 Codex 产题任务。解析完成后默认自动触发 Codex 处理。

## 2. 用户故事

作为管理员，我希望把已有 Java 知识点 Markdown 文档上传到系统，也希望可以直接上传整理好的压缩包。上传接口快速返回排队结果，后台单线程解析任务自动读取 Markdown 文档，并让 Codex 自动从这些文档中生成单选题，减少人工拆包和录题成本。

## 3. 核心对象

### 上传批次

一次上传行为形成一个上传批次，用来记录管理员上传的原始文件。

上传批次保存：

- 上传批次 ID。
- 上传类型：`MARKDOWN` 或 `ZIP`。
- 原始上传文件名。
- 原始上传文件大小。
- 原始上传文件 `sha256`。
- 上传人。
- 上传时间。
- 解析状态。
- 识别出的 Markdown 文档数量。
- 忽略或跳过的文件数量。

说明：

- 直接上传 `.md` 时，一个上传批次对应一个 Markdown 文档。
- 上传 `.zip` 时，一个上传批次可以对应多个 Markdown 文档。
- ZIP 原文件只作为上传来源保存，不作为 Codex 产题输入。
- 上传成功后，上传批次先进入 `QUEUED`，等待 RabbitMQ 消费者解析。

### 解析任务

解析任务记录一次后台解析执行，必须落库到 MySQL。

解析任务保存：

- 解析任务 ID。
- 所属上传批次 ID。
- 任务状态：`PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`。
- 是否在解析成功后自动创建并启动 Codex 导入任务。
- 失败原因。
- 创建时间、开始时间、完成时间、更新时间。

说明：

- 上传接口创建解析任务后投递 RabbitMQ 消息。
- RabbitMQ 消息只携带解析任务 ID 和上传批次 ID。
- 系统只启动一个解析消费者线程监听该队列。
- 解析消费者从 MySQL 读取解析任务和上传批次，执行 Markdown 或 ZIP 解析。
- 解析成功后才创建知识文档和导入任务。
- 解析失败时更新解析任务失败原因，并把上传批次标记为 `FAILED`。

### 知识文档

知识文档是 Codex 处理的最小单位，必须是 Markdown 内容。

知识文档保存：

- 文档 ID。
- 展示文件名。
- 来源类型：`MARKDOWN` 或 `ZIP`。
- 所属上传批次 ID。
- 如果来源于 ZIP，记录压缩包原始文件名和包内路径 `archiveEntryPath`。
- Markdown 文件大小。
- Markdown 内容 `sha256`。
- Markdown 实际存储路径。
- 文档状态。

说明：

- Codex 只处理知识文档，不处理上传批次。
- ZIP 内每个有效 `.md` 都生成独立知识文档。
- ZIP 内同名 `.md` 通过 `archiveEntryPath` 区分。

### 导入任务

导入任务记录一次 Codex 产题执行。

说明：

- 每个知识文档解析成功后默认创建一个导入任务。
- ZIP 解析成功后，为包内每个有效 Markdown 文档分别创建导入任务。
- 手动重新触发导入时，为目标知识文档创建新的导入任务。

## 4. 功能范围

### P0

- 上传 `.md` 文件。
- 上传 `.zip` 压缩包。
- 保存原始文件。
- 计算上传文件 hash。
- 创建上传批次记录。
- 创建 MySQL 解析任务记录。
- 上传成功后向 RabbitMQ 投递解析消息。
- 只有一个解析消费者线程监听 RabbitMQ。
- 解析消费者解压压缩包并扫描其中的 `.md` 文件。
- 计算每个 Markdown 文档内容 hash。
- 创建文档记录。
- 为压缩包内每个 `.md` 文件创建独立文档记录。
- 为每个 Markdown 文档创建导入任务。
- 解析成功后自动触发 Codex。
- 查看文档列表。
- 查看文档详情。
- 查看压缩包内 Markdown 文档列表。

### P1

- 手动触发导入。
- 重新上传同名文档时提示。
- 文件内容预览。
- 压缩包内无效文件提示。

### P2

- 批量上传。
- 文档分组。
- 文档版本管理。
- 支持 `.tar`、`.tar.gz`、`.7z` 等更多压缩格式。

## 5. 页面功能

### 文档上传页

展示：

- 文件选择区域。
- 上传说明，明确支持 `.md` 和 `.zip`。
- 当前上传进度。
- 上传排队结果。
- 解析任务状态。

操作：

- 选择 Markdown 文件或 ZIP 压缩包。
- 点击上传。
- 上传成功后跳转本次上传结果页，展示上传批次和解析任务状态。
- 解析完成后展示识别出的 Markdown 文档和对应导入任务。

校验：

- 只允许 `.md` 文件和 `.zip` 压缩包。
- 文件不能为空。
- 文件大小不能超过系统限制。
- ZIP 内必须至少包含一个 `.md` 文件，由后台解析任务校验。
- ZIP 内文件路径不允许路径穿越，由后台解析任务校验。
- ZIP 解压后的总大小和文件数量不能超过系统限制，由后台解析任务校验。

### 上传结果页

展示：

- 原始上传文件名。
- 上传文件类型：`MARKDOWN` 或 `ZIP`。
- 上传文件大小。
- 上传批次解析状态。
- 识别到的 Markdown 文件数量。
- 忽略的非 Markdown 文件数量。
- 跳过的 Markdown 文件数量和原因。
- 每个 Markdown 文件的包内路径、文件大小、文档状态、关联任务状态。

操作：

- 查看 Markdown 文档详情。
- 查看导入任务。
- 对单个 Markdown 文档重新触发导入。

### 文档列表页

展示：

- 文件名。
- 来源类型：单文件上传或压缩包内文件。
- 压缩包原始文件名。
- 压缩包内路径。
- 文件大小。
- 上传人。
- 上传时间。
- 文档状态。
- 关联任务状态。

操作：

- 查看详情。
- 触发导入。
- 查看任务。

### 文档详情页

展示：

- 文件基础信息。
- 来源信息。
- 如果来源于压缩包，展示压缩包原始文件名和包内路径。
- 文档状态。
- 上传时间。
- 最近一次导入任务。
- 生成题目数量。

操作：

- 重新触发导入。
- 查看导入日志。

## 6. 主流程

### 单个 Markdown 上传

1. 管理员进入文档上传页。
2. 选择 Markdown 文件。
3. 前端校验文件后上传。
4. 后端保存文件。
5. 后端计算 `sha256`。
6. 后端创建上传批次记录，解析状态为 `QUEUED`。
7. 后端创建 MySQL 解析任务，任务状态为 `PENDING`。
8. 后端在上传事务提交后投递 RabbitMQ 解析消息。
9. 上传接口返回排队结果，页面跳转到上传结果页。
10. 单线程 RabbitMQ 消费者收到解析消息。
11. 消费者把解析任务状态更新为 `RUNNING`，上传批次解析状态更新为 `PARSING`。
12. 消费者校验 Markdown 内容并创建知识文档记录。
13. 消费者创建导入任务。
14. 消费者把解析任务状态更新为 `SUCCEEDED`，上传批次解析状态更新为 `PARSED`。
15. 如果 `autoStart=true`，导入任务创建后自动触发 Codex CLI。
16. 管理员在上传结果页查看解析任务、文档和导入任务状态。

### ZIP 压缩包上传

1. 管理员进入文档上传页。
2. 选择 ZIP 压缩包。
3. 前端校验文件类型和大小后上传。
4. 后端保存原始 ZIP 文件。
5. 后端计算 ZIP 文件 `sha256`。
6. 后端创建上传批次记录，解析状态为 `QUEUED`。
7. 后端创建 MySQL 解析任务，任务状态为 `PENDING`。
8. 后端在上传事务提交后投递 RabbitMQ 解析消息。
9. 上传接口返回排队结果，页面跳转到上传结果页。
10. 单线程 RabbitMQ 消费者收到解析消息。
11. 消费者把解析任务状态更新为 `RUNNING`，上传批次解析状态更新为 `PARSING`。
12. 消费者安全读取 ZIP，扫描 ZIP 内部文件，只识别 `.md` 文件。
13. 消费者过滤目录项、隐藏系统文件和非 Markdown 文件。
14. 消费者校验每个 Markdown 文件不能为空。
15. 消费者为每个有效 Markdown 文件计算 `sha256`。
16. 消费者为每个有效 Markdown 文件创建知识文档记录，记录包内路径。
17. 消费者为每个知识文档创建导入任务。
18. 消费者更新上传批次解析状态、识别数量、忽略数量和跳过原因。
19. 消费者把解析任务状态更新为 `SUCCEEDED`，上传批次解析状态更新为 `PARSED`。
20. 如果 `autoStart=true`，导入任务创建后自动触发 Codex CLI。
21. 管理员查看每个 Markdown 文档和任务状态。

## 7. 接口

### 上传文档

`POST /api/admin/documents`

请求类型：`multipart/form-data`

字段：

- `file`：Markdown 文件或 ZIP 压缩包。
- `autoStart`：是否自动触发 Codex，默认 `true`。

上传成功响应：

```json
{
  "uploadId": 3001,
  "uploadType": "MARKDOWN",
  "originalFilename": "jvm.md",
  "parseStatus": "QUEUED",
  "parseTaskId": 4001,
  "parseTaskStatus": "PENDING",
  "documentId": null,
  "importJobId": null,
  "documentStatus": null,
  "jobStatus": null,
  "documentCount": 0,
  "ignoredFileCount": 0,
  "skippedFileCount": 0,
  "skippedFiles": [],
  "documents": []
}
```

说明：

- 上传接口不解析文件内容。
- 上传接口不直接创建知识文档。
- 上传接口不直接创建导入任务。
- 上传接口只返回上传批次和解析任务排队状态。
- Markdown 和 ZIP 上传成功响应结构一致，区别只在 `uploadType` 和 `originalFilename`。

ZIP 上传成功响应示例：

```json
{
  "uploadId": 3002,
  "uploadType": "ZIP",
  "originalFilename": "java-notes.zip",
  "parseStatus": "QUEUED",
  "parseTaskId": 4002,
  "parseTaskStatus": "PENDING",
  "documentId": null,
  "importJobId": null,
  "documentStatus": null,
  "jobStatus": null,
  "documentCount": 0,
  "ignoredFileCount": 0,
  "skippedFileCount": 0,
  "skippedFiles": [],
  "documents": []
}
```

### 上传结果详情

`GET /api/admin/document-uploads/{uploadId}`

响应：

```json
{
  "id": 3002,
  "uploadType": "ZIP",
  "originalFilename": "java-notes.zip",
  "fileSize": 102400,
  "uploadSha256": "xxx",
  "parseStatus": "PARSED",
  "parseTaskId": 4002,
  "parseTaskStatus": "SUCCEEDED",
  "parseFailedReason": null,
  "documentCount": 2,
  "ignoredFileCount": 3,
  "skippedFileCount": 1,
  "skippedFiles": [
    {
      "archiveEntryPath": "empty.md",
      "reason": "EMPTY_MARKDOWN"
    }
  ],
  "documents": [
    {
      "documentId": 1002,
      "originalFilename": "gc.md",
      "archiveEntryPath": "jvm/gc.md",
      "fileSize": 20480,
      "documentStatus": "UPLOADED",
      "latestJob": {
        "id": 2002,
        "status": "PENDING"
      }
    }
  ]
}
```

### 文档列表

`GET /api/admin/documents?page=1&pageSize=20`

响应项：

```json
{
  "id": 1001,
  "uploadId": 3001,
  "originalFilename": "jvm.md",
  "sourceType": "MARKDOWN",
  "archiveOriginalFilename": null,
  "archiveEntryPath": null,
  "fileSize": 20480,
  "status": "PROCESSING",
  "uploadedBy": "admin",
  "createdAt": "2026-06-29 10:00:00",
  "latestJob": {
    "id": 2001,
    "status": "RUNNING",
    "generatedQuestionCount": 0
  }
}
```

### 文档详情

`GET /api/admin/documents/{documentId}`

如果文档来源于 ZIP，详情需要返回：

```json
{
  "id": 1002,
  "uploadId": 3002,
  "originalFilename": "gc.md",
  "sourceType": "ZIP",
  "archiveOriginalFilename": "java-notes.zip",
  "archiveEntryPath": "jvm/gc.md",
  "contentSha256": "xxx",
  "status": "PROCESSING"
}
```

### 触发导入

`POST /api/admin/documents/{documentId}/import-jobs`

响应：

```json
{
  "importJobId": 2002,
  "status": "PENDING"
}
```

## 8. 状态规则

### 上传批次解析状态

- `QUEUED`：上传成功，已创建解析任务并等待 RabbitMQ 消费。
- `PARSING`：后台解析中。
- `PARSED`：解析完成，已识别出可处理的 Markdown 文档。
- `FAILED`：解析失败，没有创建可处理的知识文档。

### 解析任务状态

- `PENDING`：解析任务已创建，等待 RabbitMQ 消费。
- `RUNNING`：解析消费者正在处理。
- `SUCCEEDED`：解析成功，已创建知识文档和导入任务。
- `FAILED`：解析失败，失败原因写入解析任务。

### 文档状态

- 解析成功并创建知识文档后：`UPLOADED`。
- Codex 任务运行时：`PROCESSING`。
- Codex 任务成功时：`PROCESSED`。
- Codex 任务失败时：`FAILED`。

### 上传来源类型

- `MARKDOWN`：直接上传的 Markdown 文件。
- `ZIP`：从 ZIP 压缩包中提取出的 Markdown 文件。

### ZIP 解析规则

- ZIP 本身只作为上传来源保存，不直接交给 Codex 处理。
- Codex 只处理 ZIP 内部识别出的 Markdown 文件。
- ZIP 内每个 Markdown 文件独立计算 hash、独立建文档记录、独立建导入任务。
- ZIP 内目录结构保留为 `archiveEntryPath`，用于展示、排查和重复判断。
- ZIP 内非 Markdown 文件默认忽略，但上传结果中展示忽略数量。
- ZIP 内 Markdown 文件名重复时，以 `archiveEntryPath` 区分。

### RabbitMQ 解析规则

- 上传接口保存原始文件和 MySQL 解析任务后，向 RabbitMQ 投递解析消息。
- RabbitMQ 队列必须持久化。
- 解析消息投递必须发生在上传事务提交之后，避免消费者读取到未提交的任务。
- 解析消费者并发数固定为 1，最大并发数固定为 1，prefetch 为 1。
- 解析消费者处理失败时要更新 MySQL 中的解析任务状态和失败原因。
- 解析消费者处理完失败状态后可以确认消息，避免同一个业务失败无限重投。
- 如果部署多个后端实例，必须保证只有一个实例启用解析消费者，或额外引入分布式锁。

### 重复上传规则

一期允许重复上传，但记录 `contentSha256`。

后续可以做：

- 相同 hash 提醒管理员。
- 相同 hash 默认不重复创建任务。
- ZIP 内相同 `archiveEntryPath` 和相同 hash 的 Markdown 文件提示重复。

## 9. 文件存储规则

推荐保存路径：

```text
runtime/uploads/knowledge-uploads/{uploadId}/{originalFilename}
runtime/uploads/knowledge-documents/{documentId}/{safeFilename}
```

要求：

- 不允许路径穿越。
- 原始文件名只用于展示。
- 实际存储路径由系统生成。
- 上传目录不提交到 Git。
- ZIP 解压时必须限制单文件大小、总解压大小和文件数量，避免恶意压缩包。
- ZIP 内部路径只作为 `archiveEntryPath` 展示和追踪，不直接拼接为最终存储路径。
- 对 ZIP 内 Markdown 文件，系统应保存提取后的 Markdown 文件副本，供 Codex 和预览功能使用。

## 10. 异常规则

- 非 `.md` 或 `.zip` 文件：提示“仅支持 Markdown 文件或 ZIP 压缩包”。
- 文件为空：提示“文件内容不能为空”。
- 文件过大：提示“文件超过大小限制”。
- ZIP 文件为空：提示“压缩包内容不能为空”。
- ZIP 内没有 Markdown 文件：提示“压缩包内未找到 Markdown 文件”。
- ZIP 解压失败：提示“压缩包解析失败，请检查文件格式”。
- ZIP 内文件过多：提示“压缩包内文件数量超过限制”。
- ZIP 解压后内容过大：提示“压缩包解压后内容超过大小限制”。
- ZIP 内 Markdown 文件为空：跳过该文件并在上传结果中展示失败原因。
- ZIP 内路径不安全：跳过该文件并在上传结果中展示失败原因。
- 保存失败：提示“文件保存失败，请重试”。
- Codex 启动失败：文档保留，任务标记失败。

## 11. 验收标准

- 管理员可以上传 `.md` 文件。
- 管理员可以上传包含 `.md` 文件的 `.zip` 压缩包。
- 每次上传都会生成上传批次记录。
- 上传成功后生成 MySQL 解析任务记录。
- 上传成功后解析状态为 `QUEUED`，解析任务状态为 `PENDING`。
- 上传成功后投递 RabbitMQ 解析消息。
- 上传接口响应中不直接返回文档 ID 或导入任务 ID。
- RabbitMQ 解析消费者只有一个线程处理该队列。
- 解析消费者开始处理后，上传批次解析状态变为 `PARSING`，解析任务状态变为 `RUNNING`。
- Markdown 解析成功后生成 1 条知识文档记录和 1 条导入任务。
- ZIP 解析成功后，压缩包内每个有效 `.md` 都生成独立文档记录。
- ZIP 解析成功后，压缩包内每个有效 `.md` 都生成独立导入任务。
- 解析成功后，上传批次解析状态为 `PARSED`，解析任务状态为 `SUCCEEDED`。
- 解析失败后，上传批次解析状态为 `FAILED`，解析任务状态为 `FAILED`，并保存失败原因。
- 管理员可以在列表或上传结果中看到压缩包内 Markdown 文件的包内路径和任务状态。
- 解析成功后自动触发 Codex 任务。
- 文档列表可以看到上传记录和任务状态。
- 非管理员不能上传文档。
