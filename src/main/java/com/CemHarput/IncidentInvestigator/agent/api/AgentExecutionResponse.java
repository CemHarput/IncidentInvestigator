package com.CemHarput.IncidentInvestigator.agent.api;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecution;
import java.time.LocalDateTime;

public record AgentExecutionResponse(
        Long id,
        String agentName,
        String agentVersion,
        String status,
        Long incidentId,
        Integer currentStep,
        Integer maxSteps,
        Long timeoutSeconds,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMs,
        String failureType,
        String failureReason,
        String resultSummary
) {

    public static AgentExecutionResponse from(AgentExecution execution) {
        return new AgentExecutionResponse(
                execution.getId(),
                execution.getAgentName(),
                execution.getAgentVersion(),
                execution.getStatus().name(),
                execution.getIncidentId(),
                execution.getCurrentStep(),
                execution.getMaxSteps(),
                execution.getTimeoutSeconds(),
                execution.getCreatedAt(),
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getDurationMs(),
                execution.getFailureType() == null ? null : execution.getFailureType().name(),
                execution.getFailureReason(),
                execution.getResultSummary()
        );
    }
}
