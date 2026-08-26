package com.CemHarput.IncidentInvestigator.analysis.messaging;

import com.CemHarput.IncidentInvestigator.analysis.application.AsyncAnalysisResultService;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisCompletedEvent;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisFailedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AnalysisResultConsumer {

    private final ObjectMapper objectMapper;
    private final AsyncAnalysisResultService resultService;

    public AnalysisResultConsumer(
            ObjectMapper objectMapper,
            AsyncAnalysisResultService resultService
    ) {
        this.objectMapper = objectMapper;
        this.resultService = resultService;
    }

    @KafkaListener(
            id = "analysis-completed-result-consumer",
            topics = "${analysis.kafka.completed-topic}",
            groupId = "${analysis.kafka.result-consumer-group}"
    )
    public void consumeCompleted(String payload) {
        resultService.processCompleted(readCompletedEvent(payload));
    }

    @KafkaListener(
            id = "analysis-failed-result-consumer",
            topics = "${analysis.kafka.failed-topic}",
            groupId = "${analysis.kafka.result-consumer-group}"
    )
    public void consumeFailed(String payload) {
        resultService.processFailed(readFailedEvent(payload));
    }

    private AnalysisCompletedEvent readCompletedEvent(String payload) {
        try {
            return objectMapper.readValue(payload, AnalysisCompletedEvent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid analysis completed event payload", ex);
        }
    }

    private AnalysisFailedEvent readFailedEvent(String payload) {
        try {
            return objectMapper.readValue(payload, AnalysisFailedEvent.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid analysis failed event payload", ex);
        }
    }
}
