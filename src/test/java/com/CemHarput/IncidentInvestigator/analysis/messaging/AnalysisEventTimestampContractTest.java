package com.CemHarput.IncidentInvestigator.analysis.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisCompletedEvent;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisRequestedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AnalysisEventTimestampContractTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void requestedAtShouldSerializeAsUtcInstant() throws Exception {
        AnalysisRequestedEvent event = new AnalysisRequestedEvent(
                UUID.randomUUID(),
                99L,
                42L,
                "Payment service latency",
                "LATENCY",
                List.of(),
                Instant.parse("2026-08-26T11:00:10Z")
        );

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertThat(payload.get("requestedAt").asText())
                .isEqualTo("2026-08-26T11:00:10Z");
    }

    @Test
    void completedAtShouldDeserializeFromPythonUtcTimestamp() throws Exception {
        String payload = """
                {
                  "eventId": "3846772d-dc25-43f6-a68b-d100302f82f1",
                  "executionId": 99,
                  "incidentId": 42,
                  "candidates": [],
                  "completedAt": "2026-08-26T11:01:00Z"
                }
                """;

        AnalysisCompletedEvent event = objectMapper.readValue(
                payload,
                AnalysisCompletedEvent.class
        );

        assertThat(event.completedAt())
                .isEqualTo(Instant.parse("2026-08-26T11:01:00Z"));
    }
}
