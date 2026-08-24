package com.CemHarput.IncidentInvestigator.analysis.api;

import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import java.util.List;

public record AnalysisResultResponse(
        Long incidentId,
        String status,
        String rootCause,
        Double confidence,
        String explanation,
        List<String> supportingEvidence
) {

    public static AnalysisResultResponse identified(Long incidentId, RootCauseCandidateResponse candidate) {
        return new AnalysisResultResponse(
                incidentId,
                "ROOT_CAUSE_IDENTIFIED",
                candidate.rootCause(),
                candidate.confidence(),
                candidate.explanation(),
                candidate.supportingEvidence()
        );
    }

    public static AnalysisResultResponse inconclusive(Long incidentId, RootCauseCandidateResponse candidate) {
        String explanation = candidate != null && candidate.explanation() != null && !candidate.explanation().isBlank()
                ? candidate.explanation()
                : "Available evidence is insufficient for a confident diagnosis.";

        double confidence = candidate == null ? 0.0d : candidate.confidence();

        return new AnalysisResultResponse(
                incidentId,
                "INCONCLUSIVE",
                "UNKNOWN",
                confidence,
                explanation,
                List.of()
        );
    }
}
