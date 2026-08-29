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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.time.Duration;
import java.time.Instant;
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
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SimpleTracer tracer = new SimpleTracer();
        KafkaAnalysisEventPublisher publisher = new KafkaAnalysisEventPublisher(
                kafkaTemplate,
                TOPIC,
                Duration.ofMillis(250),
                meterRegistry,
                tracer
        );

        publisher.publishAnalysisRequested(event);

        verify(kafkaTemplate).send(TOPIC, "42", event);
        verify(send).get(250L, TimeUnit.MILLISECONDS);
        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("kafka.publish.analysis-request");
        assertThat(span.getTags())
                .containsEntry("messaging.system", "kafka")
                .containsEntry("messaging.destination", TOPIC)
                .containsEntry("analysis.execution.id", "99")
                .containsEntry("incident.id", "42");
    }

    @Test
    void shouldExposeFailedSendAsMessagingFailure() {
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        AnalysisRequestedEvent event = event();
        CompletableFuture<SendResult<String, Object>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(TOPIC, "42", event)).thenReturn(failedSend);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SimpleTracer tracer = new SimpleTracer();
        KafkaAnalysisEventPublisher publisher = new KafkaAnalysisEventPublisher(
                kafkaTemplate,
                TOPIC,
                Duration.ofSeconds(5),
                meterRegistry,
                tracer
        );

        assertThatThrownBy(() -> publisher.publishAnalysisRequested(event))
                .isInstanceOf(AnalysisMessagingException.class)
                .hasMessage("Failed to publish analysis request");
        assertThat(meterRegistry.counter("incident.analysis.kafka.publish.failures").count())
                .isEqualTo(1.0d);
        assertThat(tracer.onlySpan().getError()).isNotNull();
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
                Duration.ofMillis(1),
                new SimpleMeterRegistry(),
                Tracer.NOOP
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
                Duration.ofSeconds(5),
                new SimpleMeterRegistry(),
                Tracer.NOOP
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
                Instant.now()
        );
    }
}
