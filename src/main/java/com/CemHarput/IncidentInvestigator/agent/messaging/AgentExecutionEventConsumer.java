package com.CemHarput.IncidentInvestigator.agent.messaging;

import com.CemHarput.IncidentInvestigator.agent.application.AgentExecutionEventService;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionCompletedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionFailedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionStepEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AgentExecutionEventConsumer {

    private final ObjectMapper objectMapper;
    private final AgentExecutionEventService eventService;
    private final Tracer tracer;
    private final String eventsTopic;

    public AgentExecutionEventConsumer(
            ObjectMapper objectMapper,
            AgentExecutionEventService eventService,
            Tracer tracer,
            @Value("${agent.kafka.events-topic}") String eventsTopic
    ) {
        this.objectMapper = objectMapper;
        this.eventService = eventService;
        this.tracer = tracer;
        this.eventsTopic = eventsTopic;
    }

    @KafkaListener(
            id = "agent-execution-event-consumer",
            topics = "${agent.kafka.events-topic}",
            groupId = "${agent.kafka.result-consumer-group}"
    )
    public void consume(String payload) {
        switch (readEventType(payload)) {
            case "STEP" -> consumeStep(payload);
            case "COMPLETED" -> consumeCompleted(payload);
            case "FAILED" -> consumeFailed(payload);
            default -> throw new IllegalArgumentException(
                    "Unknown agent execution eventType"
            );
        }
    }

    private void consumeStep(String payload) {
        AgentExecutionStepEvent event = read(
                payload,
                AgentExecutionStepEvent.class,
                "step"
        );
        consume(
                "agent-step",
                eventsTopic,
                event.executionId(),
                () -> eventService.processStep(event)
        );
    }

    private void consumeCompleted(String payload) {
        AgentExecutionCompletedEvent event = read(
                payload,
                AgentExecutionCompletedEvent.class,
                "completed"
        );
        consume(
                "agent-completed",
                eventsTopic,
                event.executionId(),
                () -> eventService.processCompleted(event)
        );
    }

    private void consumeFailed(String payload) {
        AgentExecutionFailedEvent event = read(
                payload,
                AgentExecutionFailedEvent.class,
                "failed"
        );
        consume(
                "agent-failed",
                eventsTopic,
                event.executionId(),
                () -> eventService.processFailed(event)
        );
    }

    private void consume(String eventType, String topic, Long executionId, Runnable processing) {
        Span span = tracer.nextSpan().name("agent.result.consume").start();
        span.tag("messaging.system", "kafka");
        span.tag("messaging.destination", topic);
        span.tag("agent.event.type", eventType);
        if (executionId != null) {
            span.tag("agent.execution.id", executionId.toString());
        }
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            processing.run();
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private <T> T read(String payload, Class<T> eventType, String eventName) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid agent execution " + eventName + " event payload",
                    ex
            );
        }
    }

    private String readEventType(String payload) {
        try {
            JsonNode eventType = objectMapper.readTree(payload).get("eventType");
            if (eventType == null || !eventType.isString()) {
                throw new IllegalArgumentException(
                        "Agent execution eventType must be a string"
                );
            }
            return eventType.asString();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid agent execution event payload",
                    ex
            );
        }
    }
}
