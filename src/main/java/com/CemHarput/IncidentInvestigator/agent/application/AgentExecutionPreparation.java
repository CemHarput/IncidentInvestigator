package com.CemHarput.IncidentInvestigator.agent.application;

import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionRequestedEvent;

public record AgentExecutionPreparation(AgentExecutionRequestedEvent event) {
}
