package com.CemHarput.IncidentInvestigator.agent.application;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecution;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionFailureType;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStatus;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStep;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStepType;
import com.CemHarput.IncidentInvestigator.agent.domain.ProcessedAgentEvent;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionRepository;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionStepRepository;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.ProcessedAgentEventRepository;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionCompletedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionFailedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionStepEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentResult;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCauseDecisionPolicy;
import com.CemHarput.IncidentInvestigator.incident.exception.IncidentNotFoundException;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentExecutionEventService.class);

    private final AgentExecutionRepository executionRepository;
    private final AgentExecutionStepRepository stepRepository;
    private final ProcessedAgentEventRepository processedEventRepository;
    private final IncidentRepository incidentRepository;
    private final RootCauseDecisionPolicy rootCauseDecisionPolicy;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public AgentExecutionEventService(
            AgentExecutionRepository executionRepository,
            AgentExecutionStepRepository stepRepository,
            ProcessedAgentEventRepository processedEventRepository,
            IncidentRepository incidentRepository,
            RootCauseDecisionPolicy rootCauseDecisionPolicy,
            MeterRegistry meterRegistry,
            Tracer tracer
    ) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.processedEventRepository = processedEventRepository;
        this.incidentRepository = incidentRepository;
        this.rootCauseDecisionPolicy = rootCauseDecisionPolicy;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Transactional
    public void processStep(AgentExecutionStepEvent event) {
        validateStepEvent(event);
        AgentExecution execution = findExecutionForUpdate(event.executionId());
        if (isDuplicate(event.eventId(), execution.getId())) {
            return;
        }
        requireActive(execution);
        startIfQueued(execution);

        int expectedStep = execution.getCurrentStep() + 1;
        if (event.stepNumber() != expectedStep) {
            throw new IllegalStateException(
                    "Expected agent execution step " + expectedStep
                            + " but received " + event.stepNumber()
            );
        }
        if (stepRepository.existsByExecutionIdAndStepNumber(
                execution.getId(),
                event.stepNumber()
        )) {
            throw new IllegalStateException(
                    "Agent execution step already exists with a different event id: "
                            + event.stepNumber()
            );
        }

        AgentExecutionStepType stepType = parseStepType(event.stepType());
        AgentExecutionStep step = AgentExecutionStep.completed(
                event.eventId(),
                execution.getId(),
                event.stepNumber(),
                stepType,
                event.capability(),
                null,
                event.observationSummary(),
                event.reasoningSummary(),
                LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC)
        );
        stepRepository.save(step);
        execution.advanceStep();
        markProcessed(event.eventId(), execution.getId(), "STEP");
        recordStepMetricsBestEffort(execution, stepType);
    }

    @Transactional
    public void processCompleted(AgentExecutionCompletedEvent event) {
        Span span = persistResultSpan(
                event == null ? null : event.executionId(),
                "COMPLETED"
        );
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            processCompletedInternal(event, span);
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private void processCompletedInternal(AgentExecutionCompletedEvent event, Span span) {
        validateCompletedEvent(event);
        AgentExecution execution = findExecutionForUpdate(event.executionId());
        tagExecution(span, execution);
        if (isDuplicate(event.eventId(), execution.getId())) {
            return;
        }
        requireMatchingAgent(execution, event.agentName());
        requireActive(execution);
        startIfQueued(execution);
        requireCompletedStepCount(execution, event.totalSteps());

        AgentResult result = event.result();
        boolean inconclusive = rootCauseDecisionPolicy.isInconclusive(
                result.rootCause(),
                result.confidence()
        );
        if (!inconclusive) {
            Long incidentId = execution.getIncidentId();
            if (incidentId == null) {
                throw new IllegalStateException(
                        "Incident root cause result requires an incident execution"
                );
            }
            Incident incident = incidentRepository.findByIdForAnalysis(incidentId)
                    .orElseThrow(() -> new IncidentNotFoundException(incidentId));
            incident.identifyRootCause(new RootCause(
                    result.explanation(),
                    result.rootCause(),
                    false
            ));
        }

        execution.complete(result.explanation(), event.eventId());
        markProcessed(event.eventId(), execution.getId(), "COMPLETED");
        String outcome = inconclusive ? "INCONCLUSIVE" : "COMPLETED";
        span.tag("agent.execution.status", execution.getStatus().name());
        span.tag("agent.result.outcome", outcome);
        recordCompletedMetricsBestEffort(execution, outcome);
    }

    @Transactional
    public void processFailed(AgentExecutionFailedEvent event) {
        Span span = persistResultSpan(
                event == null ? null : event.executionId(),
                "FAILED"
        );
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            processFailedInternal(event, span);
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private void processFailedInternal(AgentExecutionFailedEvent event, Span span) {
        validateFailedEvent(event);
        AgentExecution execution = findExecutionForUpdate(event.executionId());
        tagExecution(span, execution);
        if (isDuplicate(event.eventId(), execution.getId())) {
            return;
        }
        requireMatchingAgent(execution, event.agentName());
        requireActive(execution);
        startIfQueued(execution);
        requireCompletedStepCount(execution, event.completedSteps());

        AgentExecutionFailureType failureType = parseFailureType(event.failureType());
        switch (failureType) {
            case TIMEOUT -> execution.timeout(event.failureReason(), event.eventId());
            case STEP_LIMIT_EXCEEDED -> execution.markStepLimitExceeded(event.eventId());
            default -> execution.fail(failureType, event.failureReason(), event.eventId());
        }
        markProcessed(event.eventId(), execution.getId(), "FAILED");
        span.tag("agent.execution.status", execution.getStatus().name());
        span.tag("agent.failure.type", failureType.name());
        recordFailedMetricsBestEffort(execution, failureType);
    }

    private Span persistResultSpan(Long executionId, String eventType) {
        Span span = tracer.nextSpan().name("agent.execution.persist-result").start();
        span.tag("agent.event.type", eventType);
        if (executionId != null) {
            span.tag("agent.execution.id", executionId.toString());
        }
        return span;
    }

    private void tagExecution(Span span, AgentExecution execution) {
        span.tag("agent.name", execution.getAgentName());
        if (execution.getIncidentId() != null) {
            span.tag("incident.id", execution.getIncidentId().toString());
        }
    }

    private void recordCompletedMetricsBestEffort(
            AgentExecution execution,
            String outcome
    ) {
        try {
            meterRegistry.counter(
                    "agent.execution.completed",
                    "agent_name",
                    execution.getAgentName(),
                    "status",
                    execution.getStatus().name(),
                    "outcome",
                    outcome
            ).increment();
            recordDuration(execution);
        } catch (RuntimeException ex) {
            logMetricsFailure(execution, ex);
        }
    }

    private void recordFailedMetricsBestEffort(
            AgentExecution execution,
            AgentExecutionFailureType failureType
    ) {
        try {
            meterRegistry.counter(
                    "agent.execution.failed",
                    "agent_name",
                    execution.getAgentName(),
                    "status",
                    execution.getStatus().name(),
                    "failure_type",
                    failureType.name()
            ).increment();
            recordDuration(execution);
        } catch (RuntimeException ex) {
            logMetricsFailure(execution, ex);
        }
    }

    private void recordDuration(AgentExecution execution) {
        if (execution.getDurationMs() != null) {
            meterRegistry.timer(
                    "agent.execution.duration",
                    "agent_name",
                    execution.getAgentName(),
                    "status",
                    execution.getStatus().name()
            ).record(Duration.ofMillis(execution.getDurationMs()));
        }
    }

    private void recordStepMetricsBestEffort(
            AgentExecution execution,
            AgentExecutionStepType stepType
    ) {
        try {
            meterRegistry.counter(
                    "agent.execution.steps",
                    "agent_name",
                    execution.getAgentName(),
                    "status",
                    execution.getStatus().name(),
                    "step_type",
                    stepType.name()
            ).increment();
        } catch (RuntimeException ex) {
            logMetricsFailure(execution, ex);
        }
    }

    private void logMetricsFailure(AgentExecution execution, RuntimeException error) {
        LOGGER.warn(
                "Failed to record agent execution metrics executionId={} status={}",
                execution.getId(),
                execution.getStatus(),
                error
        );
    }

    private AgentExecution findExecutionForUpdate(Long executionId) {
        return executionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Agent execution not found: " + executionId
                ));
    }

    private boolean isDuplicate(UUID eventId, Long executionId) {
        return processedEventRepository.findById(eventId)
                .map(processed -> {
                    if (!processed.getExecutionId().equals(executionId)) {
                        throw new IllegalArgumentException(
                                "Agent event id was already used by a different execution"
                        );
                    }
                    return true;
                })
                .orElse(false);
    }

    private void markProcessed(UUID eventId, Long executionId, String eventType) {
        processedEventRepository.save(new ProcessedAgentEvent(eventId, executionId, eventType));
    }

    private void startIfQueued(AgentExecution execution) {
        if (execution.getStatus() == AgentExecutionStatus.QUEUED) {
            execution.start();
        }
    }

    private void requireActive(AgentExecution execution) {
        if (execution.isTerminal()) {
            throw new IllegalStateException(
                    "Agent execution already has a terminal result: " + execution.getId()
            );
        }
    }

    private void requireMatchingAgent(AgentExecution execution, String agentName) {
        if (!execution.getAgentName().equals(agentName)) {
            throw new IllegalArgumentException(
                    "Result event agentName does not match agent execution"
            );
        }
    }

    private void requireCompletedStepCount(AgentExecution execution, int completedSteps) {
        if (completedSteps != execution.getCurrentStep()) {
            throw new IllegalStateException(
                    "Result event step count does not match persisted audit history"
            );
        }
    }

    private AgentExecutionStepType parseStepType(String stepType) {
        try {
            return AgentExecutionStepType.valueOf(stepType.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unsupported agent stepType: " + stepType, ex);
        }
    }

    private AgentExecutionFailureType parseFailureType(String failureType) {
        try {
            return AgentExecutionFailureType.valueOf(
                    failureType.trim().toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Unsupported agent failureType: " + failureType,
                    ex
            );
        }
    }

    private void validateStepEvent(AgentExecutionStepEvent event) {
        if (event == null
                || event.eventId() == null
                || event.executionId() == null
                || event.stepNumber() <= 0
                || event.stepType() == null
                || event.stepType().isBlank()
                || event.occurredAt() == null) {
            throw new IllegalArgumentException("Invalid agent execution step event");
        }
    }

    private void validateCompletedEvent(AgentExecutionCompletedEvent event) {
        if (event == null
                || event.eventId() == null
                || event.executionId() == null
                || event.agentName() == null
                || event.agentName().isBlank()
                || event.result() == null
                || event.totalSteps() < 0
                || event.completedAt() == null) {
            throw new IllegalArgumentException("Invalid agent execution completed event");
        }
        validateResult(event.result());
    }

    private void validateResult(AgentResult result) {
        if (result.rootCause() == null
                || result.rootCause().isBlank()
                || result.explanation() == null
                || result.explanation().isBlank()
                || result.confidence() < 0.0d
                || result.confidence() > 1.0d) {
            throw new IllegalArgumentException("Invalid agent result");
        }
    }

    private void validateFailedEvent(AgentExecutionFailedEvent event) {
        if (event == null
                || event.eventId() == null
                || event.executionId() == null
                || event.agentName() == null
                || event.agentName().isBlank()
                || event.failureType() == null
                || event.failureType().isBlank()
                || event.failureReason() == null
                || event.failureReason().isBlank()
                || event.completedSteps() < 0
                || event.failedAt() == null) {
            throw new IllegalArgumentException("Invalid agent execution failed event");
        }
    }
}
