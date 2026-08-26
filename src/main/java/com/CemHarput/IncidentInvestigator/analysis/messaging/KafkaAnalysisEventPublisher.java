package com.CemHarput.IncidentInvestigator.analysis.messaging;

import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisMessagingException;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaAnalysisEventPublisher implements AnalysisEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String requestedTopic;

    public KafkaAnalysisEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${analysis.kafka.requested-topic}") String requestedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.requestedTopic = requestedTopic;
    }

    @Override
    public void publishAnalysisRequested(AnalysisRequestedEvent event) {
        try {
            kafkaTemplate.send(
                    requestedTopic,
                    event.incidentId().toString(),
                    event
            ).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AnalysisMessagingException(
                    "Analysis request publishing was interrupted",
                    ex
            );
        } catch (ExecutionException | RuntimeException ex) {
            throw new AnalysisMessagingException(
                    "Failed to publish analysis request",
                    ex
            );
        }
    }
}
