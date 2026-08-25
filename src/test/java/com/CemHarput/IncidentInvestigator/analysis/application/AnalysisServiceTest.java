package com.CemHarput.IncidentInvestigator.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.api.AnalysisResultResponse;
import com.CemHarput.IncidentInvestigator.analysis.client.IncidentAnalyzerClient;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisNotAllowedException;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.incident.domain.Evidence;
import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

    @Test
    void analyzeIncident_shouldAttachBestCandidateWhenConfidenceAboveThreshold() {
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
                LocalDateTime.of(2026, 8, 24, 12, 0, 0)
        ));
        incident.addEvidence(new Evidence(
                EvidenceType.METRIC,
                "payment-service",
                "db_connection_pool_usage=100",
                LocalDateTime.of(2026, 8, 24, 12, 1, 0)
        ));

        IncidentRepository repository = mock(IncidentRepository.class);
        when(repository.findById(42L)).thenReturn(Optional.of(incident));

        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(any(AnalysisRequest.class))).thenReturn(new AnalysisResponse(
                42L,
                List.of(
                        new RootCauseCandidateResponse(
                                "DATABASE_CONNECTION_POOL_EXHAUSTION",
                                0.40,
                                "Weak match",
                                List.of("db_connection_pool_usage=100")
                        ),
                        new RootCauseCandidateResponse(
                                "DATABASE_CONNECTION_POOL_EXHAUSTION",
                                0.91,
                                "Database connection pool saturation matches the observed timeout symptoms.",
                                List.of(
                                        "HikariPool - Connection is not available",
                                        "db_connection_pool_usage=100"
                                )
                        )
                )
        ));

        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.save(any(AnalysisExecution.class))).thenAnswer(invocation -> {
            AnalysisExecution execution = invocation.getArgument(0);
            execution.setId(99L);
            return execution;
        });

        AnalysisService service = new AnalysisService(repository, executionRepository, client);

        AnalysisResultResponse result = service.analyzeIncident(42L);

        assertThat(result.executionId()).isEqualTo(99L);
        assertThat(result.status()).isEqualTo("ROOT_CAUSE_IDENTIFIED");
        assertThat(result.rootCause()).isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
        assertThat(incident.getRootCause()).isNotNull();
        assertThat(incident.getRootCause().getRootCauseType()).isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
        assertThat(incident.getRootCause().isConfirmed()).isFalse();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_INVESTIGATION);
        verify(executionRepository).save(any(AnalysisExecution.class));
    }

    @Test
    void analyzeIncident_shouldRejectOpenIncident() {
        Incident incident = new Incident(
                "Payment service latency",
                "Database connection pool exhausted",
                "LATENCY",
                "MONITORING"
        );

        IncidentRepository repository = mock(IncidentRepository.class);
        when(repository.findById(42L)).thenReturn(Optional.of(incident));

        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        AnalysisService service = new AnalysisService(repository, executionRepository, client);

        assertThatThrownBy(() -> service.analyzeIncident(42L))
                .isInstanceOf(AnalysisNotAllowedException.class)
                .hasMessage("Only incidents under investigation can be analyzed");

        verify(client, never()).analyze(any(AnalysisRequest.class));
        verify(executionRepository, never()).save(any(AnalysisExecution.class));
    }

    @Test
    void inconclusive_shouldKeepAnalyzerConfidenceValue() {
        RootCauseCandidateResponse candidate = new RootCauseCandidateResponse(
                "UNKNOWN",
                0.05d,
                "Available evidence is insufficient for a confident diagnosis.",
                List.of()
        );

        AnalysisResultResponse result = AnalysisResultResponse.inconclusive(42L, candidate);

        assertThat(result.executionId()).isNull();
        assertThat(result.confidence()).isEqualTo(0.05d);
        assertThat(result.status()).isEqualTo("INCONCLUSIVE");
        assertThat(result.rootCause()).isEqualTo("UNKNOWN");
    }

    @Test
    void persistFailure_shouldWriteFailedExecutionBeforeRethrowingRuntimeException() {
        AnalysisExecution execution = AnalysisExecution.create(42L);
        execution.start();

        IncidentRepository repository = mock(IncidentRepository.class);
        AnalysisExecutionRepository executionRepository = mock(AnalysisExecutionRepository.class);
        when(executionRepository.save(any(AnalysisExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnalysisService service = new AnalysisService(repository, executionRepository, mock(IncidentAnalyzerClient.class));

        RuntimeException ex = new RuntimeException("Python unavailable");
        service.persistFailure(execution, ex);

        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("Python unavailable");
        verify(executionRepository).save(execution);
    }
}
