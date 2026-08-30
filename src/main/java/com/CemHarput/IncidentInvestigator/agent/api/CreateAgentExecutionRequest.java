package com.CemHarput.IncidentInvestigator.agent.api;

import jakarta.validation.constraints.NotNull;

public record CreateAgentExecutionRequest(
        @NotNull(message = "incidentId is required")
        Long incidentId
) {
}
