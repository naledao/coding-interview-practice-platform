package xyz.kangnasi.interview.document;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class DocumentParseTaskListener {

    private final DocumentParseTaskService documentParseTaskService;

    public DocumentParseTaskListener(DocumentParseTaskService documentParseTaskService) {
        this.documentParseTaskService = documentParseTaskService;
    }

    @RabbitListener(
            queues = "${app.document-parse.queue-name}",
            containerFactory = "singleDocumentParseListenerContainerFactory"
    )
    public void handle(DocumentParseMessage message) {
        documentParseTaskService.parse(message.parseTaskId());
    }
}
