package com.CemHarput.IncidentInvestigator.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CemHarput.IncidentInvestigator.incident.exception.InvalidIncidentStateException;

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

    @Test
    void shouldProtectConfirmedRootCauseFromOverwrite() {
        Incident incident = new Incident(
                "Database outage",
                "Primary database connection pool exhausted",
                "SYSTEM_FAILURE",
                "MONITORING"
        );
        incident.startInvestigation();
        RootCause confirmed = new RootCause(
                "Connection pool misconfiguration",
                "CONFIGURATION",
                true
        );
        incident.identifyRootCause(confirmed);

        assertThat(incident.hasConfirmedRootCause()).isTrue();
        assertThatThrownBy(() -> incident.identifyRootCause(new RootCause(
                "Analyzer suggestion",
                "DATABASE",
                false
        )))
                .isInstanceOf(InvalidIncidentStateException.class)
                .hasMessage("Confirmed root cause cannot be overwritten");
        assertThat(incident.getRootCause()).isSameAs(confirmed);
    }
}
