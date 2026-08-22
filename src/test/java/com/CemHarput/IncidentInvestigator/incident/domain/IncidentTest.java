package com.CemHarput.IncidentInvestigator.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IncidentTest {

    @Test
    void shouldTrackLifecycleFromOpenToResolved() {
        Incident incident = new Incident(
                "Database outage",
                "Primary database connection pool exhausted during peak time",
                "SYSTEM_FAILURE",
                "MONITORING"
        );

        incident.assignTo("ops-team");
        incident.startInvestigation();

        RootCause rootCause = new RootCause(
                "Connection pool misconfiguration",
                "CONFIGURATION",
                true
        );
        incident.identifyRootCause(rootCause);
        incident.resolve();

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getAssignedTo()).isEqualTo("ops-team");
        assertThat(incident.getRootCause()).isEqualTo(rootCause);
        assertThat(incident.getResolvedAt()).isNotNull();
    }
}
