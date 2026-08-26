package com.CemHarput.IncidentInvestigator.analysis.application;

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
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerResponseException;
import com.CemHarput.IncidentInvestigator.analysis.messaging.AnalysisEventPublisher;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);
    private final AnalysisPersistenceService persistenceService;
    private final IncidentAnalyzerClient analyzerClient;
    private final AnalysisEventPublisher eventPublisher;
    private final AnalysisResultEvaluator resultEvaluator;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final Duration retryBackoff;

    public AnalysisService(
            AnalysisPersistenceService persistenceService,
            IncidentAnalyzerClient analyzerClient,
            AnalysisEventPublisher eventPublisher,
            AnalysisResultEvaluator resultEvaluator,
            MeterRegistry meterRegistry,
            @Value("${incident-analyzer.retry.max-attempts:2}") int maxAttempts,
            @Value("${incident-analyzer.retry.backoff:100ms}") Duration retryBackoff
    ) {
        this.persistenceService = persistenceService;
        this.analyzerClient = analyzerClient;
        this.eventPublisher = eventPublisher;
        this.resultEvaluator = resultEvaluator;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.retryBackoff = retryBackoff;
    }

    public AsyncAnalysisResponse analyzeIncidentAsync(Long incidentId) {
        AnalysisPreparation preparation = persistenceService.beginAsyncAnalysis(incidentId);
        AnalysisRequest request = preparation.request();
        AnalysisRequestedEvent event = new AnalysisRequestedEvent(
                UUID.randomUUID(),
                preparation.executionId(),
                request.incidentId(),
                request.title(),
                request.incidentType(),
                request.evidence(),
                LocalDateTime.now()
        );

        try {
            eventPublisher.publishAnalysisRequested(event);
        } catch (RuntimeException ex) {
            AnalysisMessagingException failure = ex instanceof AnalysisMessagingException messagingException
                    ? messagingException
                    : new AnalysisMessagingException("Failed to publish analysis request", ex);
            persistFailurePreservingOriginal(
                    preparation.executionId(),
                    failure,
                    AnalysisExecutionFailureType.MESSAGING_FAILURE
            );
            throw failure;
        }

        return new AsyncAnalysisResponse(
                preparation.executionId(),
                request.incidentId(),
                "QUEUED"
        );
    }

    public AnalysisResultResponse analyzeIncident(Long incidentId) {
        AnalysisPreparation preparation = persistenceService.beginAnalysis(incidentId);
        Long executionId = preparation.executionId();
        long startedAt = System.nanoTime();
        AttemptTracker attemptTracker = new AttemptTracker();
        RootCauseCandidateResponse bestCandidate;
        boolean inconclusive;

        try {
            AnalysisResponse response = analyzeWithRetry(preparation, attemptTracker);
            AnalysisResultEvaluator.Evaluation evaluation =
                    resultEvaluator.evaluate(incidentId, response);
            bestCandidate = evaluation.candidate();
            inconclusive = evaluation.inconclusive();

            if (inconclusive) {
                persistenceService.markInconclusive(executionId, bestCandidate.confidence());
            } else {
                persistenceService.completeAnalysis(executionId, bestCandidate);
            }
        } catch (RuntimeException ex) {
            AnalysisExecutionFailureType failureType = classifyFailure(ex);
            persistFailurePreservingOriginal(executionId, ex, failureType);
            observeOutcomeBestEffort(
                    "failed",
                    executionId,
                    incidentId,
                    "FAILED",
                    attemptTracker.count,
                    failureType,
                    startedAt
            );
            throw ex;
        }

        if (inconclusive) {
            observeOutcomeBestEffort(
                    "inconclusive",
                    executionId,
                    incidentId,
                    "INCONCLUSIVE",
                    attemptTracker.count,
                    null,
                    startedAt
            );
            return AnalysisResultResponse.inconclusive(
                    executionId,
                    incidentId,
                    bestCandidate
            );
        }

        observeOutcomeBestEffort(
                "completed",
                executionId,
                incidentId,
                "COMPLETED",
                attemptTracker.count,
                null,
                startedAt
        );
        return AnalysisResultResponse.identified(
                executionId,
                incidentId,
                bestCandidate
        );
    }

    private AnalysisResponse analyzeWithRetry(
            AnalysisPreparation preparation,
            AttemptTracker attemptTracker
    ) {
        AnalysisRequest request = preparation.request();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attemptTracker.count = attempt;
            try {
                return analyzerClient.analyze(request);
            } catch (RuntimeException ex) {
                if (!isRetryable(ex) || attempt == maxAttempts) {
                    throw ex;
        }

                LOGGER.warn(
                        "Retrying incident analysis executionId={} incidentId={} nextAttempt={} failureType={}",
                        preparation.executionId(),
                        request.incidentId(),
                        attempt + 1,
                        classifyFailure(ex)
                );
                waitBeforeRetry();
                persistenceService.incrementAttemptCount(preparation.executionId());
        }
    }

        throw new IllegalStateException("Analysis retry loop ended unexpectedly");
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(retryBackoff);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Analysis retry was interrupted", ex);
        }
        }

    private boolean isRetryable(RuntimeException ex) {
        if (ex instanceof AnalyzerUnavailableException) {
            return true;
        }
        return ex instanceof AnalyzerDownstreamException downstream && downstream.isRetryable();
    }

    private AnalysisExecutionFailureType classifyFailure(RuntimeException ex) {
        if (ex instanceof AnalyzerUnavailableException unavailable) {
            return unavailable.getFailureType();
        }
        if (ex instanceof InvalidAnalyzerRequestException) {
            return AnalysisExecutionFailureType.DOWNSTREAM_4XX;
        }
        if (ex instanceof AnalyzerDownstreamException) {
            return AnalysisExecutionFailureType.DOWNSTREAM_5XX;
        }
        if (ex instanceof InvalidAnalyzerResponseException) {
            return AnalysisExecutionFailureType.INVALID_RESPONSE;
        }
        return AnalysisExecutionFailureType.INTERNAL_ERROR;
    }

    private String failureReason(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private void persistFailurePreservingOriginal(
            Long executionId,
            RuntimeException originalException,
            AnalysisExecutionFailureType failureType
    ) {
        try {
            persistenceService.persistFailure(
                    executionId,
                    failureReason(originalException),
                    failureType
            );
        } catch (RuntimeException persistenceException) {
            if (persistenceException != originalException) {
                originalException.addSuppressed(persistenceException);
            }
        }
    }

    private void observeOutcomeBestEffort(
            String outcome,
            Long executionId,
            Long incidentId,
            String status,
            int attemptCount,
            AnalysisExecutionFailureType failureType,
            long startedAt
    ) {
        try {
            recordOutcome(outcome, startedAt);
            logFinished(
                    executionId,
                    incidentId,
                    status,
                    attemptCount,
                    failureType,
                    startedAt
            );
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "Failed to record analysis observability executionId={} status={}",
                    executionId,
                    status,
                    ex
            );
        }
    }

    private void recordOutcome(String outcome, long startedAt) {
        meterRegistry.counter("incident.analysis.executions", "outcome", outcome).increment();
        meterRegistry.timer("incident.analysis.duration", "outcome", outcome)
                .record(Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private void logFinished(
            Long executionId,
            Long incidentId,
            String status,
            int attemptCount,
            AnalysisExecutionFailureType failureType,
            long startedAt
    ) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        LOGGER.info(
                "Incident analysis finished executionId={} incidentId={} status={} attemptCount={} durationMs={} failureType={}",
                executionId,
                incidentId,
                status,
                attemptCount,
                durationMs,
                failureType
        );
    }

    private static final class AttemptTracker {
        private int count;
    }

}
