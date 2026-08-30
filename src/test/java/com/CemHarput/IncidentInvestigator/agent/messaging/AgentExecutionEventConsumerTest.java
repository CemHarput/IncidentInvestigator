package com.CemHarput.IncidentInvestigator.agent.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import tools.jackson.databind.ObjectMapper;

class AgentExecutionEventConsumerTest {

    private static final String STEP_TOPIC = "agent.execution.step.v1";
    private static final String COMPLETED_TOPIC = "agent.execution.completed.v1";
    private static final String FAILED_TOPIC = "agent.execution.failed.v1";

    @Test
    void consumeStep_shouldDeserializeAndPersistAuditEvent() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
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
        when(objectMapper.readValue("payload", AgentExecutionStepEvent.class))
                .thenReturn(event);
        SimpleTracer tracer = new SimpleTracer();
        AgentExecutionEventConsumer consumer = consumer(objectMapper, service, tracer);

        consumer.consumeStep("payload");

        verify(service).processStep(event);
        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("agent.result.consume");
        assertThat(span.getTags())
                .containsEntry("messaging.destination", STEP_TOPIC)
                .containsEntry("agent.event.type", "agent-step")
                .containsEntry("agent.execution.id", "99");
    }

    @Test
    void consumeCompleted_shouldDelegateCompletedEvent() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AgentExecutionEventService service = mock(AgentExecutionEventService.class);
        AgentExecutionCompletedEvent event = new AgentExecutionCompletedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                new AgentResult("UNKNOWN", 0.1d, "Insufficient evidence", List.of()),
                3,
                Instant.now()
        );
        when(objectMapper.readValue("payload", AgentExecutionCompletedEvent.class))
                .thenReturn(event);
        AgentExecutionEventConsumer consumer = consumer(
                objectMapper,
                service,
                new SimpleTracer()
        );

        consumer.consumeCompleted("payload");

        verify(service).processCompleted(event);
    }

    @Test
    void consumeFailed_shouldRecordProcessingFailureOnSpan() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
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
        when(objectMapper.readValue("payload", AgentExecutionFailedEvent.class))
                .thenReturn(event);
        RuntimeException failure = new RuntimeException("Database unavailable");
        doThrow(failure).when(service).processFailed(event);
        SimpleTracer tracer = new SimpleTracer();
        AgentExecutionEventConsumer consumer = consumer(objectMapper, service, tracer);

        assertThatThrownBy(() -> consumer.consumeFailed("payload")).isSameAs(failure);

        assertThat(tracer.onlySpan().getError()).isSameAs(failure);
        assertThat(tracer.onlySpan().getTags())
                .containsEntry("messaging.destination", FAILED_TOPIC);
    }

    private AgentExecutionEventConsumer consumer(
            ObjectMapper objectMapper,
            AgentExecutionEventService service,
            SimpleTracer tracer
    ) {
        return new AgentExecutionEventConsumer(
                objectMapper,
                service,
                tracer,
                STEP_TOPIC,
                COMPLETED_TOPIC,
                FAILED_TOPIC
        );
    }
}
