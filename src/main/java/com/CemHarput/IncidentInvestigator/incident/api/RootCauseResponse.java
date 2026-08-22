package com.CemHarput.IncidentInvestigator.incident.api;

import java.time.LocalDateTime;

public record RootCauseResponse(
        Long id,
        String summary,
        String rootCauseType,
        boolean confirmed,
        LocalDateTime createdAt
) {
}
