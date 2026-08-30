package com.CemHarput.IncidentInvestigator.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.agent.api.AgentExecutionAcceptedResponse;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionMessagingException;
import com.CemHarput.IncidentInvestigator.agent.messaging.AgentExecutionEventPublisher;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionRequestedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentInput;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentLimitsContract;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentExecutionServiceTest {

    @Test
    void createExecution_shouldPublishPreparedEventAndReturnQueuedResponse() {
        AgentExecutionPersistenceService persistence = mock(AgentExecutionPersistenceService.class);
        AgentExecutionRequestedEvent event = event();
        when(persistence.prepare(
                org.mockito.ArgumentMatchers.eq("incident-root-cause-agent"),
                org.mockito.ArgumentMatchers.eq(42L),
                any(UUID.class)
        )).thenReturn(new AgentExecutionPreparation(event));
        AgentExecutionEventPublisher publisher = mock(AgentExecutionEventPublisher.class);
        AgentExecutionService service = new AgentExecutionService(persistence, publisher);

        AgentExecutionAcceptedResponse response = service.createExecution(
                "incident-root-cause-agent",
                42L
        );

        assertThat(response).isEqualTo(new AgentExecutionAcceptedResponse(
                99L,
                "incident-root-cause-agent",
                "QUEUED"
        ));
        verify(publisher).publishRequested(event);
    }

    @Test
    void createExecution_shouldMarkExecutionFailedWhenPublishingFails() {
        AgentExecutionPersistenceService persistence = mock(AgentExecutionPersistenceService.class);
        AgentExecutionRequestedEvent event = event();
        when(persistence.prepare(any(), any(), any()))
                .thenReturn(new AgentExecutionPreparation(event));
        AgentExecutionEventPublisher publisher = mock(AgentExecutionEventPublisher.class);
        AgentExecutionMessagingException failure = new AgentExecutionMessagingException(
                "Failed to publish agent execution request",
                new RuntimeException("Kafka unavailable")
        );
        doThrow(failure).when(publisher).publishRequested(event);
        AgentExecutionService service = new AgentExecutionService(persistence, publisher);

        assertThatThrownBy(() -> service.createExecution("incident-root-cause-agent", 42L))
                .isSameAs(failure);

        verify(persistence).persistMessagingFailure(
                99L,
                "Failed to publish agent execution request"
        );
    }

    private AgentExecutionRequestedEvent event() {
        return new AgentExecutionRequestedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                "1.0",
                new AgentLimitsContract(10, 60),
                new AgentInput(42L, "Latency", "LATENCY", List.of()),
                Instant.now()
        );
    }
}
