package com.CemHarput.IncidentInvestigator.incident.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record CreateIncidentRequest(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Description is required") String description,
        @NotBlank(message = "Incident type is required") String incidentType,
        @NotBlank(message = "Source is required") String source,
        LocalDateTime occurredAt
) {
}
