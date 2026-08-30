package com.CemHarput.IncidentInvestigator.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.agent.application.AgentExecutionService;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionRepository;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionStepRepository;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AgentExecutionControllerTest {

    @Test
    void createExecution_shouldReturnAcceptedAndCanonicalLocation() {
        AgentExecutionService service = mock(AgentExecutionService.class);
        when(service.createExecution("incident-root-cause-agent", 42L))
                .thenReturn(new AgentExecutionAcceptedResponse(
                        99L,
                        "incident-root-cause-agent",
                        "QUEUED"
                ));
        AgentExecutionController controller = new AgentExecutionController(
                service,
                mock(AgentExecutionRepository.class),
                mock(AgentExecutionStepRepository.class)
        );

        ResponseEntity<AgentExecutionAcceptedResponse> response = controller.createExecution(
                "incident-root-cause-agent",
                new CreateAgentExecutionRequest(42L)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/api/v1/agent-executions/99"));
        assertThat(response.getBody().status()).isEqualTo("QUEUED");
    }

    @Test
    void getExecutionSteps_shouldReturnNotFoundForUnknownExecution() {
        AgentExecutionRepository executionRepository = mock(AgentExecutionRepository.class);
        when(executionRepository.existsById(99L)).thenReturn(false);
        AgentExecutionController controller = new AgentExecutionController(
                mock(AgentExecutionService.class),
                executionRepository,
                mock(AgentExecutionStepRepository.class)
        );

        assertThat(controller.getExecutionSteps(99L).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
