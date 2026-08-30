package com.CemHarput.IncidentInvestigator.agent.domain;

public enum AgentExecutionStatus {
    CREATED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    STEP_LIMIT_EXCEEDED
}
