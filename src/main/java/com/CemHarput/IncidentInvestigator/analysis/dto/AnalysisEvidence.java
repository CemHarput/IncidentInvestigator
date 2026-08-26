package com.CemHarput.IncidentInvestigator.analysis.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record AnalysisEvidence(
        String type,
        String source,
        String content,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime observedAt
) {
}
