package com.CemHarput.IncidentInvestigator.analysis.exception;

public class AnalysisAlreadyRunningException extends RuntimeException {

    public AnalysisAlreadyRunningException(Long incidentId) {
        super("An analysis is already running for incident " + incidentId);
    }
}
