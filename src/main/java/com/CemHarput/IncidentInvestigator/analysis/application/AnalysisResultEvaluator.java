package com.CemHarput.IncidentInvestigator.analysis.application;

import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerResponseException;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class AnalysisResultEvaluator {

    private static final double MIN_CONFIDENCE = 0.60d;

    public Evaluation evaluate(Long expectedIncidentId, AnalysisResponse response) {
        validateResponse(expectedIncidentId, response);
        RootCauseCandidateResponse bestCandidate = response.candidates().stream()
                .max(Comparator.comparingDouble(RootCauseCandidateResponse::confidence))
                .orElseThrow(() -> new InvalidAnalyzerResponseException(
                        "Analyzer returned no root cause candidates"
                ));
        return new Evaluation(bestCandidate, isInconclusive(bestCandidate));
    }

    private void validateResponse(Long expectedIncidentId, AnalysisResponse response) {
        if (response == null) {
            throw new InvalidAnalyzerResponseException("Analyzer returned an empty response");
        }

        if (!expectedIncidentId.equals(response.incidentId())) {
            throw new InvalidAnalyzerResponseException(
                    "Analyzer response incidentId does not match request"
            );
        }

        if (response.candidates() == null || response.candidates().isEmpty()) {
            throw new InvalidAnalyzerResponseException(
                    "Analyzer returned no root cause candidates"
            );
        }

        for (RootCauseCandidateResponse candidate : response.candidates()) {
            if (candidate == null) {
                throw new InvalidAnalyzerResponseException(
                        "Analyzer returned a null candidate"
                );
            }

            if (candidate.rootCause() == null || candidate.rootCause().isBlank()) {
                throw new InvalidAnalyzerResponseException(
                        "Analyzer returned a candidate with a blank root cause"
                );
            }

            if (candidate.confidence() < 0 || candidate.confidence() > 1.0d) {
                throw new InvalidAnalyzerResponseException(
                        "Analyzer returned a candidate with an invalid confidence value"
                );
            }
        }
    }

    private boolean isInconclusive(RootCauseCandidateResponse candidate) {
        return "UNKNOWN".equalsIgnoreCase(candidate.rootCause())
                || candidate.confidence() < MIN_CONFIDENCE;
    }

    public record Evaluation(
            RootCauseCandidateResponse candidate,
            boolean inconclusive
    ) {
    }
}
