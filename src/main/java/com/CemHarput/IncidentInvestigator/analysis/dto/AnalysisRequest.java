package com.CemHarput.IncidentInvestigator.analysis.dto;

import java.util.List;

public record AnalysisRequest(
        Long incidentId,
        String title,
        String incidentType,
        List<AnalysisEvidence> evidence
) {
}
