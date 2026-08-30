package com.CemHarput.IncidentInvestigator.agent.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record AgentExecutionRequestedEvent(
        UUID eventId,
        Long executionId,
        String agentName,
        String agentVersion,
        AgentLimitsContract limits,
        AgentInput input,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant requestedAt
) {
}
