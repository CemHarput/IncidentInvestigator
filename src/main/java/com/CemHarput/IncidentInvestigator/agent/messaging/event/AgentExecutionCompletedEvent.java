package com.CemHarput.IncidentInvestigator.agent.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record AgentExecutionCompletedEvent(
        UUID eventId,
        Long executionId,
        String agentName,
        AgentResult result,
        int totalSteps,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant completedAt
) {
}
