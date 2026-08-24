package com.CemHarput.IncidentInvestigator.analysis.dto;

import java.time.LocalDateTime;

public record AnalysisEvidence(
        String type,
        String source,
        String content,
        LocalDateTime observedAt
) {
}
