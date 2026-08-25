package com.CemHarput.IncidentInvestigator.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.api.AnalysisResultResponse;
import com.CemHarput.IncidentInvestigator.analysis.client.IncidentAnalyzerClient;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerDownstreamException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerUnavailableException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerRequestException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

    private static final AnalysisRequest REQUEST = new AnalysisRequest(
            42L,
                "Payment service latency",
                "LATENCY",
            List.of()
        );

    @Test
    void analyzeIncident_shouldPersistBestCandidateWhenConfidenceAboveThreshold() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        RootCauseCandidateResponse bestCandidate = candidate(0.91d);
        when(client.analyze(REQUEST)).thenReturn(new AnalysisResponse(
                42L,
                List.of(candidate(0.40d), bestCandidate)
        ));

        AnalysisService service = service(persistenceService, client, 2);

        AnalysisResultResponse result = service.analyzeIncident(42L);

        assertThat(result.executionId()).isEqualTo(99L);
        assertThat(result.status()).isEqualTo("ROOT_CAUSE_IDENTIFIED");
        assertThat(result.rootCause()).isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
        verify(persistenceService).completeAnalysis(99L, bestCandidate);
        verify(persistenceService, never()).persistFailure(any(), any(), any());
    }

    @Test
    void analyzeIncident_shouldRetryTimeoutOnceAndTrackSecondAttempt() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(REQUEST))
                .thenThrow(new AnalyzerUnavailableException(
                        "Incident analyzer request timed out",
                        AnalysisExecutionFailureType.TIMEOUT,
                        new RuntimeException()
                ))
                .thenReturn(new AnalysisResponse(42L, List.of(candidate(0.91d))));

        AnalysisService service = service(persistenceService, client, 2);

        AnalysisResultResponse result = service.analyzeIncident(42L);

        assertThat(result.status()).isEqualTo("ROOT_CAUSE_IDENTIFIED");
        verify(client, times(2)).analyze(REQUEST);
        verify(persistenceService).incrementAttemptCount(99L);
        verify(persistenceService, never()).persistFailure(any(), any(), any());
    }

    @Test
    void analyzeIncident_shouldPersistFailureAfterTransientRetriesAreExhausted() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        AnalyzerDownstreamException failure = new AnalyzerDownstreamException(
                "Incident analyzer returned status 503",
                503,
                new RuntimeException()
        );
        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(REQUEST)).thenThrow(failure);

        AnalysisService service = service(persistenceService, client, 2);

        assertThatThrownBy(() -> service.analyzeIncident(42L)).isSameAs(failure);

        verify(client, times(2)).analyze(REQUEST);
        verify(persistenceService).incrementAttemptCount(99L);
        verify(persistenceService).persistFailure(
                99L,
                failure.getMessage(),
                AnalysisExecutionFailureType.DOWNSTREAM_5XX
        );
    }

    @Test
    void analyzeIncident_shouldNotRetryDownstream4xx() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        InvalidAnalyzerRequestException failure = new InvalidAnalyzerRequestException(
                "Incident analyzer rejected request with status 422",
                new RuntimeException()
        );
        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(REQUEST)).thenThrow(failure);

        AnalysisService service = service(persistenceService, client, 2);

        assertThatThrownBy(() -> service.analyzeIncident(42L)).isSameAs(failure);

        verify(client).analyze(REQUEST);
        verify(persistenceService, never()).incrementAttemptCount(any());
        verify(persistenceService).persistFailure(
                99L,
                failure.getMessage(),
                AnalysisExecutionFailureType.DOWNSTREAM_4XX
        );
    }

    @Test
    void analyzeIncident_shouldNotRetryNonTransientDownstream5xx() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        AnalyzerDownstreamException failure = new AnalyzerDownstreamException(
                "Incident analyzer returned status 500",
                500,
                new RuntimeException()
        );
        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(REQUEST)).thenThrow(failure);

        AnalysisService service = service(persistenceService, client, 2);

        assertThatThrownBy(() -> service.analyzeIncident(42L)).isSameAs(failure);

        verify(client).analyze(REQUEST);
        verify(persistenceService, never()).incrementAttemptCount(any());
        verify(persistenceService).persistFailure(
                99L,
                failure.getMessage(),
                AnalysisExecutionFailureType.DOWNSTREAM_5XX
        );
    }

    @Test
    void analyzeIncident_shouldMapInvalidResponseToNonRetryableFailure() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(REQUEST)).thenReturn(null);

        AnalysisService service = service(persistenceService, client, 2);

        assertThatThrownBy(() -> service.analyzeIncident(42L))
                .hasMessage("Analyzer returned an empty response");

        verify(client).analyze(REQUEST);
        verify(persistenceService).persistFailure(
                99L,
                "Analyzer returned an empty response",
                AnalysisExecutionFailureType.INVALID_RESPONSE
        );
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

    private AnalysisService service(
            AnalysisPersistenceService persistenceService,
            IncidentAnalyzerClient client,
            int maxAttempts
    ) {
        return new AnalysisService(
                persistenceService,
                client,
                new SimpleMeterRegistry(),
                maxAttempts,
                Duration.ZERO
        );
    }

    private RootCauseCandidateResponse candidate(double confidence) {
        return new RootCauseCandidateResponse(
                "DATABASE_CONNECTION_POOL_EXHAUSTION",
                confidence,
                "Database connection pool saturation matches the observed timeout symptoms.",
                List.of("db_connection_pool_usage=100")
        );
    }
}
