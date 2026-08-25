package com.CemHarput.IncidentInvestigator.analysis.application;

import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;

public record AnalysisPreparation(Long executionId, AnalysisRequest request) {
}
