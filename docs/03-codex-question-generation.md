# 功能：Codex 自动产题

## 1. 功能目标

Codex CLI 根据管理员上传的 Markdown 文档自动生成 Java 面试单选题。Codex 必须先解析文档，再联网搜索相关资料，最后生成题目、自我 review、打标签并写入题库。

## 2. 用户故事

作为管理员，我希望上传知识文档后不需要人工录题，Codex 可以自动生成质量可用的单选题并直接进入题库。

作为普通用户，我希望刷到的题目有明确答案、解析和标签，不会出现多个正确答案或解析错误。

## 3. 功能范围

### P0

- 后端启动本地 Codex CLI。
- Codex 读取指定导入任务文档。
- Codex 提取知识点。
- Codex 联网搜索资料。
- Codex 生成单选题。
- Codex 自我 review。
- Codex 通过 MCP 写入题库。
- Codex 更新导入任务状态。

### P1

- 分批写入题目。
- 记录每个知识点的处理日志。
- 失败任务支持重试。

### P2

- 产题数量策略配置。
- 按难度比例生成。
- 按标签白名单生成。
- 题目相似度去重。

## 4. 产题主流程

1. 后端创建导入任务。
2. 后端启动 `codex exec`。
3. Codex 通过 MCP 获取任务信息。
4. Codex 标记任务运行中。
5. Codex 通过 MCP 读取 Markdown 内容。
6. Codex 从文档提取知识点。
7. Codex 对知识点联网搜索资料。
8. Codex 生成单选题。
9. Codex 对题目逐题 review。
10. Codex 丢弃或修正不合格题目。
11. Codex 通过 MCP 批量写入题目。
12. Codex 标记任务成功。
13. 后端文档状态变为处理完成。

## 5. Codex Prompt 要求

每个任务 prompt 必须包含：

- `importJobId`。
- 当前任务目标。
- 只能生成单选题。
- 必须联网搜索。
- 必须自我 review。
- 必须通过 MCP 写库。
- 必须更新任务状态。
- 失败时必须写日志并标记失败。

Prompt 示例：

```text
你是 Java 面试题库生产代理。

请处理 importJobId={jobId} 的导入任务：
1. 通过 MCP 读取 Markdown 文档。
2. 提取适合 Java 面试的知识点。
3. 对每个知识点联网搜索资料，补充并校验事实。
4. 生成单选题，每题 4 个选项，且只有 1 个正确答案。
5. 对每道题进行自我 review，修正不准确、模糊或存在多个正确答案的问题。
6. 为每道题添加 1 到 5 个标签。
7. 通过 MCP 写入题库。
8. 更新导入任务状态。

禁止：
- 生成多选题、判断题、简答题。
- 生成无明确答案的题。
- 生成多个选项都合理的题。
- 直接编造无法校验的技术结论。
```

## 6. 题目生成规则

每道题必须包含：

- 题干。
- A/B/C/D 四个选项。
- 唯一正确答案。
- 答案解析。
- 难度。
- 知识点。
- 标签。
- Codex review 摘要。

示例：

```json
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
```

## 7. 自我 review 规则

Codex 写库前必须逐题确认：

- 题干是否清楚。
- 是否只有一个正确答案。
- 正确答案和解析是否一致。
- 错误选项是否明确错误。
- 是否存在 Java 版本差异。
- 是否有过时知识。
- 标签是否准确。
- 难度是否合理。

不满足要求时：

- 可以修改题目。
- 可以删除题目。
- 不允许把不确定题目写入题库。

## 8. 联网搜索规则

Codex 必须对提取出的知识点联网搜索。

优先来源：

- Oracle Java 官方文档。
- OpenJDK 文档。
- Spring 官方文档。
- MySQL 官方文档。
- Redis 官方文档。
- 权威项目官方文档。
- 高质量技术文章。

社区讨论只能作为辅助参考。

## 9. 后端执行规则

后端负责：

- 创建 Codex 工作目录。
- 渲染 prompt。
- 启动 `codex exec`。
- 读取 Codex stdout JSONL。
- 记录 Codex stderr 日志。
- 监听进程退出码。
- 超时或异常时标记任务失败。

推荐命令形式：

```bash
codex exec --json --sandbox workspace-write "任务 prompt"
```

## 10. 异常规则

- Codex CLI 不存在：任务失败。
- Codex 未登录：任务失败。
- MCP Server 不可用：任务失败。
- 文档无法读取：任务失败。
- 联网搜索失败：记录警告；如果无法校验关键事实，则跳过相关题。
- 所有题目都未通过 review：任务失败。
- 部分题目写入失败：记录错误，继续写入其他合格题。

## 11. 验收标准

- 上传 Java 知识文档后自动启动 Codex。
- Codex 能通过 MCP 读取文档。
- Codex 能生成单选题。
- 每道题有 4 个选项。
- 每道题只有 1 个正确答案。
- 每道题有解析、难度和标签。
- Codex 自我 review 结果写入题目记录。
- 成功任务展示生成题目数量。
- 失败任务展示失败原因。
