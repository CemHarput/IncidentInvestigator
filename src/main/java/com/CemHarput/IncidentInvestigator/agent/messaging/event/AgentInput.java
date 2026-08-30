package com.CemHarput.IncidentInvestigator.agent.messaging.event;

import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisEvidence;
import java.util.List;

public record AgentInput(
        Long incidentId,
        String title,
        String incidentType,
        List<AnalysisEvidence> evidence
) {

    public AgentInput {
        evidence = List.copyOf(evidence);
    }
}
