package com.CemHarput.IncidentInvestigator.analysis.exception;

public class AnalyzerDownstreamException extends RuntimeException {

    private final int statusCode;

    public AnalyzerDownstreamException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public boolean isRetryable() {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }
}
