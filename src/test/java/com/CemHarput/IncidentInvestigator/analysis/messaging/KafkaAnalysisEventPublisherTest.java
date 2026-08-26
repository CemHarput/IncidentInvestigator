package com.CemHarput.IncidentInvestigator.analysis.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisMessagingException;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaAnalysisEventPublisherTest {

    private static final String TOPIC = "incident.analysis.requested.v1";

    @Test
    void shouldPublishUsingIncidentIdAsMessageKeyAndConfiguredTimeout() throws Exception {
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        AnalysisRequestedEvent event = event();
        SendResult<String, Object> sendResult = mock();
        CompletableFuture<SendResult<String, Object>> send = mock();
        when(kafkaTemplate.send(TOPIC, "42", event)).thenReturn(send);
        when(send.get(250L, TimeUnit.MILLISECONDS)).thenReturn(sendResult);
        KafkaAnalysisEventPublisher publisher = new KafkaAnalysisEventPublisher(
                kafkaTemplate,
                TOPIC,
                Duration.ofMillis(250)
        );

        publisher.publishAnalysisRequested(event);

        verify(kafkaTemplate).send(TOPIC, "42", event);
        verify(send).get(250L, TimeUnit.MILLISECONDS);
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
                TOPIC,
                Duration.ofSeconds(5)
        );

        assertThatThrownBy(() -> publisher.publishAnalysisRequested(event))
                .isInstanceOf(AnalysisMessagingException.class)
                .hasMessage("Failed to publish analysis request");
    }

    @Test
    void shouldExposePublishTimeoutAsMessagingFailure() throws Exception {
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        AnalysisRequestedEvent event = event();
        CompletableFuture<SendResult<String, Object>> send = mock();
        when(kafkaTemplate.send(TOPIC, "42", event)).thenReturn(send);
        when(send.get(1L, TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException());
        KafkaAnalysisEventPublisher publisher = new KafkaAnalysisEventPublisher(
                kafkaTemplate,
                TOPIC,
                Duration.ofMillis(1)
        );

        assertThatThrownBy(() -> publisher.publishAnalysisRequested(event))
                .isInstanceOf(AnalysisMessagingException.class)
                .hasMessage("Failed to publish analysis request")
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void shouldRestoreInterruptFlagWhenPublishingIsInterrupted() throws Exception {
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        AnalysisRequestedEvent event = event();
        CompletableFuture<SendResult<String, Object>> send = mock();
        when(kafkaTemplate.send(TOPIC, "42", event)).thenReturn(send);
        when(send.get(anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("interrupted"));
        KafkaAnalysisEventPublisher publisher = new KafkaAnalysisEventPublisher(
                kafkaTemplate,
                TOPIC,
                Duration.ofSeconds(5)
        );

        try {
            assertThatThrownBy(() -> publisher.publishAnalysisRequested(event))
                    .isInstanceOf(AnalysisMessagingException.class)
                    .hasMessage("Analysis request publishing was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
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
