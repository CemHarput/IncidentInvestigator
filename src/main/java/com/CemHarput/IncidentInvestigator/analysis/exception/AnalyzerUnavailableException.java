package com.CemHarput.IncidentInvestigator.analysis.exception;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;

public class AnalyzerUnavailableException extends RuntimeException {

    private final AnalysisExecutionFailureType failureType;

    public AnalyzerUnavailableException(String message, Throwable cause) {
        this(message, AnalysisExecutionFailureType.CONNECTION_FAILURE, cause);
    }

    public AnalyzerUnavailableException(
            String message,
            AnalysisExecutionFailureType failureType,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
    }

    public AnalysisExecutionFailureType getFailureType() {
        return failureType;
    }
}
