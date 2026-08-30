package com.CemHarput.IncidentInvestigator.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "agent_execution_steps",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_agent_execution_step_event",
                    columnNames = {"execution_id", "event_id"}
            ),
            @UniqueConstraint(
                    name = "uk_agent_execution_step_number",
                    columnNames = {"execution_id", "step_number"}
            )
        }
)
public class AgentExecutionStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false)
    private AgentExecutionStepType stepType;

    @Column
    private String capability;

    @Column(name = "input_summary", columnDefinition = "TEXT")
    private String inputSummary;

    @Column(name = "observation_summary", columnDefinition = "TEXT")
    private String observationSummary;

    @Column(name = "reasoning_summary", columnDefinition = "TEXT")
    private String reasoningSummary;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentExecutionStepStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    protected AgentExecutionStep() {
    }

    public static AgentExecutionStep completed(
            UUID eventId,
            Long executionId,
            int stepNumber,
            AgentExecutionStepType stepType,
            String capability,
            String inputSummary,
            String observationSummary,
            String reasoningSummary,
            LocalDateTime occurredAt
    ) {
        if (stepNumber <= 0) {
            throw new IllegalArgumentException("Step number must be greater than zero");
        }
        AgentExecutionStep step = new AgentExecutionStep();
        step.eventId = Objects.requireNonNull(eventId, "Step eventId is required");
        step.executionId = Objects.requireNonNull(executionId, "Execution id is required");
        step.stepNumber = stepNumber;
        step.stepType = Objects.requireNonNull(stepType, "Step type is required");
        step.capability = capability;
        step.inputSummary = inputSummary;
        step.observationSummary = observationSummary;
        step.reasoningSummary = reasoningSummary;
        step.startedAt = Objects.requireNonNull(occurredAt, "Step occurrence time is required");
        step.completedAt = occurredAt;
        step.durationMs = 0L;
        step.status = AgentExecutionStepStatus.COMPLETED;
        return step;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Integer getStepNumber() {
        return stepNumber;
    }

    public AgentExecutionStepType getStepType() {
        return stepType;
    }

    public String getCapability() {
        return capability;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getObservationSummary() {
        return observationSummary;
    }

    public String getReasoningSummary() {
        return reasoningSummary;
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

    public AgentExecutionStepStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
