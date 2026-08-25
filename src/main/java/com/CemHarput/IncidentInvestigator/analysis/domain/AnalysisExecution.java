package com.CemHarput.IncidentInvestigator.analysis.domain;

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

@Entity
@Table(name = "analysis_executions")
public class AnalysisExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisExecutionStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "selected_root_cause")
    private String selectedRootCause;

    @Column(name = "selected_confidence")
    private Double selectedConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type")
    private AnalysisExecutionFailureType failureType;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected AnalysisExecution() {
        this.status = AnalysisExecutionStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    public AnalysisExecution(Long incidentId) {
        this();
        this.incidentId = incidentId;
    }

    public static AnalysisExecution create(Long incidentId) {
        return new AnalysisExecution(incidentId);
    }

    public void start() {
        if (this.status != AnalysisExecutionStatus.CREATED) {
            throw new IllegalStateException("Analysis execution can only start from CREATED state");
        }
        this.status = AnalysisExecutionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.attemptCount = 1;
    }

    public void complete(String rootCause, double confidence) {
        if (this.status != AnalysisExecutionStatus.RUNNING) {
            throw new IllegalStateException("Analysis execution can only complete from RUNNING state");
        }
        this.status = AnalysisExecutionStatus.COMPLETED;
        this.selectedRootCause = rootCause;
        this.selectedConfidence = confidence;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    public void markInconclusive(double confidence) {
        if (this.status != AnalysisExecutionStatus.RUNNING) {
            throw new IllegalStateException("Analysis execution can only be marked inconclusive from RUNNING state");
        }
        this.status = AnalysisExecutionStatus.INCONCLUSIVE;
        this.selectedRootCause = "UNKNOWN";
        this.selectedConfidence = confidence;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    public void fail(String reason) {
        fail(reason, null);
    }

    public void fail(String reason, AnalysisExecutionFailureType failureType) {
        if (this.status == AnalysisExecutionStatus.COMPLETED || this.status == AnalysisExecutionStatus.INCONCLUSIVE) {
            throw new IllegalStateException("Completed executions cannot be marked as failed");
        }
        this.status = AnalysisExecutionStatus.FAILED;
        this.failureReason = reason;
        this.failureType = failureType;
        this.completedAt = LocalDateTime.now();
        this.attemptCount = this.attemptCount == null ? 1 : Math.max(this.attemptCount, 1);
        calculateDuration();
    }

    public void incrementAttemptCount() {
        this.attemptCount = (this.attemptCount == null ? 0 : this.attemptCount) + 1;
    }

    private void calculateDuration() {
        if (this.startedAt == null) {
            this.durationMs = 0L;
            return;
        }
        LocalDateTime end = this.completedAt == null ? LocalDateTime.now() : this.completedAt;
        this.durationMs = ChronoUnit.MILLIS.between(this.startedAt, end);
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public AnalysisExecutionStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSelectedRootCause() {
        return selectedRootCause;
    }

    public Double getSelectedConfidence() {
        return selectedConfidence;
    }

    public AnalysisExecutionFailureType getFailureType() {
        return failureType;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
