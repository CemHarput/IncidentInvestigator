package com.CemHarput.IncidentInvestigator.analysis.dto;

import java.util.List;

public record AnalysisResponse(
        Long incidentId,
        List<RootCauseCandidateResponse> candidates
) {
}
