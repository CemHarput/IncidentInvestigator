package com.CemHarput.IncidentInvestigator.agent.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionCompletedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionFailedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionStepEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AgentExecutionEventContractTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldDeserializePythonStepEventContract() throws Exception {
        String payload = """
                {
                  "eventId": "3846772d-dc25-43f6-a68b-d100302f82f1",
                  "executionId": 99,
                  "stepNumber": 3,
                  "stepType": "OBSERVATION",
                  "capability": "log-analyzer",
                  "observationSummary": "Connection pool timeout signatures detected.",
                  "reasoningSummary": "Inspect metrics next.",
                  "occurredAt": "2026-08-30T11:01:00Z"
                }
                """;

        AgentExecutionStepEvent event = objectMapper.readValue(
                payload,
                AgentExecutionStepEvent.class
        );

        assertThat(event.stepNumber()).isEqualTo(3);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-08-30T11:01:00Z"));
    }

    @Test
    void completedEventShouldSerializeUtcTimestampAndNestedResult() throws Exception {
        AgentExecutionCompletedEvent event = new AgentExecutionCompletedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                new AgentResult(
                        "DATABASE_CONNECTION_POOL_EXHAUSTION",
                        0.91d,
                        "Pool saturation detected.",
                        List.of("connection timeout")
                ),
                7,
                Instant.parse("2026-08-30T11:02:00Z")
        );

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertThat(payload.get("completedAt").asText()).isEqualTo("2026-08-30T11:02:00Z");
        assertThat(payload.get("result").get("rootCause").asText())
                .isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
    }

    @Test
    void shouldDeserializeFailedEventContract() throws Exception {
        String payload = """
                {
                  "eventId": "3846772d-dc25-43f6-a68b-d100302f82f1",
                  "executionId": 99,
                  "agentName": "incident-root-cause-agent",
                  "failureType": "STEP_LIMIT_EXCEEDED",
                  "failureReason": "Maximum step count reached",
                  "completedSteps": 10,
                  "failedAt": "2026-08-30T11:03:00Z"
                }
                """;

        AgentExecutionFailedEvent event = objectMapper.readValue(
                payload,
                AgentExecutionFailedEvent.class
        );

        assertThat(event.failureType()).isEqualTo("STEP_LIMIT_EXCEEDED");
        assertThat(event.completedSteps()).isEqualTo(10);
    }
}
