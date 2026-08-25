package com.CemHarput.IncidentInvestigator.analysis.application;

import com.CemHarput.IncidentInvestigator.analysis.api.AnalysisResultResponse;
import com.CemHarput.IncidentInvestigator.analysis.client.IncidentAnalyzerClient;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerDownstreamException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerUnavailableException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerRequestException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerResponseException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);
    private static final double MIN_CONFIDENCE = 0.60d;

    private final AnalysisPersistenceService persistenceService;
    private final IncidentAnalyzerClient analyzerClient;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final Duration retryBackoff;

    public AnalysisService(
            AnalysisPersistenceService persistenceService,
            IncidentAnalyzerClient analyzerClient,
            MeterRegistry meterRegistry,
            @Value("${incident-analyzer.retry.max-attempts:2}") int maxAttempts,
            @Value("${incident-analyzer.retry.backoff:100ms}") Duration retryBackoff
    ) {
        this.persistenceService = persistenceService;
        this.analyzerClient = analyzerClient;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.retryBackoff = retryBackoff;
    }

    public AnalysisResultResponse analyzeIncident(Long incidentId) {
        AnalysisPreparation preparation = persistenceService.beginAnalysis(incidentId);
        Long executionId = preparation.executionId();
        long startedAt = System.nanoTime();
        AttemptTracker attemptTracker = new AttemptTracker();

        try {
            AnalysisResponse response = analyzeWithRetry(preparation, attemptTracker);
            validateResponse(incidentId, response);

            RootCauseCandidateResponse bestCandidate = selectBestCandidate(response);

            if (isInconclusive(bestCandidate)) {
                persistenceService.markInconclusive(executionId, bestCandidate.confidence());
                recordOutcome("inconclusive", startedAt);
                logFinished(
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

            persistenceService.completeAnalysis(executionId, bestCandidate);
            recordOutcome("completed", startedAt);
            logFinished(
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
        } catch (RuntimeException ex) {
            AnalysisExecutionFailureType failureType = classifyFailure(ex);
            persistenceService.persistFailure(executionId, failureReason(ex), failureType);
            recordOutcome("failed", startedAt);
            logFinished(
                    executionId,
                    incidentId,
                    "FAILED",
                    attemptTracker.count,
                    failureType,
                    startedAt
            );
            throw ex;
        }
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

    private void validateResponse(Long expectedIncidentId, AnalysisResponse response) {
        if (response == null) {
            throw new InvalidAnalyzerResponseException("Analyzer returned an empty response");
        }

        if (!expectedIncidentId.equals(response.incidentId())) {
            throw new InvalidAnalyzerResponseException(
                    "Analyzer response incidentId does not match request"
            );
        }

        if (response.candidates() == null || response.candidates().isEmpty()) {
            throw new InvalidAnalyzerResponseException(
                    "Analyzer returned no root cause candidates"
            );
        }

        for (RootCauseCandidateResponse candidate : response.candidates()) {
            if (candidate == null) {
                throw new InvalidAnalyzerResponseException(
                        "Analyzer returned a null candidate"
                );
            }

            if (candidate.rootCause() == null || candidate.rootCause().isBlank()) {
                throw new InvalidAnalyzerResponseException(
                        "Analyzer returned a candidate with a blank root cause"
                );
            }

            if (candidate.confidence() < 0 || candidate.confidence() > 1.0d) {
                throw new InvalidAnalyzerResponseException(
                        "Analyzer returned a candidate with an invalid confidence value"
                );
            }
        }
    }

    private RootCauseCandidateResponse selectBestCandidate(AnalysisResponse response) {
        return response.candidates().stream()
                .max(Comparator.comparingDouble(RootCauseCandidateResponse::confidence))
                .orElseThrow(() -> new InvalidAnalyzerResponseException(
                        "Analyzer returned no root cause candidates"
                ));
    }

    private boolean isInconclusive(RootCauseCandidateResponse candidate) {
        if (candidate == null) {
            return true;
        }
        if ("UNKNOWN".equalsIgnoreCase(candidate.rootCause())) {
            return true;
        }
        return candidate.confidence() < MIN_CONFIDENCE;
    }
}
