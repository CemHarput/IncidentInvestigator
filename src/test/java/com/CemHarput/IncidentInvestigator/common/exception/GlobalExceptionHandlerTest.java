package com.CemHarput.IncidentInvestigator.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisAlreadyRunningException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerDownstreamException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisMessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void activeAnalysis_shouldReturnSemanticConflict() {
        ResponseEntity<ApiError> response = handler.handleAnalysisAlreadyRunning(
                new AnalysisAlreadyRunningException(42L)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ANALYSIS_ALREADY_RUNNING");
    }

    @Test
    void downstreamFailure_shouldReturnBadGateway() {
        ResponseEntity<ApiError> response = handler.handleAnalyzerDownstream(
                new AnalyzerDownstreamException("Downstream failed", 500, new RuntimeException())
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ANALYZER_DOWNSTREAM_ERROR");
    }

    @Test
    void messagingFailure_shouldReturnServiceUnavailable() {
        ResponseEntity<ApiError> response = handler.handleAnalysisMessaging(
                new AnalysisMessagingException("Kafka unavailable", new RuntimeException())
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ANALYSIS_MESSAGING_UNAVAILABLE");
    }
}
