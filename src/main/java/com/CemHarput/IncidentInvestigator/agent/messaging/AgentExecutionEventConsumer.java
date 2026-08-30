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
import tools.jackson.databind.ObjectMapper;

@Component
public class AgentExecutionEventConsumer {

    private final ObjectMapper objectMapper;
    private final AgentExecutionEventService eventService;
    private final Tracer tracer;
    private final String stepTopic;
    private final String completedTopic;
    private final String failedTopic;

    public AgentExecutionEventConsumer(
            ObjectMapper objectMapper,
            AgentExecutionEventService eventService,
            Tracer tracer,
            @Value("${agent.kafka.step-topic}") String stepTopic,
            @Value("${agent.kafka.completed-topic}") String completedTopic,
            @Value("${agent.kafka.failed-topic}") String failedTopic
    ) {
        this.objectMapper = objectMapper;
        this.eventService = eventService;
        this.tracer = tracer;
        this.stepTopic = stepTopic;
        this.completedTopic = completedTopic;
        this.failedTopic = failedTopic;
    }

    @KafkaListener(
            id = "agent-execution-step-consumer",
            topics = "${agent.kafka.step-topic}",
            groupId = "${agent.kafka.result-consumer-group}"
    )
    public void consumeStep(String payload) {
        AgentExecutionStepEvent event = read(
                payload,
                AgentExecutionStepEvent.class,
                "step"
        );
        consume(
                "agent-step",
                stepTopic,
                event.executionId(),
                () -> eventService.processStep(event)
        );
    }

    @KafkaListener(
            id = "agent-execution-completed-consumer",
            topics = "${agent.kafka.completed-topic}",
            groupId = "${agent.kafka.result-consumer-group}"
    )
    public void consumeCompleted(String payload) {
        AgentExecutionCompletedEvent event = read(
                payload,
                AgentExecutionCompletedEvent.class,
                "completed"
        );
        consume(
                "agent-completed",
                completedTopic,
                event.executionId(),
                () -> eventService.processCompleted(event)
        );
    }

    @KafkaListener(
            id = "agent-execution-failed-consumer",
            topics = "${agent.kafka.failed-topic}",
            groupId = "${agent.kafka.result-consumer-group}"
    )
    public void consumeFailed(String payload) {
        AgentExecutionFailedEvent event = read(
                payload,
                AgentExecutionFailedEvent.class,
                "failed"
        );
        consume(
                "agent-failed",
                failedTopic,
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
}
