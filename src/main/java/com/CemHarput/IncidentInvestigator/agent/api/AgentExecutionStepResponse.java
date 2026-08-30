package com.CemHarput.IncidentInvestigator.agent.api;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStep;
import java.time.LocalDateTime;

public record AgentExecutionStepResponse(
        Integer stepNumber,
        String type,
        String capability,
        String inputSummary,
        String observationSummary,
        String reasoningSummary,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMs,
        String status,
        String failureReason
) {

    public static AgentExecutionStepResponse from(AgentExecutionStep step) {
        return new AgentExecutionStepResponse(
                step.getStepNumber(),
                step.getStepType().name(),
                step.getCapability(),
                step.getInputSummary(),
                step.getObservationSummary(),
                step.getReasoningSummary(),
                step.getStartedAt(),
                step.getCompletedAt(),
                step.getDurationMs(),
                step.getStatus().name(),
                step.getFailureReason()
        );
    }
}
