package xyz.kangnasi.interview.importjob;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.document.KnowledgeDocument;
import xyz.kangnasi.interview.document.KnowledgeDocumentRepository;

@Service
public class ImportJobExecutionService {

    private final ImportJobRepository importJobRepository;
    private final ImportJobLogRepository importJobLogRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final TransactionTemplate transactionTemplate;
    private final CodexQuestionGenerationPrompt questionGenerationPrompt;
    private final String codexCommand;
    private final String sandboxMode;
    private final String toolBaseUrl;
    private final String toolToken;
    private final String mcpServerName;
    private final String mcpUrl;
    private final String proxyUrl;
    private final String noProxy;
    private final Path workRoot;
    private final Duration processTimeout;

    public ImportJobExecutionService(
            ImportJobRepository importJobRepository,
            ImportJobLogRepository importJobLogRepository,
            KnowledgeDocumentRepository documentRepository,
            TransactionTemplate transactionTemplate,
            CodexQuestionGenerationPrompt questionGenerationPrompt,
            @Value("${app.codex.command:codex}") String codexCommand,
            @Value("${app.codex.sandbox:workspace-write}") String sandboxMode,
            @Value("${app.codex.tool-base-url:http://127.0.0.1:8904/api/codex-tools}") String toolBaseUrl,
            @Value("${app.codex.tool-token:}") String toolToken,
            @Value("${app.codex.mcp-server-name:interview_practice}") String mcpServerName,
            @Value("${app.codex.mcp-url:http://127.0.0.1:8904/mcp}") String mcpUrl,
            @Value("${app.codex.proxy-url:}") String proxyUrl,
            @Value("${app.codex.no-proxy:127.0.0.1,localhost,::1}") String noProxy,
            @Value("${app.codex.work-root:runtime/codex-jobs}") String workRoot,
            @Value("${app.codex.timeout-minutes:30}") long processTimeoutMinutes
    ) {
        this.importJobRepository = importJobRepository;
        this.importJobLogRepository = importJobLogRepository;
        this.documentRepository = documentRepository;
        this.transactionTemplate = transactionTemplate;
        this.questionGenerationPrompt = questionGenerationPrompt;
        this.codexCommand = codexCommand;
        this.sandboxMode = sandboxMode;
        this.toolBaseUrl = toolBaseUrl;
        this.toolToken = toolToken == null ? "" : toolToken;
        this.mcpServerName = normalizeMcpServerName(mcpServerName);
        this.mcpUrl = mcpUrl == null ? "" : mcpUrl.trim();
        this.proxyUrl = proxyUrl == null ? "" : proxyUrl;
        this.noProxy = noProxy == null ? "" : noProxy;
        this.workRoot = Path.of(workRoot);
        this.processTimeout = Duration.ofMinutes(Math.max(1, processTimeoutMinutes));
    }

    public void recordAutoStartQueued(Long importJobId) {
        transactionTemplate.executeWithoutResult(status ->
                importJobRepository.findById(importJobId)
                        .ifPresent(job -> importJobLogRepository.save(ImportJobLog.create(
                                importJobId,
                                ImportJobLogLevel.INFO,
                                "已创建导入任务，Codex CLI 自动启动当前未启用",
                                null
                        ))));
    }

    public void runCodex(Long importJobId) {
        try {
            ImportJob job = markRunning(importJobId);
            int exitCode = runCodexProcess(job);
            if (exitCode == 0) {
                markFailedIfStillRunning(importJobId, "Codex 进程已退出，但未通过工具标记任务结果");
            } else {
                markFailedIfStillRunning(importJobId, "Codex 进程异常退出，退出码：" + exitCode);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markFailedIfStillRunning(importJobId, "Codex 任务被中断");
        } catch (Exception exception) {
            markFailedIfStillRunning(importJobId, exception.getMessage() == null ? "Codex 启动失败" : exception.getMessage());
        }
    }

    public ImportJob markRunning(Long importJobId) {
        return transactionTemplate.execute(status -> {
            ImportJob job = importJobRepository.findById(importJobId)
                    .orElseThrow(() -> AppException.notFound("导入任务不存在"));
            KnowledgeDocument document = documentRepository.findById(job.getDocumentId())
                    .orElseThrow(() -> AppException.notFound("知识文档不存在"));

            job.markRunning();
            document.markProcessing();
            importJobLogRepository.save(ImportJobLog.create(importJobId, ImportJobLogLevel.INFO, "Codex 任务开始执行", null));
            return job;
        });
    }

    public void markSucceededIfStillRunning(Long importJobId) {
        transactionTemplate.executeWithoutResult(status -> {
            ImportJob job = importJobRepository.findById(importJobId)
                    .orElseThrow(() -> AppException.notFound("导入任务不存在"));
            KnowledgeDocument document = documentRepository.findById(job.getDocumentId())
                    .orElseThrow(() -> AppException.notFound("知识文档不存在"));

            if (job.getStatus() == ImportJobStatus.RUNNING || job.getStatus() == ImportJobStatus.PENDING) {
                job.markSucceeded(job.getGeneratedQuestionCount());
                document.markProcessed();
                importJobLogRepository.save(ImportJobLog.create(importJobId, ImportJobLogLevel.INFO, "Codex 任务执行完成", null));
            }
        });
    }

    public void markFailed(Long importJobId, String reason) {
        transactionTemplate.executeWithoutResult(status ->
                importJobRepository.findById(importJobId).ifPresent(job -> {
                    job.markFailed(reason);
                    documentRepository.findById(job.getDocumentId()).ifPresent(KnowledgeDocument::markFailed);
                    importJobLogRepository.save(ImportJobLog.create(importJobId, ImportJobLogLevel.ERROR, reason, null));
                }));
    }

    public void markFailedIfStillRunning(Long importJobId, String reason) {
        transactionTemplate.executeWithoutResult(status ->
                importJobRepository.findById(importJobId).ifPresent(job -> {
                    if (job.getStatus() == ImportJobStatus.RUNNING || job.getStatus() == ImportJobStatus.PENDING) {
                        job.markFailed(reason);
                        documentRepository.findById(job.getDocumentId()).ifPresent(KnowledgeDocument::markFailed);
                        importJobLogRepository.save(ImportJobLog.create(importJobId, ImportJobLogLevel.ERROR, reason, null));
                    }
                }));
    }

    private int runCodexProcess(ImportJob job) throws IOException, InterruptedException {
        Path workDirectory = workRoot.resolve(job.getId().toString()).toAbsolutePath().normalize();
        Files.createDirectories(workDirectory);
        recordProcessLine(job.getId(), "启动 Codex CLI，工作目录：" + workDirectory);

        ProcessBuilder processBuilder = new ProcessBuilder(buildCodexCommand(job.getId()))
                .directory(workDirectory.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("CODEX_TOOL_BASE_URL", toolBaseUrl);
        if (!toolToken.isBlank()) {
            processBuilder.environment().put("CODEX_TOOL_TOKEN", toolToken);
        }
        injectProxyEnvironment(processBuilder);

        Process process = processBuilder.start();
        // Codex exec treats an open piped stdin as additional prompt input and waits for EOF.
        process.getOutputStream().close();

        AtomicReference<IOException> readerException = new AtomicReference<>();
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    recordProcessLine(job.getId(), line);
                }
            } catch (IOException exception) {
                readerException.set(exception);
            }
        }, "codex-import-job-" + job.getId() + "-output");
        outputReader.setDaemon(true);
        outputReader.start();

        boolean finished = process.waitFor(processTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            recordProcessLine(job.getId(), "Codex 任务超过 " + processTimeout.toMinutes() + " 分钟未完成，已终止进程");
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        }

        outputReader.join();
        if (readerException.get() != null) {
            throw readerException.get();
        }
        return finished ? process.exitValue() : 124;
    }

    private List<String> buildCodexCommand(Long importJobId) {
        List<String> command = new ArrayList<>();
        command.add(codexCommand);
        command.add("exec");
        command.add("--json");
        command.add("--sandbox");
        command.add(sandboxMode);
        command.add("-c");
        command.add("approval_policy=\"never\"");
        command.add("-c");
        command.add("sandbox_workspace_write.network_access=true");
        if (!mcpServerName.isBlank() && !mcpUrl.isBlank()) {
            command.add("-c");
            command.add("mcp_servers." + mcpServerName + ".url=" + tomlString(mcpUrl));
            command.add("-c");
            command.add("mcp_servers." + mcpServerName + ".enabled=true");
            command.add("-c");
            command.add("mcp_servers." + mcpServerName + ".required=true");
            command.add("-c");
            command.add("mcp_servers." + mcpServerName + ".tool_timeout_sec=120");
            command.add("-c");
            command.add("mcp_servers." + mcpServerName + ".default_tools_approval_mode=\"approve\"");
        }
        command.add(questionGenerationPrompt.render(importJobId, mcpServerName));
        return command;
    }

    private String normalizeMcpServerName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            return "";
        }
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String tomlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void injectProxyEnvironment(ProcessBuilder processBuilder) {
        if (proxyUrl.isBlank()) {
            return;
        }

        var environment = processBuilder.environment();
        environment.put("HTTP_PROXY", proxyUrl);
        environment.put("HTTPS_PROXY", proxyUrl);
        environment.put("ALL_PROXY", proxyUrl);
        environment.put("http_proxy", proxyUrl);
        environment.put("https_proxy", proxyUrl);
        environment.put("all_proxy", proxyUrl);
        if (!noProxy.isBlank()) {
            environment.put("NO_PROXY", noProxy);
            environment.put("no_proxy", noProxy);
        }
    }

    public void recordProcessLine(Long importJobId, String line) {
        transactionTemplate.executeWithoutResult(status -> {
            String message = line.length() > 1000 ? line.substring(0, 1000) : line;
            importJobLogRepository.save(ImportJobLog.create(importJobId, ImportJobLogLevel.INFO, message, null));
        });
    }
}
