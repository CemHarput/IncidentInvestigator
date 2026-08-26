package com.CemHarput.IncidentInvestigator.analysis.api;

import com.CemHarput.IncidentInvestigator.analysis.application.AnalysisService;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AnalysisExecutionRepository analysisExecutionRepository;

    public AnalysisController(
            AnalysisService analysisService,
            AnalysisExecutionRepository analysisExecutionRepository
    ) {
        this.analysisService = analysisService;
        this.analysisExecutionRepository = analysisExecutionRepository;
    }

    @PostMapping("/incidents/{id}/analyze")
    public ResponseEntity<AnalysisResultResponse> analyze(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(analysisService.analyzeIncident(id));
    }

    @PostMapping("/incidents/{id}/analyze-async")
    public ResponseEntity<AsyncAnalysisResponse> analyzeAsync(
            @PathVariable Long id
    ) {
        AsyncAnalysisResponse response = analysisService.analyzeIncidentAsync(id);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/analyses/" + response.executionId()))
                .body(response);
    }

    @GetMapping("/incidents/{incidentId}/analyses")
    public ResponseEntity<List<AnalysisExecutionResponse>> getIncidentAnalyses(
            @PathVariable Long incidentId
    ) {
        List<AnalysisExecution> executions = analysisExecutionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
        List<AnalysisExecutionResponse> response = executions.stream()
                .map(AnalysisExecutionResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analyses/{executionId}")
    public ResponseEntity<AnalysisExecutionResponse> getAnalysis(
            @PathVariable Long executionId
    ) {
        return analysisExecutionRepository.findById(executionId)
                .map(execution -> ResponseEntity.ok(AnalysisExecutionResponse.from(execution)))
                .orElse(ResponseEntity.notFound().build());
    }
}
