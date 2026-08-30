package com.CemHarput.IncidentInvestigator.agent.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionMessagingException;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionRequestedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentInput;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentLimitsContract;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaAgentExecutionEventPublisherTest {

    private static final String TOPIC = "agent.execution.requested.v1";

    @Test
    void shouldPublishUsingExecutionIdAsKeyAndConfiguredTimeout() throws Exception {
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        AgentExecutionRequestedEvent event = event();
        CompletableFuture<SendResult<String, Object>> send = mock();
        when(kafkaTemplate.send(TOPIC, "99", event)).thenReturn(send);
        when(send.get(250L, TimeUnit.MILLISECONDS)).thenReturn(mock(SendResult.class));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SimpleTracer tracer = new SimpleTracer();
        KafkaAgentExecutionEventPublisher publisher = new KafkaAgentExecutionEventPublisher(
                kafkaTemplate,
                TOPIC,
                Duration.ofMillis(250),
                meterRegistry,
                tracer
        );

        publisher.publishRequested(event);

        verify(kafkaTemplate).send(TOPIC, "99", event);
        verify(send).get(250L, TimeUnit.MILLISECONDS);
        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("kafka.publish.agent-execution-request");
        assertThat(span.getTags())
                .containsEntry("messaging.destination", TOPIC)
                .containsEntry("agent.execution.id", "99")
                .containsEntry("agent.name", "incident-root-cause-agent");
    }

    @Test
    void shouldClassifyFailedSendAsMessagingFailure() {
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        AgentExecutionRequestedEvent event = event();
        CompletableFuture<SendResult<String, Object>> send = new CompletableFuture<>();
        send.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(TOPIC, "99", event)).thenReturn(send);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        KafkaAgentExecutionEventPublisher publisher = new KafkaAgentExecutionEventPublisher(
                kafkaTemplate,
                TOPIC,
                Duration.ofSeconds(5),
                meterRegistry,
                new SimpleTracer()
        );

        assertThatThrownBy(() -> publisher.publishRequested(event))
                .isInstanceOf(AgentExecutionMessagingException.class)
                .hasMessage("Failed to publish agent execution request");
        assertThat(meterRegistry.counter("agent.execution.kafka.publish.failures").count())
                .isEqualTo(1.0d);
    }

    private AgentExecutionRequestedEvent event() {
        return new AgentExecutionRequestedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                "1.0",
                new AgentLimitsContract(10, 60),
                new AgentInput(42L, "Latency", "LATENCY", List.of()),
                Instant.now()
        );
    }
}
