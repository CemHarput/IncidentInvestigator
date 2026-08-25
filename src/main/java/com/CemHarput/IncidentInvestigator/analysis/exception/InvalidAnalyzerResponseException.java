package com.CemHarput.IncidentInvestigator.analysis.exception;

public class InvalidAnalyzerResponseException extends RuntimeException {

    public InvalidAnalyzerResponseException(String message) {
        super(message);
    }

    public InvalidAnalyzerResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
