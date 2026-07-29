# 功能：MCP 工具

## 1. 功能目标

后端通过 MCP 向 Codex 暴露受控工具，让 Codex 可以完成产题任务，但不能直接访问数据库、任意文件或敏感配置。

## 2. 使用者

主要使用者是 Codex。

管理员和普通用户不会直接调用 MCP 工具。

## 3. 功能范围

### P0

- 查询导入任务。
- 读取导入文档。
- 追加生成日志。
- 批量创建题目。
- 标记任务运行中。
- 标记任务成功。
- 标记任务失败。

### P1

- 查询已生成题目数量。
- 查询推荐标签。
- 校验题目但不写入。

### P2

- 题目相似度检测。
- 题目质量评分。
- 题目自动下线建议。

## 4. 工具列表

### `get_import_job`

用途：

读取导入任务信息。

输入：

```json
{
  "importJobId": 123
}
```

输出：

```json
{
  "importJobId": 123,
  "status": "PENDING",
  "documentId": 456,
  "documentName": "jvm.md"
}
```

### `read_import_document`

用途：

读取导入任务绑定的 Markdown 文档。

输入：

```json
{
  "importJobId": 123
}
```

输出：

```json
{
  "importJobId": 123,
  "documentId": 456,
  "filename": "jvm.md",
  "content": "# JVM\n..."
}
```

限制：

- 只能按 `importJobId` 读取。
- 不允许传文件路径。
- 文件过大时返回错误。

### `append_generation_log`

用途：

记录 Codex 产题过程。

输入：

```json
{
  "importJobId": 123,
  "level": "INFO",
  "message": "提取到 8 个知识点",
  "payload": {
    "knowledgePoints": ["JVM内存区域", "GC Roots"]
  }
}
```

输出：

```json
{
  "ok": true
}
```

### `create_question_batch`

用途：

批量创建题目、选项和标签。

输入：

```json
{
  "importJobId": 123,
  "questions": [
    {
      "stem": "关于 JVM 运行时数据区，下列说法正确的是？",
      "difficulty": "MEDIUM",
      "knowledgePoint": "JVM运行时数据区",
      "answerAnalysis": "程序计数器是线程私有的...",
      "codexReviewSummary": "已检查唯一答案和版本表述。",
      "tags": ["JVM", "Java基础"],
      "options": [
        {
          "optionKey": "A",
          "content": "程序计数器是线程共享的",
          "correct": false
        },
        {
          "optionKey": "B",
          "content": "程序计数器是线程私有的",
          "correct": true
        },
        {
          "optionKey": "C",
          "content": "Java 堆是线程私有的",
          "correct": false
        },
        {
          "optionKey": "D",
          "content": "方法区在所有 JVM 实现中都必须叫永久代",
          "correct": false
        }
      ]
    }
  ]
}
```

输出：

```json
{
  "ok": true,
  "createdQuestionIds": [10001],
  "createdCount": 1,
  "skippedCount": 0,
  "errors": []
}
```

### `get_generated_question_count`

用途：

查询指定导入任务当前已经写入的题目数量。

输入：

```json
{
  "importJobId": 123
}
```

输出：

```json
{
  "importJobId": 123,
  "generatedQuestionCount": 12
}
```

### `get_recommended_tags`

用途：

查询推荐标签，辅助 Codex 打标签。

输入：

```json
{}
```

输出：

```json
{
  "tags": [
    {
      "tagId": 1,
      "name": "JVM",
      "normalizedName": "jvm",
      "category": "JAVA",
      "questionCount": 30
    }
  ]
}
```

说明：

- 已存在标签返回真实 `tagId` 和 `questionCount`。
- 尚未创建的推荐标签返回 `tagId=null`、`questionCount=0`。

### `validate_question_batch`

用途：

校验题目、选项和标签，但不写入题库，也不创建标签。

输入：

```json
{
  "importJobId": 123,
  "questions": [
    {
      "stem": "关于 Java 中 volatile 的说法，哪一项是正确的？",
      "difficulty": "MEDIUM",
      "knowledgePoint": "volatile 内存语义",
      "answerAnalysis": "volatile 能保证变量可见性，并限制特定指令重排序，但不能保证 i++ 这类复合操作的原子性。",
      "codexReviewSummary": "已检查唯一答案和常见混淆点。",
      "tags": ["Java并发", "JMM", "volatile"],
      "options": [
        {
          "optionKey": "A",
          "content": "volatile 可以保证 i++ 的原子性",
          "correct": false
        },
        {
          "optionKey": "B",
          "content": "volatile 可以保证变量可见性，并提供相关内存语义",
          "correct": true
        },
        {
          "optionKey": "C",
          "content": "volatile 的作用完全等同于 synchronized",
          "correct": false
        },
        {
          "optionKey": "D",
          "content": "volatile 只能修饰局部变量",
          "correct": false
        }
      ]
    }
  ]
}
```

输出：

```json
{
  "ok": true,
  "validCount": 1,
  "skippedCount": 0,
  "errors": []
}
```

### `mark_import_job_running`

用途：

标记任务运行中。

输入：

```json
{
  "importJobId": 123
}
```

### `mark_import_job_succeeded`

用途：

标记任务成功。

输入：

```json
{
  "importJobId": 123,
  "generatedQuestionCount": 12,
  "summary": "生成 12 道 Java 单选题"
}
```

### `mark_import_job_failed`

用途：

标记任务失败。

输入：

```json
{
  "importJobId": 123,
  "reason": "没有题目通过自我 review"
}
```

## 5. 工具调用顺序

正常流程：

1. `get_import_job`
2. `mark_import_job_running`
3. `read_import_document`
4. `append_generation_log`
5. `get_recommended_tags`
6. `validate_question_batch`
7. `create_question_batch`
8. `get_generated_question_count`
9. `append_generation_log`
10. `mark_import_job_succeeded`

失败流程：

1. `append_generation_log`
2. `mark_import_job_failed`

## 6. 后端校验规则

`create_question_batch` 必须校验：

- 任务存在。
- 任务状态为 `RUNNING`。
- 题干不能为空。
- 解析不能为空。
- 难度合法。
- 至少 4 个选项。
- 恰好 1 个正确选项。
- 选项 key 不重复。
- 至少 1 个标签。
- 标签长度合法。
- 每批题目数量不超过限制。

## 7. 幂等规则

- 同一任务内相同题干只写入一次。
- 标签按归一化名称 upsert。
- 题目标签关联去重。
- 重复标记 `RUNNING` 不报错。
- 已成功任务不允许继续写入题目。

## 8. 安全规则

- MCP Server 只监听本机地址。
- HTTP MCP 必须使用 bearer token。
- token 通过环境变量注入。
- Codex 不能拿到数据库密码。
- Codex 不能传入 SQL。
- Codex 不能传入任意文件路径。
- Codex 不能删除用户数据。

## 9. 错误格式

```json
{
  "ok": false,
  "errorCode": "INVALID_QUESTION",
  "message": "Question must have exactly one correct option.",
  "details": {
    "questionIndex": 2
  }
}
```

错误码：

- `JOB_NOT_FOUND`
- `JOB_STATUS_INVALID`
- `DOCUMENT_NOT_FOUND`
- `DOCUMENT_TOO_LARGE`
- `INVALID_ARGUMENT`
- `INVALID_QUESTION`
- `DATABASE_ERROR`
- `INTERNAL_ERROR`

## 10. 验收标准

- Codex 能通过 MCP 读取文档。
- Codex 能通过 MCP 写入题目。
- 非法题目会被 MCP 拒绝。
- 工具调用日志可追踪。
- MCP 不暴露任意 SQL 或任意文件读取能力。
