package xyz.kangnasi.interview.document;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentParseTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;
    private final boolean rabbitmqEnabled;

    public DocumentParseTaskPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.document-parse.queue-name}") String queueName,
            @Value("${app.document-parse.rabbitmq-enabled:true}") boolean rabbitmqEnabled
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
        this.rabbitmqEnabled = rabbitmqEnabled;
    }

    public void publish(DocumentParseMessage message) {
        if (!rabbitmqEnabled) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(queueName, message);
                }
            });
            return;
        }

        rabbitTemplate.convertAndSend(queueName, message);
    }
}
