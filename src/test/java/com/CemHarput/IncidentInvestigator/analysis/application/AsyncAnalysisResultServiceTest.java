package com.CemHarput.IncidentInvestigator.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisCompletedEvent;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisFailedEvent;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsyncAnalysisResultServiceTest {

    @Test
    void completedEvent_shouldPersistBestCandidateAndCompleteExecution() {
        Fixture fixture = fixture();
        RootCauseCandidateResponse best = candidate(
                "DATABASE_CONNECTION_POOL_EXHAUSTION",
                0.91d
        );
        AnalysisCompletedEvent event = completedEvent(
                UUID.randomUUID(),
                List.of(candidate("UNKNOWN", 0.20d), best)
        );

        fixture.service.processCompleted(event);

        assertThat(fixture.execution.getStatus()).isEqualTo(AnalysisExecutionStatus.COMPLETED);
        assertThat(fixture.execution.getSelectedRootCause())
                .isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
        assertThat(fixture.execution.getResultEventId()).isEqualTo(event.eventId());
        assertThat(fixture.incident.getRootCause()).isNotNull();
        assertThat(fixture.incident.getRootCause().getRootCauseType())
                .isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
    }

    @Test
    void completedEvent_shouldMarkUnknownResultInconclusiveWithoutChangingIncident() {
        Fixture fixture = fixture();
        AnalysisCompletedEvent event = completedEvent(
                UUID.randomUUID(),
                List.of(candidate("UNKNOWN", 0.90d))
        );

        fixture.service.processCompleted(event);

        assertThat(fixture.execution.getStatus()).isEqualTo(AnalysisExecutionStatus.INCONCLUSIVE);
        assertThat(fixture.execution.getResultEventId()).isEqualTo(event.eventId());
        assertThat(fixture.incident.getRootCause()).isNull();
        verify(fixture.incidentRepository, never()).findByIdForAnalysis(42L);
    }

    @Test
    void completedEvent_shouldMarkLowConfidenceResultInconclusiveWithoutChangingIncident() {
        Fixture fixture = fixture();
        AnalysisCompletedEvent event = completedEvent(
                UUID.randomUUID(),
                List.of(candidate("DATABASE_CONNECTION_POOL_EXHAUSTION", 0.59d))
        );

        fixture.service.processCompleted(event);

        assertThat(fixture.execution.getStatus()).isEqualTo(AnalysisExecutionStatus.INCONCLUSIVE);
        assertThat(fixture.execution.getResultEventId()).isEqualTo(event.eventId());
        assertThat(fixture.incident.getRootCause()).isNull();
        verify(fixture.incidentRepository, never()).findByIdForAnalysis(42L);
    }

    @Test
    void failedEvent_shouldFailExecutionWithoutChangingIncident() {
        Fixture fixture = fixture();
        AnalysisFailedEvent event = failedEvent(UUID.randomUUID());

        fixture.service.processFailed(event);

        assertThat(fixture.execution.getStatus()).isEqualTo(AnalysisExecutionStatus.FAILED);
        assertThat(fixture.execution.getFailureType())
                .isEqualTo(AnalysisExecutionFailureType.INTERNAL_ERROR);
        assertThat(fixture.execution.getFailureReason()).isEqualTo("Analyzer failed");
        assertThat(fixture.execution.getResultEventId()).isEqualTo(event.eventId());
        assertThat(fixture.incident.getRootCause()).isNull();
        verify(fixture.incidentRepository, never()).findByIdForAnalysis(42L);
    }

    @Test
    void duplicateEvent_shouldBeIgnoredWithoutMutatingIncidentTwice() {
        Fixture fixture = fixture();
        AnalysisCompletedEvent event = completedEvent(
                UUID.randomUUID(),
                List.of(candidate("DATABASE_CONNECTION_POOL_EXHAUSTION", 0.91d))
        );

        fixture.service.processCompleted(event);
        fixture.service.processCompleted(event);

        assertThat(fixture.execution.getStatus()).isEqualTo(AnalysisExecutionStatus.COMPLETED);
        assertThat(fixture.execution.getResultEventId()).isEqualTo(event.eventId());
        verify(fixture.incidentRepository, times(1)).findByIdForAnalysis(42L);
    }

    @Test
    void differentEventForTerminalExecution_shouldBeRejected() {
        Fixture fixture = fixture();
        fixture.service.processFailed(failedEvent(UUID.randomUUID()));

        assertThatThrownBy(() -> fixture.service.processFailed(failedEvent(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Analysis execution already has a different result");
    }

    @Test
    void mismatchedIncidentId_shouldBeRejected() {
        Fixture fixture = fixture();
        AnalysisCompletedEvent event = new AnalysisCompletedEvent(
                UUID.randomUUID(),
                99L,
                43L,
                List.of(candidate("DATABASE_CONNECTION_POOL_EXHAUSTION", 0.91d)),
                LocalDateTime.now()
        );

        assertThatThrownBy(() -> fixture.service.processCompleted(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Result event incidentId does not match analysis execution");
    }

    @Test
    void unsupportedFailureType_shouldBeRejected() {
        Fixture fixture = fixture();
        AnalysisFailedEvent event = new AnalysisFailedEvent(
                UUID.randomUUID(),
                99L,
                42L,
                "UNKNOWN_FAILURE_TYPE",
                "Analyzer failed",
                LocalDateTime.now()
        );

        assertThatThrownBy(() -> fixture.service.processFailed(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported analysis failureType: UNKNOWN_FAILURE_TYPE");
    }

    private Fixture fixture() {
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AnalysisExecution execution = AnalysisExecution.create(42L);
        execution.queue();
        Incident incident = new Incident(
                "Payment service latency",
                "Database connection pool exhausted",
                "LATENCY",
                "MONITORING"
        );
        incident.startInvestigation();
        when(executionRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(execution));
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident));
        AsyncAnalysisResultService service = new AsyncAnalysisResultService(
                executionRepository,
                incidentRepository,
                new AnalysisResultEvaluator()
        );
        return new Fixture(service, execution, incident, incidentRepository);
    }

    private AnalysisCompletedEvent completedEvent(
            UUID eventId,
            List<RootCauseCandidateResponse> candidates
    ) {
        return new AnalysisCompletedEvent(
                eventId,
                99L,
                42L,
                candidates,
                LocalDateTime.now()
        );
    }

    private AnalysisFailedEvent failedEvent(UUID eventId) {
        return new AnalysisFailedEvent(
                eventId,
                99L,
                42L,
                "INTERNAL_ERROR",
                "Analyzer failed",
                LocalDateTime.now()
        );
    }

    private RootCauseCandidateResponse candidate(String rootCause, double confidence) {
        return new RootCauseCandidateResponse(
                rootCause,
                confidence,
                "Analyzer explanation",
                List.of("supporting-evidence")
        );
    }

    private record Fixture(
            AsyncAnalysisResultService service,
            AnalysisExecution execution,
            Incident incident,
            IncidentRepository incidentRepository
    ) {
    }
}
