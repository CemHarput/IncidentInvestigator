package com.CemHarput.IncidentInvestigator.agent.application;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentDefinition;

public interface AgentDefinitionRegistry {

    AgentDefinition getRequired(String agentName);
}
