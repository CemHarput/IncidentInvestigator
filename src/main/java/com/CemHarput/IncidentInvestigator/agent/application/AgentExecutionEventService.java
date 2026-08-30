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
import com.CemHarput.IncidentInvestigator.incident.exception.IncidentNotFoundException;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionEventService {

    private final AgentExecutionRepository executionRepository;
    private final AgentExecutionStepRepository stepRepository;
    private final ProcessedAgentEventRepository processedEventRepository;
    private final IncidentRepository incidentRepository;

    public AgentExecutionEventService(
            AgentExecutionRepository executionRepository,
            AgentExecutionStepRepository stepRepository,
            ProcessedAgentEventRepository processedEventRepository,
            IncidentRepository incidentRepository
    ) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.processedEventRepository = processedEventRepository;
        this.incidentRepository = incidentRepository;
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

        AgentExecutionStep step = AgentExecutionStep.completed(
                event.eventId(),
                execution.getId(),
                event.stepNumber(),
                parseStepType(event.stepType()),
                event.capability(),
                null,
                event.observationSummary(),
                event.reasoningSummary(),
                LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC)
        );
        stepRepository.save(step);
        execution.advanceStep();
        markProcessed(event.eventId(), execution.getId(), "STEP");
    }

    @Transactional
    public void processCompleted(AgentExecutionCompletedEvent event) {
        validateCompletedEvent(event);
        AgentExecution execution = findExecutionForUpdate(event.executionId());
        if (isDuplicate(event.eventId(), execution.getId())) {
            return;
        }
        requireMatchingAgent(execution, event.agentName());
        requireActive(execution);
        startIfQueued(execution);
        requireCompletedStepCount(execution, event.totalSteps());

        AgentResult result = event.result();
        if (!"UNKNOWN".equalsIgnoreCase(result.rootCause())) {
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
    }

    @Transactional
    public void processFailed(AgentExecutionFailedEvent event) {
        validateFailedEvent(event);
        AgentExecution execution = findExecutionForUpdate(event.executionId());
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
