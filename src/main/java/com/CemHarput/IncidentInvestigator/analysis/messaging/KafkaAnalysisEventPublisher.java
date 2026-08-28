package com.CemHarput.IncidentInvestigator.analysis.messaging;

import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisMessagingException;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaAnalysisEventPublisher implements AnalysisEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaAnalysisEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String requestedTopic;
    private final Duration publishTimeout;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public KafkaAnalysisEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${analysis.kafka.requested-topic}") String requestedTopic,
            @Value("${analysis.kafka.publish-timeout:5s}") Duration publishTimeout,
            MeterRegistry meterRegistry,
            Tracer tracer
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.requestedTopic = requestedTopic;
        this.publishTimeout = publishTimeout;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    public void publishAnalysisRequested(AnalysisRequestedEvent event) {
        Span span = tracer.nextSpan().name("kafka.publish.analysis-request").start();
        span.tag("messaging.system", "kafka");
        span.tag("messaging.destination", requestedTopic);
        span.tag("analysis.execution.id", event.executionId().toString());
        span.tag("incident.id", event.incidentId().toString());

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            kafkaTemplate.send(
                    requestedTopic,
                    event.incidentId().toString(),
                    event
            ).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            span.error(ex);
            recordPublishFailure();
            throw new AnalysisMessagingException(
                    "Analysis request publishing was interrupted",
                    ex
            );
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            span.error(ex);
            recordPublishFailure();
            throw new AnalysisMessagingException(
                    "Failed to publish analysis request",
                    ex
            );
        } finally {
            span.end();
        }
    }

    private void recordPublishFailure() {
        try {
            meterRegistry.counter("incident.analysis.kafka.publish.failures").increment();
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to increment Kafka publish failure metric", ex);
        }
    }
}
