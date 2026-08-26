package com.CemHarput.IncidentInvestigator.analysis.messaging.event;

import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AnalysisCompletedEvent(
        UUID eventId,
        Long executionId,
        Long incidentId,
        List<RootCauseCandidateResponse> candidates,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime completedAt
) {
}
