package com.CemHarput.IncidentInvestigator.agent.domain;

public enum AgentExecutionFailureType {
    RUNTIME_ERROR,
    TIMEOUT,
    STEP_LIMIT_EXCEEDED,
    CAPABILITY_FAILURE,
    INVALID_RESULT,
    MESSAGING_FAILURE
}
