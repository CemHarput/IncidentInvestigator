package com.CemHarput.IncidentInvestigator.agent.api;

import com.CemHarput.IncidentInvestigator.agent.application.AgentExecutionService;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionRepository;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionStepRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentExecutionController {

    private final AgentExecutionService executionService;
    private final AgentExecutionRepository executionRepository;
    private final AgentExecutionStepRepository stepRepository;

    public AgentExecutionController(
            AgentExecutionService executionService,
            AgentExecutionRepository executionRepository,
            AgentExecutionStepRepository stepRepository
    ) {
        this.executionService = executionService;
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
    }

    @PostMapping("/agents/{agentName}/executions")
    public ResponseEntity<AgentExecutionAcceptedResponse> createExecution(
            @PathVariable String agentName,
            @Valid @RequestBody CreateAgentExecutionRequest request
    ) {
        AgentExecutionAcceptedResponse response = executionService.createExecution(
                agentName,
                request.incidentId()
        );
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/agent-executions/" + response.executionId()))
                .body(response);
    }

    @GetMapping("/agent-executions/{executionId}")
    public ResponseEntity<AgentExecutionResponse> getExecution(@PathVariable Long executionId) {
        return executionRepository.findById(executionId)
                .map(execution -> ResponseEntity.ok(AgentExecutionResponse.from(execution)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/agent-executions/{executionId}/steps")
    public ResponseEntity<List<AgentExecutionStepResponse>> getExecutionSteps(
            @PathVariable Long executionId
    ) {
        if (!executionRepository.existsById(executionId)) {
            return ResponseEntity.notFound().build();
        }
        List<AgentExecutionStepResponse> response = stepRepository
                .findByExecutionIdOrderByStepNumberAsc(executionId)
                .stream()
                .map(AgentExecutionStepResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
