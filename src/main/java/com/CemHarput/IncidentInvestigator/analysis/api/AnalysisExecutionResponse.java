package com.CemHarput.IncidentInvestigator.analysis.api;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import java.time.LocalDateTime;

public record AnalysisExecutionResponse(
        Long id,
        Long incidentId,
        String status,
        String selectedRootCause,
        Double selectedConfidence,
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
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getDurationMs()
        );
    }
}
