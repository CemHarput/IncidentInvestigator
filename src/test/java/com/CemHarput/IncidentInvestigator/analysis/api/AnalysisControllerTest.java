package com.CemHarput.IncidentInvestigator.analysis.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.application.AnalysisService;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AnalysisControllerTest {

    @Test
    void analyzeAsync_shouldReturnAcceptedResponseAndExecutionLocation() {
        AnalysisService analysisService = mock(AnalysisService.class);
        when(analysisService.analyzeIncidentAsync(42L))
                .thenReturn(new AsyncAnalysisResponse(99L, 42L, "QUEUED"));
        AnalysisController controller = new AnalysisController(
                analysisService,
                mock(AnalysisExecutionRepository.class)
        );

        ResponseEntity<AsyncAnalysisResponse> response = controller.analyzeAsync(42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/api/v1/analyses/99"));
        assertThat(response.getBody()).isEqualTo(new AsyncAnalysisResponse(99L, 42L, "QUEUED"));
    }
}
