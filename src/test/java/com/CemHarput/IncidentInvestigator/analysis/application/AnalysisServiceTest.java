package com.CemHarput.IncidentInvestigator.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.api.AnalysisResultResponse;
import com.CemHarput.IncidentInvestigator.analysis.api.AsyncAnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.client.IncidentAnalyzerClient;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerDownstreamException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerUnavailableException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisMessagingException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerRequestException;
import com.CemHarput.IncidentInvestigator.analysis.messaging.AnalysisEventPublisher;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.mockito.ArgumentCaptor;
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
    void analyzeIncidentAsync_shouldPublishQueuedExecutionSnapshot() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAsyncAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));
        AnalysisEventPublisher publisher = mock(AnalysisEventPublisher.class);
        AnalysisService service = service(
                persistenceService,
                mock(IncidentAnalyzerClient.class),
                publisher,
                2
        );

        AsyncAnalysisResponse response = service.analyzeIncidentAsync(42L);

        assertThat(response.executionId()).isEqualTo(99L);
        assertThat(response.incidentId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo("QUEUED");
        ArgumentCaptor<AnalysisRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(AnalysisRequestedEvent.class);
        verify(publisher).publishAnalysisRequested(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventId()).isNotNull();
        assertThat(eventCaptor.getValue().executionId()).isEqualTo(99L);
        assertThat(eventCaptor.getValue().incidentId()).isEqualTo(42L);
        assertThat(eventCaptor.getValue().title()).isEqualTo("Payment service latency");
        assertThat(eventCaptor.getValue().requestedAt()).isNotNull();
    }

    @Test
    void analyzeIncidentAsync_shouldFailExecutionWhenPublishingFails() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAsyncAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));
        AnalysisEventPublisher publisher = mock(AnalysisEventPublisher.class);
        AnalysisMessagingException failure = new AnalysisMessagingException(
                "Failed to publish analysis request",
                new RuntimeException("Kafka unavailable")
        );
        doThrow(failure).when(publisher).publishAnalysisRequested(any());
        AnalysisService service = service(
                persistenceService,
                mock(IncidentAnalyzerClient.class),
                publisher,
                2
        );

        assertThatThrownBy(() -> service.analyzeIncidentAsync(42L)).isSameAs(failure);

        verify(persistenceService).persistFailure(
                99L,
                "Failed to publish analysis request",
                AnalysisExecutionFailureType.MESSAGING_FAILURE
        );
    }

    @Test
    void analyzeIncident_shouldNotMarkCompletedExecutionFailedWhenObservabilityFails() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        RootCauseCandidateResponse bestCandidate = candidate(0.91d);
        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(REQUEST))
                .thenReturn(new AnalysisResponse(42L, List.of(bestCandidate)));
        MeterRegistry meterRegistry = failingMeterRegistry("completed");

        AnalysisResultResponse result = service(
                persistenceService,
                client,
                meterRegistry,
                2
        ).analyzeIncident(42L);

        assertThat(result.status()).isEqualTo("ROOT_CAUSE_IDENTIFIED");
        verify(persistenceService).completeAnalysis(99L, bestCandidate);
        verify(persistenceService, never()).persistFailure(any(), any(), any());
    }

    @Test
    void analyzeIncident_shouldNotMarkInconclusiveExecutionFailedWhenObservabilityFails() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        RootCauseCandidateResponse unknownCandidate = new RootCauseCandidateResponse(
                "UNKNOWN",
                0.05d,
                "Available evidence is insufficient.",
                List.of()
        );
        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(REQUEST))
                .thenReturn(new AnalysisResponse(42L, List.of(unknownCandidate)));
        MeterRegistry meterRegistry = failingMeterRegistry("inconclusive");

        AnalysisResultResponse result = service(
                persistenceService,
                client,
                meterRegistry,
                2
        ).analyzeIncident(42L);

        assertThat(result.status()).isEqualTo("INCONCLUSIVE");
        verify(persistenceService).markInconclusive(99L, 0.05d);
        verify(persistenceService, never()).persistFailure(any(), any(), any());
    }

    @Test
    void analyzeIncident_shouldKeepOriginalFailureWhenFailurePersistenceAlsoFails() {
        AnalysisPersistenceService persistenceService = mock(AnalysisPersistenceService.class);
        when(persistenceService.beginAnalysis(42L))
                .thenReturn(new AnalysisPreparation(99L, REQUEST));

        AnalyzerUnavailableException originalFailure = new AnalyzerUnavailableException(
                "Incident analyzer service is unavailable",
                AnalysisExecutionFailureType.CONNECTION_FAILURE,
                new RuntimeException()
        );
        RuntimeException persistenceFailure = new RuntimeException("Database unavailable");
        IncidentAnalyzerClient client = mock(IncidentAnalyzerClient.class);
        when(client.analyze(REQUEST)).thenThrow(originalFailure);
        doThrow(persistenceFailure)
                .when(persistenceService)
                .persistFailure(
                        99L,
                        originalFailure.getMessage(),
                        AnalysisExecutionFailureType.CONNECTION_FAILURE
                );

        assertThatThrownBy(() -> service(persistenceService, client, 1).analyzeIncident(42L))
                .isSameAs(originalFailure)
                .satisfies(ex -> assertThat(ex.getSuppressed()).containsExactly(persistenceFailure));
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
        return service(
                persistenceService,
                client,
                mock(AnalysisEventPublisher.class),
                maxAttempts
        );
    }

    private AnalysisService service(
            AnalysisPersistenceService persistenceService,
            IncidentAnalyzerClient client,
            AnalysisEventPublisher publisher,
            int maxAttempts
    ) {
        return new AnalysisService(
                persistenceService,
                client,
                publisher,
                new SimpleMeterRegistry(),
                maxAttempts,
                Duration.ZERO
        );
    }

    private AnalysisService service(
            AnalysisPersistenceService persistenceService,
            IncidentAnalyzerClient client,
            MeterRegistry meterRegistry,
            int maxAttempts
    ) {
        return new AnalysisService(
                persistenceService,
                client,
                mock(AnalysisEventPublisher.class),
                meterRegistry,
                maxAttempts,
                Duration.ZERO
        );
    }

    private MeterRegistry failingMeterRegistry(String outcome) {
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        when(meterRegistry.counter("incident.analysis.executions", "outcome", outcome))
                .thenThrow(new RuntimeException("Metrics backend unavailable"));
        return meterRegistry;
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
