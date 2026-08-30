package com.CemHarput.IncidentInvestigator.agent.messaging.event;

public record AgentLimitsContract(int maxSteps, long timeoutSeconds) {
}
