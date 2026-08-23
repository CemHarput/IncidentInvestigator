package com.CemHarput.IncidentInvestigator.incident.api;

import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import java.time.LocalDateTime;

public record EvidenceResponse(
        Long id,
        EvidenceType type,
        String source,
        String content,
        LocalDateTime observedAt,
        LocalDateTime createdAt
) {
}
