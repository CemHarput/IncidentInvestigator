package com.CemHarput.IncidentInvestigator.agent.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record AgentExecutionFailedEvent(
        String eventType,
        UUID eventId,
        Long executionId,
        String agentName,
        String failureType,
        String failureReason,
        int completedSteps,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant failedAt
) {
    public AgentExecutionFailedEvent(
            UUID eventId,
            Long executionId,
            String agentName,
            String failureType,
            String failureReason,
            int completedSteps,
            Instant failedAt
    ) {
        this(
                "FAILED",
                eventId,
                executionId,
                agentName,
                failureType,
                failureReason,
                completedSteps,
                failedAt
        );
    }
}
