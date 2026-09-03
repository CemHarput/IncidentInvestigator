package com.CemHarput.IncidentInvestigator.incident.domain;

import org.springframework.stereotype.Component;

@Component
public class RootCauseDecisionPolicy {

    private static final double MIN_CONFIDENCE = 0.60d;

    public boolean isInconclusive(String rootCause, double confidence) {
        return "UNKNOWN".equalsIgnoreCase(rootCause)
                || confidence < MIN_CONFIDENCE;
    }
}
