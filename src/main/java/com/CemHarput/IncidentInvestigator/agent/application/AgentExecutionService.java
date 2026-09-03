package com.CemHarput.IncidentInvestigator.agent.application;

import com.CemHarput.IncidentInvestigator.agent.api.AgentExecutionAcceptedResponse;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionMessagingException;
import com.CemHarput.IncidentInvestigator.agent.messaging.AgentExecutionEventPublisher;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionRequestedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentExecutionService.class);

    private final AgentExecutionPersistenceService persistenceService;
    private final AgentExecutionEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public AgentExecutionService(
            AgentExecutionPersistenceService persistenceService,
            AgentExecutionEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            Tracer tracer
    ) {
        this.persistenceService = persistenceService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    public AgentExecutionAcceptedResponse createExecution(String agentName, Long incidentId) {
        Span span = tracer.nextSpan().name("agent.execution.create").start();
        if (agentName != null) {
            span.tag("agent.name", agentName);
        }
        if (incidentId != null) {
            span.tag("incident.id", incidentId.toString());
        }
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            AgentExecutionAcceptedResponse response = createExecutionInternal(
                    agentName,
                    incidentId
            );
            span.tag("agent.execution.id", response.executionId().toString());
            span.tag("agent.execution.status", response.status());
            return response;
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private AgentExecutionAcceptedResponse createExecutionInternal(
            String agentName,
            Long incidentId
    ) {
        AgentExecutionPreparation preparation = persistenceService.prepare(
                agentName,
                incidentId,
                UUID.randomUUID()
        );
        AgentExecutionRequestedEvent event = preparation.event();

        try {
            eventPublisher.publishRequested(event);
        } catch (RuntimeException ex) {
            AgentExecutionMessagingException failure = ex instanceof AgentExecutionMessagingException messaging
                    ? messaging
                    : new AgentExecutionMessagingException(
                            "Failed to publish agent execution request",
                            ex
                    );
            persistFailurePreservingOriginal(event.executionId(), failure);
            throw failure;
        }
        recordRequestedMetricBestEffort(event.agentName());

        return new AgentExecutionAcceptedResponse(
                event.executionId(),
                event.agentName(),
                "QUEUED"
        );
    }

    private void recordRequestedMetricBestEffort(String agentName) {
        try {
            meterRegistry.counter(
                    "agent.execution.requested",
                    "agent_name",
                    agentName,
                    "status",
                    "QUEUED"
            ).increment();
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to record requested agent execution metric", ex);
        }
    }

    private void persistFailurePreservingOriginal(
            Long executionId,
            AgentExecutionMessagingException original
    ) {
        try {
            persistenceService.persistMessagingFailure(executionId, original.getMessage());
        } catch (RuntimeException persistenceFailure) {
            if (persistenceFailure != original) {
                original.addSuppressed(persistenceFailure);
            }
        }
    }
}
