package com.CemHarput.IncidentInvestigator.analysis.api;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import java.time.LocalDateTime;

public record AnalysisExecutionResponse(
        Long id,
        Long incidentId,
        String status,
        String selectedRootCause,
        Double selectedConfidence,
        String failureType,
        String failureReason,
        Integer attemptCount,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMs
) {

    public static AnalysisExecutionResponse from(AnalysisExecution execution) {
        return new AnalysisExecutionResponse(
                execution.getId(),
                execution.getIncidentId(),
                execution.getStatus().name(),
                execution.getSelectedRootCause(),
                execution.getSelectedConfidence(),
                execution.getFailureType() == null ? null : execution.getFailureType().name(),
                execution.getFailureReason(),
                execution.getAttemptCount(),
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getDurationMs()
        );
    }
}
