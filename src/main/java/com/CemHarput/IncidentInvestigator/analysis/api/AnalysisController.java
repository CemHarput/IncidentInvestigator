package com.CemHarput.IncidentInvestigator.analysis.api;

import com.CemHarput.IncidentInvestigator.analysis.application.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<AnalysisResultResponse> analyze(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(analysisService.analyzeIncident(id));
    }
}
