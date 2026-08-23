package com.CemHarput.IncidentInvestigator.incident.api;

import jakarta.validation.constraints.NotBlank;

public record AddRootCauseRequest(
        @NotBlank(message = "Summary is required") String summary,
        @NotBlank(message = "Root cause type is required") String rootCauseType,
        boolean confirmed
) {
}
