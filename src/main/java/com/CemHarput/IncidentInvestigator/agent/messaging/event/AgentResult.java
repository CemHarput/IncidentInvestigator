package com.CemHarput.IncidentInvestigator.agent.messaging.event;

import java.util.List;

public record AgentResult(
        String rootCause,
        double confidence,
        String explanation,
        List<String> supportingEvidence
) {

    public AgentResult {
        supportingEvidence = supportingEvidence == null
                ? List.of()
                : List.copyOf(supportingEvidence);
    }
}
