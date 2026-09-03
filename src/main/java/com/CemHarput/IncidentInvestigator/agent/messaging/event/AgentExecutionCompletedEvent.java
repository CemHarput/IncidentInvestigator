package com.CemHarput.IncidentInvestigator.agent.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record AgentExecutionCompletedEvent(
        String eventType,
        UUID eventId,
        Long executionId,
        String agentName,
        AgentResult result,
        int totalSteps,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant completedAt
) {
    public AgentExecutionCompletedEvent(
            UUID eventId,
            Long executionId,
            String agentName,
            AgentResult result,
            int totalSteps,
            Instant completedAt
    ) {
        this(
                "COMPLETED",
                eventId,
                executionId,
                agentName,
                result,
                totalSteps,
                completedAt
        );
    }
}
