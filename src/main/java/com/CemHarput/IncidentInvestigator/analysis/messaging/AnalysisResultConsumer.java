package com.CemHarput.IncidentInvestigator.analysis.messaging;

import com.CemHarput.IncidentInvestigator.analysis.application.AsyncAnalysisResultService;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisCompletedEvent;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisFailedEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AnalysisResultConsumer {

    private final ObjectMapper objectMapper;
    private final AsyncAnalysisResultService resultService;
    private final Tracer tracer;
    private final String completedTopic;
    private final String failedTopic;

    public AnalysisResultConsumer(
            ObjectMapper objectMapper,
            AsyncAnalysisResultService resultService,
            Tracer tracer,
            @Value("${analysis.kafka.completed-topic}") String completedTopic,
            @Value("${analysis.kafka.failed-topic}") String failedTopic
    ) {
        this.objectMapper = objectMapper;
        this.resultService = resultService;
        this.tracer = tracer;
        this.completedTopic = completedTopic;
        this.failedTopic = failedTopic;
    }

    @KafkaListener(
            id = "analysis-completed-result-consumer",
            topics = "${analysis.kafka.completed-topic}",
            groupId = "${analysis.kafka.result-consumer-group}"
    )
    public void consumeCompleted(String payload) {
        AnalysisCompletedEvent event = readCompletedEvent(payload);
        consumeCompleted(event);
    }

    @KafkaListener(
            id = "analysis-failed-result-consumer",
            topics = "${analysis.kafka.failed-topic}",
            groupId = "${analysis.kafka.result-consumer-group}"
    )
    public void consumeFailed(String payload) {
        AnalysisFailedEvent event = readFailedEvent(payload);
        consumeFailed(event);
    }

    private void consumeCompleted(AnalysisCompletedEvent event) {
        Span span = resultSpan(
                "spring.consume.analysis-completed",
                completedTopic,
                event.executionId(),
                event.incidentId()
        );
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            resultService.processCompleted(event);
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private void consumeFailed(AnalysisFailedEvent event) {
        Span span = resultSpan(
                "spring.consume.analysis-failed",
                failedTopic,
                event.executionId(),
                event.incidentId()
        );
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            resultService.processFailed(event);
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private Span resultSpan(String name, String topic, Long executionId, Long incidentId) {
        Span span = tracer.nextSpan().name(name).start();
        span.tag("messaging.system", "kafka");
        span.tag("messaging.destination", topic);
        if (executionId != null) {
            span.tag("analysis.execution.id", executionId.toString());
        }
        if (incidentId != null) {
            span.tag("incident.id", incidentId.toString());
        }
        return span;
    }

    private AnalysisCompletedEvent readCompletedEvent(String payload) {
        try {
            return objectMapper.readValue(payload, AnalysisCompletedEvent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid analysis completed event payload", ex);
        }
    }

    private AnalysisFailedEvent readFailedEvent(String payload) {
        try {
            return objectMapper.readValue(payload, AnalysisFailedEvent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid analysis failed event payload", ex);
        }
    }
}
