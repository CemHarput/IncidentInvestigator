package com.CemHarput.IncidentInvestigator.agent.exception;

public class AgentDefinitionNotFoundException extends RuntimeException {

    public AgentDefinitionNotFoundException(String agentName) {
        super("Agent definition not found: " + agentName);
    }
}
