package com.CemHarput.IncidentInvestigator.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisAlreadyRunningException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisNotAllowedException;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.incident.domain.Evidence;
import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AnalysisPersistenceServiceTest {

    @Test
    void beginAnalysis_shouldRejectAnExistingActiveExecution() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident()));
        AnalysisExecution queuedExecution = AnalysisExecution.create(42L);
        queuedExecution.queue();
        when(executionRepository.findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
                any(), any()
        )).thenReturn(Optional.of(queuedExecution));

        AnalysisPersistenceService service = new AnalysisPersistenceService(
                incidentRepository,
                executionRepository
        );

        assertThatThrownBy(() -> service.beginAnalysis(42L))
                .isInstanceOf(AnalysisAlreadyRunningException.class)
                .hasMessage("An analysis is already running for incident 42");

        verify(executionRepository, never()).save(any());
    }

    @Test
    void beginAnalysis_shouldCreateRunningExecutionAndRequestSnapshot() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident()));
        when(executionRepository.findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
                any(), any()
        )).thenReturn(Optional.empty());
        AnalysisExecution savedExecution = mock(AnalysisExecution.class);
        when(savedExecution.getId()).thenReturn(99L);
        when(executionRepository.save(any(AnalysisExecution.class))).thenReturn(savedExecution);

        AnalysisPersistenceService service = new AnalysisPersistenceService(
                incidentRepository,
                executionRepository
        );

        AnalysisPreparation preparation = service.beginAnalysis(42L);

        assertThat(preparation.executionId()).isEqualTo(99L);
        assertThat(preparation.request().incidentId()).isEqualTo(42L);
        assertThat(preparation.request().evidence()).hasSize(1);
        verify(executionRepository).save(any(AnalysisExecution.class));
    }

    @Test
    void beginAsyncAnalysis_shouldCreateQueuedExecutionAndRequestSnapshot() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident()));
        when(executionRepository.findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
                any(), any()
        )).thenReturn(Optional.empty());
        AnalysisExecution savedExecution = mock(AnalysisExecution.class);
        when(savedExecution.getId()).thenReturn(99L);
        when(executionRepository.save(any(AnalysisExecution.class))).thenReturn(savedExecution);

        AnalysisPersistenceService service = new AnalysisPersistenceService(
                incidentRepository,
                executionRepository
        );

        AnalysisPreparation preparation = service.beginAsyncAnalysis(42L);

        assertThat(preparation.executionId()).isEqualTo(99L);
        assertThat(preparation.request().incidentId()).isEqualTo(42L);
        assertThat(preparation.request().evidence()).hasSize(1);
        verify(executionRepository).save(org.mockito.ArgumentMatchers.argThat(
                execution -> execution.getStatus() == AnalysisExecutionStatus.QUEUED
        ));
    }

    @Test
    void beginAsyncAnalysis_shouldRejectAnExistingActiveExecution() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident()));
        AnalysisExecution queuedExecution = AnalysisExecution.create(42L);
        queuedExecution.queue();
        when(executionRepository.findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
                any(), any()
        )).thenReturn(Optional.of(queuedExecution));
        AnalysisPersistenceService service = new AnalysisPersistenceService(
                incidentRepository,
                executionRepository
        );

        assertThatThrownBy(() -> service.beginAsyncAnalysis(42L))
                .isInstanceOf(AnalysisAlreadyRunningException.class);

        verify(executionRepository, never()).save(any());
    }

    @Test
    void beginAsyncAnalysis_shouldRejectIncidentWithoutEvidence() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        Incident incident = new Incident(
                "Payment service latency",
                "Database connection pool exhausted",
                "LATENCY",
                "MONITORING"
        );
        incident.startInvestigation();
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident));
        AnalysisPersistenceService service = new AnalysisPersistenceService(
                incidentRepository,
                executionRepository
        );

        assertThatThrownBy(() -> service.beginAsyncAnalysis(42L))
                .isInstanceOf(AnalysisNotAllowedException.class)
                .hasMessage("Incident must contain evidence before analysis");

        verify(executionRepository, never()).save(any());
    }

    @Test
    void beginAsyncAnalysis_shouldRejectConfirmedRootCause() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        Incident incident = incident();
        incident.identifyRootCause(new RootCause(
                "Connection pool exhaustion confirmed",
                "DATABASE_CONNECTION_POOL_EXHAUSTION",
                true
        ));
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident));
        AnalysisPersistenceService service = new AnalysisPersistenceService(
                incidentRepository,
                executionRepository
        );

        assertThatThrownBy(() -> service.beginAsyncAnalysis(42L))
                .isInstanceOf(AnalysisNotAllowedException.class)
                .hasMessage("Incident already has a confirmed root cause");

        verify(executionRepository, never()).save(any());
    }

    @Test
    void persistFailure_shouldUseRequiresNewAndStoreClassification() throws Exception {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        AnalysisExecution execution = AnalysisExecution.create(42L);
        execution.start();
        when(executionRepository.findById(99L)).thenReturn(Optional.of(execution));

        AnalysisPersistenceService service = new AnalysisPersistenceService(
                incidentRepository,
                executionRepository
        );

        service.persistFailure(99L, "Read timed out", AnalysisExecutionFailureType.TIMEOUT);

        Transactional annotation = AnalysisPersistenceService.class
                .getMethod(
                        "persistFailure",
                        Long.class,
                        String.class,
                        AnalysisExecutionFailureType.class
                )
                .getAnnotation(Transactional.class);
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.FAILED);
        assertThat(execution.getFailureType()).isEqualTo(AnalysisExecutionFailureType.TIMEOUT);
        assertThat(execution.getFailureReason()).isEqualTo("Read timed out");
    }

    private Incident incident() {
        Incident incident = new Incident(
                "Payment service latency",
                "Database connection pool exhausted",
                "LATENCY",
                "MONITORING"
        );
        incident.startInvestigation();
        incident.addEvidence(new Evidence(
                EvidenceType.LOG,
                "payment-service",
                "HikariPool - Connection is not available",
                LocalDateTime.of(2026, 8, 24, 12, 0)
        ));
        return incident;
    }
}
