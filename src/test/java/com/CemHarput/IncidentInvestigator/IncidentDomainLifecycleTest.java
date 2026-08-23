package com.CemHarput.IncidentInvestigator;

import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import com.CemHarput.IncidentInvestigator.incident.exception.InvalidIncidentStateException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentDomainLifecycleTest {

    @Test
    void identifyRootCause_shouldRejectOpenIncident() {
        Incident incident = new Incident("t","d","type","src");
        RootCause rc = new RootCause("sum","type", true);
        assertThrows(InvalidIncidentStateException.class, () -> incident.identifyRootCause(rc));
    }

    @Test
    void resolve_shouldRejectWhenRootCauseMissing() {
        Incident incident = new Incident("t","d","type","src");
        incident.startInvestigation();
        assertThrows(InvalidIncidentStateException.class, incident::resolve);
    }

    @Test
    void close_shouldRejectOpenIncident() {
        Incident incident = new Incident("t","d","type","src");
        assertThrows(InvalidIncidentStateException.class, incident::close);
    }

    @Test
    void close_shouldRejectIncidentUnderInvestigation() {
        Incident incident = new Incident("t","d","type","src");
        incident.startInvestigation();
        assertThrows(InvalidIncidentStateException.class, incident::close);
    }

    @Test
    void close_shouldSucceedAfterResolve_and_notOverwriteResolvedAt() {
        Incident incident = new Incident("t","d","type","src");
        incident.startInvestigation();
        RootCause rc = new RootCause("sum","type", true);
        incident.identifyRootCause(rc);
        incident.resolve();
        LocalDateTime resolvedAt = incident.getResolvedAt();
        incident.close();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getResolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void shouldCompleteEntireLifecycle() {
        Incident incident = new Incident("t","d","type","src");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        incident.startInvestigation();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_INVESTIGATION);
        RootCause rc = new RootCause("sum","type", true);
        incident.identifyRootCause(rc);
        incident.resolve();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        incident.close();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
    }
}
