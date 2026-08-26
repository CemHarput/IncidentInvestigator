package com.CemHarput.IncidentInvestigator.analysis.application;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisEvidence;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisAlreadyRunningException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisNotAllowedException;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.exception.IncidentNotFoundException;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisPersistenceService {

    private static final List<AnalysisExecutionStatus> ACTIVE_STATUSES = List.of(
            AnalysisExecutionStatus.CREATED,
            AnalysisExecutionStatus.QUEUED,
            AnalysisExecutionStatus.RUNNING
    );

    private final IncidentRepository incidentRepository;
    private final AnalysisExecutionRepository analysisExecutionRepository;

    public AnalysisPersistenceService(
            IncidentRepository incidentRepository,
            AnalysisExecutionRepository analysisExecutionRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.analysisExecutionRepository = analysisExecutionRepository;
    }

    @Transactional
    public AnalysisPreparation beginAnalysis(Long incidentId) {
        Incident incident = incidentRepository.findByIdForAnalysis(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        validateAnalysisAllowed(incident);

        if (analysisExecutionRepository
                .findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(incidentId, ACTIVE_STATUSES)
                .isPresent()) {
            throw new AnalysisAlreadyRunningException(incidentId);
        }

        AnalysisExecution execution = AnalysisExecution.create(incidentId);
        execution.start();
        execution = analysisExecutionRepository.save(execution);

        return new AnalysisPreparation(execution.getId(), toAnalysisRequest(incidentId, incident));
    }

    @Transactional
    public AnalysisPreparation beginAsyncAnalysis(Long incidentId) {
        Incident incident = incidentRepository.findByIdForAnalysis(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        validateAnalysisAllowed(incident);

        if (analysisExecutionRepository
                .findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(incidentId, ACTIVE_STATUSES)
                .isPresent()) {
            throw new AnalysisAlreadyRunningException(incidentId);
        }

        AnalysisExecution execution = AnalysisExecution.create(incidentId);
        execution.queue();
        execution = analysisExecutionRepository.save(execution);

        return new AnalysisPreparation(execution.getId(), toAnalysisRequest(incidentId, incident));
    }

    @Transactional
    public void incrementAttemptCount(Long executionId) {
        findExecution(executionId).incrementAttemptCount();
    }

    @Transactional
    public void completeAnalysis(Long executionId, RootCauseCandidateResponse candidate) {
        AnalysisExecution execution = findExecution(executionId);
        Incident incident = incidentRepository.findById(execution.getIncidentId())
                .orElseThrow(() -> new IncidentNotFoundException(execution.getIncidentId()));

        incident.identifyRootCause(new RootCause(
                candidate.explanation(),
                candidate.rootCause(),
                false
        ));
        execution.complete(candidate.rootCause(), candidate.confidence());
    }

    @Transactional
    public void markInconclusive(Long executionId, double confidence) {
        findExecution(executionId).markInconclusive(confidence);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistFailure(
            Long executionId,
            String reason,
            AnalysisExecutionFailureType failureType
    ) {
        findExecution(executionId).fail(reason, failureType);
    }

    private AnalysisExecution findExecution(Long executionId) {
        return analysisExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Analysis execution not found: " + executionId
                ));
    }

    private void validateAnalysisAllowed(Incident incident) {
        if (incident.getStatus() != IncidentStatus.IN_INVESTIGATION) {
            throw new AnalysisNotAllowedException(
                    "Only incidents under investigation can be analyzed"
            );
        }

        if (incident.getEvidence().isEmpty()) {
            throw new AnalysisNotAllowedException(
                    "Incident must contain evidence before analysis"
            );
        }

        if (incident.hasConfirmedRootCause()) {
            throw new AnalysisNotAllowedException(
                    "Incident already has a confirmed root cause"
            );
        }
    }

    private AnalysisRequest toAnalysisRequest(Long incidentId, Incident incident) {
        List<AnalysisEvidence> evidence = incident.getEvidence().stream()
                .map(item -> new AnalysisEvidence(
                        item.getType().name(),
                        item.getSource(),
                        item.getContent(),
                        item.getObservedAt()
                ))
                .toList();

        return new AnalysisRequest(
                incidentId,
                incident.getTitle(),
                incident.getIncidentType(),
                evidence
        );
    }
}
