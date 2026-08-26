package com.CemHarput.IncidentInvestigator.analysis.api;

public record AsyncAnalysisResponse(
        Long executionId,
        Long incidentId,
        String status
) {
}
