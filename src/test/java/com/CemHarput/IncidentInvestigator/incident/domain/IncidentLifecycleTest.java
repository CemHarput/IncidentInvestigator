package com.CemHarput.IncidentInvestigator.incident.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IncidentLifecycleTest {

    @Test
    void startInvestigation_shouldRejectNonOpenIncidents() {
        Incident incident = new Incident(
                "Database outage",
                "Primary database connection pool exhausted",
                "SYSTEM_FAILURE",
                "MONITORING"
        );

        incident.startInvestigation();

        assertThatThrownBy(incident::startInvestigation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only OPEN incidents can be investigated");
    }

    @Test
    void resolve_shouldRejectIncidentsThatAreNotUnderInvestigation() {
        Incident incident = new Incident(
                "Database outage",
                "Primary database connection pool exhausted",
                "SYSTEM_FAILURE",
                "MONITORING"
        );

        assertThatThrownBy(incident::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Incident must be under investigation before resolving");
    }
}
