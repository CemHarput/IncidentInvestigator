package com.CemHarput.IncidentInvestigator.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import com.CemHarput.IncidentInvestigator.incident.exception.InvalidIncidentStateException;
import org.junit.jupiter.api.Test;

class IncidentEvidenceTest {

    private Incident createIncident() {
        return new Incident("t", "d", "type", "src");
    }

    @Test
    void shouldAddEvidenceDuringInvestigation() {
        Incident incident = createIncident();
        incident.startInvestigation();

        Evidence evidence = new Evidence(
                EvidenceType.LOG,
                "payment-service",
                "Connection pool exhausted",
                LocalDateTime.now()
        );

        incident.addEvidence(evidence);

        assertThat(incident.getEvidence()).hasSize(1);
    }

    @Test
    void shouldRejectEvidenceWhenIncidentIsOpen() {
        Incident incident = createIncident();

        Evidence evidence = new Evidence(
                EvidenceType.LOG,
                "payment-service",
                "Connection pool exhausted",
                LocalDateTime.now()
        );

        assertThatThrownBy(() -> incident.addEvidence(evidence))
                .isInstanceOf(InvalidIncidentStateException.class);
    }
}
