package com.CemHarput.IncidentInvestigator.agent.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record AgentExecutionStepEvent(
        String eventType,
        UUID eventId,
        Long executionId,
        int stepNumber,
        String stepType,
        String capability,
        String observationSummary,
        String reasoningSummary,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt
) {
    public AgentExecutionStepEvent(
            UUID eventId,
            Long executionId,
            int stepNumber,
            String stepType,
            String capability,
            String observationSummary,
            String reasoningSummary,
            Instant occurredAt
    ) {
        this(
                "STEP",
                eventId,
                executionId,
                stepNumber,
                stepType,
                capability,
                observationSummary,
                reasoningSummary,
                occurredAt
        );
    }
}
