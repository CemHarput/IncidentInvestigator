package com.CemHarput.IncidentInvestigator.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "agent_executions")
public class AgentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    @Column(name = "agent_version", nullable = false)
    private String agentVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentExecutionStatus status;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "max_steps", nullable = false)
    private Integer maxSteps;

    @Column(name = "timeout_seconds", nullable = false)
    private Long timeoutSeconds;

    @Column(name = "current_step", nullable = false)
    private Integer currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type")
    private AgentExecutionFailureType failureType;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "request_event_id", nullable = false, unique = true)
    private UUID requestEventId;

    @Column(name = "result_event_id", unique = true)
    private UUID resultEventId;

    protected AgentExecution() {
    }

    private AgentExecution(AgentDefinition definition, Long incidentId, UUID requestEventId) {
        this.agentName = definition.name();
        this.agentVersion = definition.version();
        this.status = AgentExecutionStatus.CREATED;
        this.incidentId = incidentId;
        this.createdAt = LocalDateTime.now();
        this.maxSteps = definition.limits().maxSteps();
        this.timeoutSeconds = definition.limits().timeout().toSeconds();
        this.currentStep = 0;
        this.requestEventId = Objects.requireNonNull(requestEventId, "Request eventId is required");
    }

    public static AgentExecution create(
            AgentDefinition definition,
            Long incidentId,
            UUID requestEventId
    ) {
        return new AgentExecution(
                Objects.requireNonNull(definition, "Agent definition is required"),
                incidentId,
                requestEventId
        );
    }

    public void queue() {
        requireStatus(AgentExecutionStatus.CREATED, "Only CREATED agent executions can be queued");
        this.status = AgentExecutionStatus.QUEUED;
    }

    public void start() {
        requireStatus(AgentExecutionStatus.QUEUED, "Only QUEUED agent executions can start");
        this.status = AgentExecutionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void advanceStep() {
        requireStatus(AgentExecutionStatus.RUNNING, "Only RUNNING agent executions can advance steps");
        if (this.currentStep >= this.maxSteps) {
            throw new IllegalStateException("Agent execution has reached its step limit");
        }
        this.currentStep++;
    }

    public void complete(String resultSummary) {
        complete(resultSummary, null);
    }

    public void complete(String resultSummary, UUID resultEventId) {
        requireStatus(AgentExecutionStatus.RUNNING, "Only RUNNING agent executions can complete");
        finish(AgentExecutionStatus.COMPLETED, null, null, resultSummary);
        this.resultEventId = resultEventId;
    }

    public void fail(AgentExecutionFailureType type, String reason) {
        fail(type, reason, null);
    }

    public void fail(AgentExecutionFailureType type, String reason, UUID resultEventId) {
        requireNonTerminal();
        finish(
                AgentExecutionStatus.FAILED,
                Objects.requireNonNull(type, "Failure type is required"),
                reason,
                null
        );
        this.resultEventId = resultEventId;
    }

    public void timeout(String reason) {
        timeout(reason, null);
    }

    public void timeout(String reason, UUID resultEventId) {
        requireStatus(AgentExecutionStatus.RUNNING, "Only RUNNING agent executions can time out");
        finish(AgentExecutionStatus.TIMED_OUT, AgentExecutionFailureType.TIMEOUT, reason, null);
        this.resultEventId = resultEventId;
    }

    public void markStepLimitExceeded() {
        markStepLimitExceeded(null);
    }

    public void markStepLimitExceeded(UUID resultEventId) {
        requireStatus(
                AgentExecutionStatus.RUNNING,
                "Only RUNNING agent executions can exceed the step limit"
        );
        finish(
                AgentExecutionStatus.STEP_LIMIT_EXCEEDED,
                AgentExecutionFailureType.STEP_LIMIT_EXCEEDED,
                "Agent execution exceeded the maximum step count of " + this.maxSteps,
                null
        );
        this.resultEventId = resultEventId;
    }

    private void finish(
            AgentExecutionStatus terminalStatus,
            AgentExecutionFailureType failureType,
            String failureReason,
            String resultSummary
    ) {
        this.status = terminalStatus;
        this.failureType = failureType;
        this.failureReason = failureReason;
        this.resultSummary = resultSummary;
        this.completedAt = LocalDateTime.now();
        LocalDateTime durationStart = this.startedAt == null ? this.createdAt : this.startedAt;
        this.durationMs = ChronoUnit.MILLIS.between(durationStart, this.completedAt);
    }

    private void requireStatus(AgentExecutionStatus expected, String message) {
        if (this.status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void requireNonTerminal() {
        if (isTerminal()) {
            throw new IllegalStateException("Terminal agent executions cannot be changed");
        }
    }

    public boolean isTerminal() {
        return switch (this.status) {
            case COMPLETED, FAILED, TIMED_OUT, STEP_LIMIT_EXCEEDED -> true;
            case CREATED, QUEUED, RUNNING -> false;
        };
    }

    public Long getId() {
        return id;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public AgentExecutionStatus getStatus() {
        return status;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public Long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Integer getCurrentStep() {
        return currentStep;
    }

    public AgentExecutionFailureType getFailureType() {
        return failureType;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public UUID getRequestEventId() {
        return requestEventId;
    }

    public UUID getResultEventId() {
        return resultEventId;
    }

    public boolean hasProcessedResult(UUID eventId) {
        return eventId != null && eventId.equals(this.resultEventId);
    }
}
