package com.CemHarput.IncidentInvestigator.analysis.domain;

public enum AnalysisExecutionFailureType {
    TIMEOUT,
    CONNECTION_FAILURE,
    DOWNSTREAM_4XX,
    DOWNSTREAM_5XX,
    INVALID_RESPONSE,
    MESSAGING_FAILURE,
    INTERNAL_ERROR
}
