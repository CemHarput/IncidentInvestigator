package com.CemHarput.IncidentInvestigator.analysis.application;

import com.CemHarput.IncidentInvestigator.analysis.api.AnalysisResultResponse;
import com.CemHarput.IncidentInvestigator.analysis.client.IncidentAnalyzerClient;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisEvidence;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisNotAllowedException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerResponseException;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.exception.IncidentNotFoundException;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnalysisService {

    private static final double MIN_CONFIDENCE = 0.60d;

    private final IncidentRepository incidentRepository;
    private final AnalysisExecutionRepository analysisExecutionRepository;
    private final IncidentAnalyzerClient analyzerClient;

    public AnalysisService(
            IncidentRepository incidentRepository,
            IncidentAnalyzerClient analyzerClient
    ) {
        this(incidentRepository, null, analyzerClient);
    }

    @Autowired
    public AnalysisService(
            IncidentRepository incidentRepository,
            AnalysisExecutionRepository analysisExecutionRepository,
            IncidentAnalyzerClient analyzerClient
    ) {
        this.incidentRepository = incidentRepository;
        this.analysisExecutionRepository = analysisExecutionRepository;
        this.analyzerClient = analyzerClient;
    }

    public AnalysisResultResponse analyzeIncident(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        validateAnalysisAllowed(incident);

        AnalysisExecution execution = createExecution(incidentId);
        execution.start();

        try {
            AnalysisRequest request = toAnalysisRequest(incident);
            AnalysisResponse response = analyzerClient.analyze(request);
            validateResponse(incidentId, response);

            RootCauseCandidateResponse bestCandidate = selectBestCandidate(response);

            if (isInconclusive(bestCandidate)) {
                execution.markInconclusive(bestCandidate.confidence());
                return AnalysisResultResponse.inconclusive(
                        execution.getId(),
                        incidentId,
                        bestCandidate
                );
            }

            incident.identifyRootCause(new RootCause(
                    bestCandidate.explanation(),
                    bestCandidate.rootCause(),
                    false
            ));

            execution.complete(bestCandidate.rootCause(), bestCandidate.confidence());

            return AnalysisResultResponse.identified(
                    execution.getId(),
                    incidentId,
                    bestCandidate
            );
        } catch (RuntimeException ex) {
            if (execution != null) {
                execution.fail(ex.getMessage());
            }
            throw ex;
        }
    }

    private AnalysisExecution createExecution(Long incidentId) {
        if (analysisExecutionRepository == null) {
            return AnalysisExecution.create(incidentId);
        }
        return analysisExecutionRepository.save(AnalysisExecution.create(incidentId));
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
    }

    private AnalysisRequest toAnalysisRequest(Incident incident) {
        List<AnalysisEvidence> evidence = incident.getEvidence().stream()
                .map(item -> new AnalysisEvidence(
                        item.getType().name(),
                        item.getSource(),
                        item.getContent(),
                        item.getObservedAt()
                ))
                .toList();

        return new AnalysisRequest(
                incident.getId(),
                incident.getTitle(),
                incident.getIncidentType(),
                evidence
        );
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
