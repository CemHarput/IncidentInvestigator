package com.CemHarput.IncidentInvestigator.analysis.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisMessagingException;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaAnalysisEventPublisherTest {

    private static final String TOPIC = "incident.analysis.requested.v1";

    @Test
    void shouldPublishUsingIncidentIdAsMessageKey() {
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        AnalysisRequestedEvent event = event();
        SendResult<String, Object> sendResult = mock();
        when(kafkaTemplate.send(TOPIC, "42", event))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        KafkaAnalysisEventPublisher publisher = new KafkaAnalysisEventPublisher(
                kafkaTemplate,
                TOPIC
        );

        publisher.publishAnalysisRequested(event);

        verify(kafkaTemplate).send(TOPIC, "42", event);
    }

    @Test
    void shouldExposeFailedSendAsMessagingFailure() {
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        AnalysisRequestedEvent event = event();
        CompletableFuture<SendResult<String, Object>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(TOPIC, "42", event)).thenReturn(failedSend);
        KafkaAnalysisEventPublisher publisher = new KafkaAnalysisEventPublisher(
                kafkaTemplate,
                TOPIC
        );

        assertThatThrownBy(() -> publisher.publishAnalysisRequested(event))
                .isInstanceOf(AnalysisMessagingException.class)
                .hasMessage("Failed to publish analysis request");
    }

    private AnalysisRequestedEvent event() {
        return new AnalysisRequestedEvent(
                UUID.randomUUID(),
                99L,
                42L,
                "Payment service latency",
                "LATENCY",
                List.of(),
                LocalDateTime.now()
        );
    }
}
