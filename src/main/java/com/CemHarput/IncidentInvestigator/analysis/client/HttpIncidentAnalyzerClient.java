package com.CemHarput.IncidentInvestigator.analysis.client;

import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpIncidentAnalyzerClient implements IncidentAnalyzerClient {

    private final RestClient restClient;

    public HttpIncidentAnalyzerClient(
            RestClient.Builder builder,
            @Value("${incident-analyzer.base-url}") String baseUrl
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public AnalysisResponse analyze(AnalysisRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/analyze")
                    .body(request)
                    .retrieve()
                    .body(AnalysisResponse.class);
        } catch (RestClientException ex) {
            throw new AnalyzerUnavailableException(
                    "Incident analyzer service is unavailable",
                    ex
            );
        }
    }
}
