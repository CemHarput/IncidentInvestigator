package com.CemHarput.IncidentInvestigator.incident.api;

import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import java.time.LocalDateTime;

public record IncidentResponse(
        Long id,
        String title,
        String description,
        String incidentType,
        String source,
        String assignedTo,
        IncidentStatus status,
        LocalDateTime reportedAt,
        LocalDateTime occurredAt,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        RootCauseResponse rootCause
) {
}
