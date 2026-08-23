package com.CemHarput.IncidentInvestigator.incident.api;

import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AddEvidenceRequest(

        @NotNull
        EvidenceType type,

        @NotBlank
        String source,

        @NotBlank
        String content,

        LocalDateTime observedAt

) {
}
