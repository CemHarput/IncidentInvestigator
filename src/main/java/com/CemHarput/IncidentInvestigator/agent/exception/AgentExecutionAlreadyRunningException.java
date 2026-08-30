package com.CemHarput.IncidentInvestigator.agent.exception;

public class AgentExecutionAlreadyRunningException extends RuntimeException {

    public AgentExecutionAlreadyRunningException(String agentName, Long incidentId) {
        super("An active " + agentName + " execution already exists for incident: " + incidentId);
    }
}
