package com.CemHarput.IncidentInvestigator.analysis.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record AnalysisFailedEvent(
        UUID eventId,
        Long executionId,
        Long incidentId,
        String failureType,
        String failureReason,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant failedAt
) {
}
