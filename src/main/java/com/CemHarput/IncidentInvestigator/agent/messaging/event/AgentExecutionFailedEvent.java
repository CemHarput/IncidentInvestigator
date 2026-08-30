package com.CemHarput.IncidentInvestigator.agent.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record AgentExecutionFailedEvent(
        UUID eventId,
        Long executionId,
        String agentName,
        String failureType,
        String failureReason,
        int completedSteps,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant failedAt
) {
}
