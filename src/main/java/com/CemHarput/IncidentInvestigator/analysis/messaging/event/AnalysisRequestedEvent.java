package com.CemHarput.IncidentInvestigator.analysis.messaging.event;

import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisEvidence;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AnalysisRequestedEvent(
        UUID eventId,
        Long executionId,
        Long incidentId,
        String title,
        String incidentType,
        List<AnalysisEvidence> evidence,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime requestedAt
) {
}
