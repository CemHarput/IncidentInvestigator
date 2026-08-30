package com.CemHarput.IncidentInvestigator.agent.messaging;

import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionMessagingException;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionRequestedEvent;
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
public class KafkaAgentExecutionEventPublisher implements AgentExecutionEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaAgentExecutionEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String requestedTopic;
    private final Duration publishTimeout;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public KafkaAgentExecutionEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${agent.kafka.requested-topic}") String requestedTopic,
            @Value("${agent.kafka.publish-timeout:5s}") Duration publishTimeout,
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
    public void publishRequested(AgentExecutionRequestedEvent event) {
        Span span = tracer.nextSpan().name("kafka.publish.agent-execution-request").start();
        span.tag("messaging.system", "kafka");
        span.tag("messaging.destination", requestedTopic);
        span.tag("agent.execution.id", event.executionId().toString());
        span.tag("agent.name", event.agentName());

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            kafkaTemplate.send(
                    requestedTopic,
                    event.executionId().toString(),
                    event
            ).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            span.error(ex);
            recordPublishFailure();
            throw new AgentExecutionMessagingException(
                    "Agent execution request publishing was interrupted",
                    ex
            );
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            span.error(ex);
            recordPublishFailure();
            throw new AgentExecutionMessagingException(
                    "Failed to publish agent execution request",
                    ex
            );
        } finally {
            span.end();
        }
    }

    private void recordPublishFailure() {
        try {
            meterRegistry.counter("agent.execution.kafka.publish.failures").increment();
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to increment agent Kafka publish failure metric", ex);
        }
    }
}
