package xyz.kangnasi.interview.importjob;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CodexJobLauncher {

    private final TaskExecutor taskExecutor;
    private final ImportJobExecutionService executionService;
    private final boolean launchEnabled;

    public CodexJobLauncher(
            TaskExecutor taskExecutor,
            ImportJobExecutionService executionService,
            @Value("${app.codex.launch-enabled:true}") boolean launchEnabled
    ) {
        this.taskExecutor = taskExecutor;
        this.executionService = executionService;
        this.launchEnabled = launchEnabled;
    }

    public void start(Long importJobId) {
        if (!launchEnabled) {
            executionService.recordAutoStartQueued(importJobId);
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutor.execute(() -> executionService.runCodex(importJobId));
                }
            });
            return;
        }

        taskExecutor.execute(() -> executionService.runCodex(importJobId));
    }
}
