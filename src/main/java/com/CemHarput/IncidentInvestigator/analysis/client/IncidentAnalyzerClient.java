package com.CemHarput.IncidentInvestigator.analysis.client;

import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;

public interface IncidentAnalyzerClient {

    AnalysisResponse analyze(AnalysisRequest request);
}
