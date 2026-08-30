package com.CemHarput.IncidentInvestigator.agent.domain;

import java.util.List;
import java.util.Objects;

public record AgentDefinition(
        String name,
        String version,
        List<String> capabilities,
        AgentLimits limits
) {

    public AgentDefinition {
        Objects.requireNonNull(name, "Agent name is required");
        Objects.requireNonNull(version, "Agent version is required");
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "Agent capabilities are required"));
        Objects.requireNonNull(limits, "Agent limits are required");
    }
}
