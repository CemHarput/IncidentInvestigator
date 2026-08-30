package com.CemHarput.IncidentInvestigator.agent.api;

public record AgentExecutionAcceptedResponse(
        Long executionId,
        String agentName,
        String status
) {
}
