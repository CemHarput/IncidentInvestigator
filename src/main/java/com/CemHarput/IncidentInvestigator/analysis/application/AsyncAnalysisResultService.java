package com.CemHarput.IncidentInvestigator.analysis.application;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisCompletedEvent;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisFailedEvent;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.exception.IncidentNotFoundException;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncAnalysisResultService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncAnalysisResultService.class);

    private final AnalysisExecutionRepository executionRepository;
    private final IncidentRepository incidentRepository;
    private final AnalysisResultEvaluator resultEvaluator;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public AsyncAnalysisResultService(
            AnalysisExecutionRepository executionRepository,
            IncidentRepository incidentRepository,
            AnalysisResultEvaluator resultEvaluator,
            MeterRegistry meterRegistry,
            Tracer tracer
    ) {
        this.executionRepository = executionRepository;
        this.incidentRepository = incidentRepository;
        this.resultEvaluator = resultEvaluator;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Transactional
    public void processCompleted(AnalysisCompletedEvent event) {
        Span span = persistResultSpan(event.executionId(), event.incidentId());
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            validateCompletedEvent(event);
            AnalysisExecution execution = findExecutionForUpdate(event.executionId());

            if (ignoreDuplicate(execution, event.eventId(), event.incidentId())) {
                span.tag("analysis.final.status", execution.getStatus().name());
                return;
            }

            AnalysisResultEvaluator.Evaluation evaluation = resultEvaluator.evaluate(
                    event.incidentId(),
                    new AnalysisResponse(event.incidentId(), event.candidates())
            );
            RootCauseCandidateResponse candidate = evaluation.candidate();

            if (evaluation.inconclusive()) {
                execution.markAsyncInconclusive(candidate.confidence(), event.eventId());
                span.tag("analysis.final.status", AnalysisExecutionStatus.INCONCLUSIVE.name());
                recordAsyncOutcomeBestEffort(
                        "incident.analysis.async.inconclusive.total",
                        execution
                );
                logFinished(execution);
                return;
            }

            Incident incident = incidentRepository.findByIdForAnalysis(event.incidentId())
                    .orElseThrow(() -> new IncidentNotFoundException(event.incidentId()));
            incident.identifyRootCause(new RootCause(
                    candidate.explanation(),
                    candidate.rootCause(),
                    false
            ));
            execution.completeAsync(
                    candidate.rootCause(),
                    candidate.confidence(),
                    event.eventId()
            );
            span.tag("analysis.final.status", AnalysisExecutionStatus.COMPLETED.name());
            recordAsyncOutcomeBestEffort("incident.analysis.async.completed.total", execution);
            logFinished(execution);
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    @Transactional
    public void processFailed(AnalysisFailedEvent event) {
        Span span = persistResultSpan(event.executionId(), event.incidentId());
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            validateFailedEvent(event);
            AnalysisExecution execution = findExecutionForUpdate(event.executionId());

            if (ignoreDuplicate(execution, event.eventId(), event.incidentId())) {
                span.tag("analysis.final.status", execution.getStatus().name());
                return;
            }

            AnalysisExecutionFailureType failureType = parseFailureType(event.failureType());
            execution.failAsync(
                    event.failureReason(),
                    failureType,
                    event.eventId()
            );
            span.tag("analysis.final.status", AnalysisExecutionStatus.FAILED.name());
            span.tag("analysis.failure.type", failureType.name());
            recordAsyncOutcomeBestEffort("incident.analysis.async.failed.total", execution);
            logFinished(execution);
        } catch (RuntimeException ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    private Span persistResultSpan(Long executionId, Long incidentId) {
        Span span = tracer.nextSpan().name("analysis.execution.persist-result").start();
        if (executionId != null) {
            span.tag("analysis.execution.id", executionId.toString());
        }
        if (incidentId != null) {
            span.tag("incident.id", incidentId.toString());
        }
        return span;
    }

    private void recordAsyncOutcomeBestEffort(String counterName, AnalysisExecution execution) {
        try {
            meterRegistry.counter(counterName).increment();
            if (execution.getDurationMs() != null) {
                meterRegistry.timer("incident.analysis.async.end.to.end.duration")
                        .record(Duration.ofMillis(execution.getDurationMs()));
            }
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "Failed to record async analysis metrics executionId={} status={}",
                    execution.getId(),
                    execution.getStatus(),
                    ex
            );
        }
    }

    private void logFinished(AnalysisExecution execution) {
        try (MDC.MDCCloseable executionId = MDC.putCloseable(
                "executionId",
                String.valueOf(execution.getId())
        ); MDC.MDCCloseable incidentId = MDC.putCloseable(
                "incidentId",
                String.valueOf(execution.getIncidentId())
        )) {
            LOGGER.info(
                    "Async incident analysis finished executionId={} incidentId={} status={} durationMs={} failureType={}",
                    execution.getId(),
                    execution.getIncidentId(),
                    execution.getStatus(),
                    execution.getDurationMs(),
                    execution.getFailureType()
            );
        }
    }

    private AnalysisExecution findExecutionForUpdate(Long executionId) {
        return executionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Analysis execution not found: " + executionId
                ));
    }

    private boolean ignoreDuplicate(
            AnalysisExecution execution,
            UUID eventId,
            Long incidentId
    ) {
        if (!execution.getIncidentId().equals(incidentId)) {
            throw new IllegalArgumentException(
                    "Result event incidentId does not match analysis execution"
            );
        }

        if (execution.hasProcessedResult(eventId)) {
            LOGGER.info(
                    "Ignoring duplicate analysis result eventId={} executionId={}",
                    eventId,
                    execution.getId()
            );
            return true;
        }

        if (execution.getResultEventId() != null
                || execution.getStatus() != AnalysisExecutionStatus.QUEUED) {
            throw new IllegalStateException(
                    "Analysis execution already has a different result: " + execution.getId()
            );
        }

        return false;
    }

    private AnalysisExecutionFailureType parseFailureType(String failureType) {
        try {
            return AnalysisExecutionFailureType.valueOf(
                    failureType.trim().toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Unsupported analysis failureType: " + failureType,
                    ex
            );
        }
    }

    private void validateCompletedEvent(AnalysisCompletedEvent event) {
        if (event == null
                || event.eventId() == null
                || event.executionId() == null
                || event.incidentId() == null
                || event.completedAt() == null) {
            throw new IllegalArgumentException("Invalid analysis completed event");
        }
    }

    private void validateFailedEvent(AnalysisFailedEvent event) {
        if (event == null
                || event.eventId() == null
                || event.executionId() == null
                || event.incidentId() == null
                || event.failureType() == null
                || event.failureType().isBlank()
                || event.failureReason() == null
                || event.failureReason().isBlank()
                || event.failedAt() == null) {
            throw new IllegalArgumentException("Invalid analysis failed event");
        }
    }
}
