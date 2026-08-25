package com.CemHarput.IncidentInvestigator.analysis.client;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisRequest;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisResponse;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerDownstreamException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerUnavailableException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerRequestException;
import com.CemHarput.IncidentInvestigator.analysis.exception.InvalidAnalyzerResponseException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
        } catch (HttpClientErrorException ex) {
            throw new InvalidAnalyzerRequestException(
                    "Incident analyzer rejected request with status " + ex.getStatusCode().value(),
                    ex
            );
        } catch (HttpServerErrorException ex) {
            throw new AnalyzerDownstreamException(
                    "Incident analyzer returned status " + ex.getStatusCode().value(),
                    ex.getStatusCode().value(),
                    ex
            );
        } catch (ResourceAccessException ex) {
            AnalysisExecutionFailureType failureType = isTimeout(ex)
                    ? AnalysisExecutionFailureType.TIMEOUT
                    : AnalysisExecutionFailureType.CONNECTION_FAILURE;
            String message = failureType == AnalysisExecutionFailureType.TIMEOUT
                    ? "Incident analyzer request timed out"
                    : "Incident analyzer service is unavailable";
            throw new AnalyzerUnavailableException(message, failureType, ex);
        } catch (RestClientException ex) {
            throw new InvalidAnalyzerResponseException(
                    "Incident analyzer returned an invalid response",
                    ex
            );
        }
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
