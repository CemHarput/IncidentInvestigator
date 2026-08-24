package com.CemHarput.IncidentInvestigator.analysis.dto;

import java.util.List;

public record RootCauseCandidateResponse(
        String rootCause,
        double confidence,
        String explanation,
        List<String> supportingEvidence
) {
}
