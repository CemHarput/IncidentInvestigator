package com.CemHarput.IncidentInvestigator.agent.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.CemHarput.IncidentInvestigator.agent.application.AgentExecutionEventService;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionCompletedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionFailedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionStepEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentResult;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AgentExecutionEventConsumerTest {

    private static final String EVENTS_TOPIC = "agent.execution.events.v1";
    private final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void consumeStep_shouldDeserializeAndPersistAuditEvent() throws Exception {
        AgentExecutionEventService service = mock(AgentExecutionEventService.class);
        AgentExecutionStepEvent event = new AgentExecutionStepEvent(
                UUID.randomUUID(),
                99L,
                1,
                "PLAN",
                null,
                null,
                "Inspect logs first.",
                Instant.now()
        );
        SimpleTracer tracer = new SimpleTracer();
        AgentExecutionEventConsumer consumer = consumer(service, tracer);

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(service).processStep(event);
        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("agent.result.consume");
        assertThat(span.getTags())
                .containsEntry("messaging.destination", EVENTS_TOPIC)
                .containsEntry("agent.event.type", "agent-step")
                .containsEntry("agent.execution.id", "99");
    }

    @Test
    void consumeCompleted_shouldDelegateCompletedEvent() throws Exception {
        AgentExecutionEventService service = mock(AgentExecutionEventService.class);
        AgentExecutionCompletedEvent event = new AgentExecutionCompletedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                new AgentResult("UNKNOWN", 0.1d, "Insufficient evidence", List.of()),
                3,
                Instant.now()
        );
        AgentExecutionEventConsumer consumer = consumer(
                service,
                new SimpleTracer()
        );

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(service).processCompleted(event);
    }

    @Test
    void consumeFailed_shouldRecordProcessingFailureOnSpan() throws Exception {
        AgentExecutionEventService service = mock(AgentExecutionEventService.class);
        AgentExecutionFailedEvent event = new AgentExecutionFailedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                "CAPABILITY_FAILURE",
                "Log analyzer failed",
                2,
                Instant.now()
        );
        RuntimeException failure = new RuntimeException("Database unavailable");
        doThrow(failure).when(service).processFailed(event);
        SimpleTracer tracer = new SimpleTracer();
        AgentExecutionEventConsumer consumer = consumer(service, tracer);

        String payload = objectMapper.writeValueAsString(event);
        assertThatThrownBy(() -> consumer.consume(payload)).isSameAs(failure);

        assertThat(tracer.onlySpan().getError()).isSameAs(failure);
        assertThat(tracer.onlySpan().getTags())
                .containsEntry("messaging.destination", EVENTS_TOPIC);
    }

    @Test
    void consume_shouldRejectUnknownEventType() {
        AgentExecutionEventConsumer consumer = consumer(
                mock(AgentExecutionEventService.class),
                new SimpleTracer()
        );

        assertThatThrownBy(() -> consumer.consume("{\"eventType\":\"UNKNOWN\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown agent execution eventType");
    }

    private AgentExecutionEventConsumer consumer(
            AgentExecutionEventService service,
            SimpleTracer tracer
    ) {
        return new AgentExecutionEventConsumer(
                objectMapper,
                service,
                tracer,
                EVENTS_TOPIC
        );
    }
}
