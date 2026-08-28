package com.CemHarput.IncidentInvestigator.analysis.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.application.AsyncAnalysisResultService;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisCompletedEvent;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisFailedEvent;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AnalysisResultConsumerTest {

    private static final String COMPLETED_TOPIC = "incident.analysis.completed.v1";
    private static final String FAILED_TOPIC = "incident.analysis.failed.v1";

    @Test
    void consumeCompleted_shouldCreateConsumerSpanAroundPersistence() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AsyncAnalysisResultService resultService = mock(AsyncAnalysisResultService.class);
        AnalysisCompletedEvent event = new AnalysisCompletedEvent(
                UUID.randomUUID(),
                99L,
                42L,
                List.of(),
                LocalDateTime.now()
        );
        when(objectMapper.readValue("payload", AnalysisCompletedEvent.class)).thenReturn(event);
        SimpleTracer tracer = new SimpleTracer();
        AnalysisResultConsumer consumer = consumer(objectMapper, resultService, tracer);

        consumer.consumeCompleted("payload");

        verify(resultService).processCompleted(event);
        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("spring.consume.analysis-completed");
        assertThat(span.getTags())
                .containsEntry("messaging.system", "kafka")
                .containsEntry("messaging.destination", COMPLETED_TOPIC)
                .containsEntry("analysis.execution.id", "99")
                .containsEntry("incident.id", "42");
    }

    @Test
    void consumeFailed_shouldRecordPersistenceExceptionOnSpan() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AsyncAnalysisResultService resultService = mock(AsyncAnalysisResultService.class);
        AnalysisFailedEvent event = new AnalysisFailedEvent(
                UUID.randomUUID(),
                99L,
                42L,
                "INTERNAL_ERROR",
                "Analyzer failed",
                LocalDateTime.now()
        );
        when(objectMapper.readValue("payload", AnalysisFailedEvent.class)).thenReturn(event);
        RuntimeException failure = new RuntimeException("Database unavailable");
        doThrow(failure).when(resultService).processFailed(event);
        SimpleTracer tracer = new SimpleTracer();
        AnalysisResultConsumer consumer = consumer(objectMapper, resultService, tracer);

        assertThatThrownBy(() -> consumer.consumeFailed("payload")).isSameAs(failure);

        assertThat(tracer.onlySpan().getName()).isEqualTo("spring.consume.analysis-failed");
        assertThat(tracer.onlySpan().getError()).isSameAs(failure);
    }

    private AnalysisResultConsumer consumer(
            ObjectMapper objectMapper,
            AsyncAnalysisResultService resultService,
            SimpleTracer tracer
    ) {
        return new AnalysisResultConsumer(
                objectMapper,
                resultService,
                tracer,
                COMPLETED_TOPIC,
                FAILED_TOPIC
        );
    }
}
