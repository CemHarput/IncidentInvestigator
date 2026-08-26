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
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncAnalysisResultService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncAnalysisResultService.class);

    private final AnalysisExecutionRepository executionRepository;
    private final IncidentRepository incidentRepository;
    private final AnalysisResultEvaluator resultEvaluator;

    public AsyncAnalysisResultService(
            AnalysisExecutionRepository executionRepository,
            IncidentRepository incidentRepository,
            AnalysisResultEvaluator resultEvaluator
    ) {
        this.executionRepository = executionRepository;
        this.incidentRepository = incidentRepository;
        this.resultEvaluator = resultEvaluator;
    }

    @Transactional
    public void processCompleted(AnalysisCompletedEvent event) {
        validateCompletedEvent(event);
        AnalysisExecution execution = findExecutionForUpdate(event.executionId());

        if (ignoreDuplicate(execution, event.eventId(), event.incidentId())) {
            return;
        }

        AnalysisResultEvaluator.Evaluation evaluation = resultEvaluator.evaluate(
                event.incidentId(),
                new AnalysisResponse(event.incidentId(), event.candidates())
        );
        RootCauseCandidateResponse candidate = evaluation.candidate();

        if (evaluation.inconclusive()) {
            execution.markAsyncInconclusive(candidate.confidence(), event.eventId());
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
    }

    @Transactional
    public void processFailed(AnalysisFailedEvent event) {
        validateFailedEvent(event);
        AnalysisExecution execution = findExecutionForUpdate(event.executionId());

        if (ignoreDuplicate(execution, event.eventId(), event.incidentId())) {
            return;
        }

        execution.failAsync(
                event.failureReason(),
                parseFailureType(event.failureType()),
                event.eventId()
        );
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
