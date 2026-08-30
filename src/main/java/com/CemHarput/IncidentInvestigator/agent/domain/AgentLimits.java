package com.CemHarput.IncidentInvestigator.agent.domain;

import java.time.Duration;
import java.util.Objects;

public record AgentLimits(int maxSteps, Duration timeout) {

    public AgentLimits {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("Agent maxSteps must be greater than zero");
        }
        Objects.requireNonNull(timeout, "Agent timeout is required");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Agent timeout must be greater than zero");
        }
    }
}
