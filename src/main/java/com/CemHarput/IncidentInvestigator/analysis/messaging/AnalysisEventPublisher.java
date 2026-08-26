package com.CemHarput.IncidentInvestigator.analysis.messaging;

import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;

public interface AnalysisEventPublisher {

    void publishAnalysisRequested(AnalysisRequestedEvent event);
}
