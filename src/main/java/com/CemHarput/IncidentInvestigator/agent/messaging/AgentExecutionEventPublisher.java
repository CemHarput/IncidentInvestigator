package com.CemHarput.IncidentInvestigator.agent.messaging;

import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionRequestedEvent;

public interface AgentExecutionEventPublisher {

    void publishRequested(AgentExecutionRequestedEvent event);
}
