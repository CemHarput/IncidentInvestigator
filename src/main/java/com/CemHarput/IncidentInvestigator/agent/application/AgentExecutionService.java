package com.CemHarput.IncidentInvestigator.agent.application;

import com.CemHarput.IncidentInvestigator.agent.api.AgentExecutionAcceptedResponse;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionMessagingException;
import com.CemHarput.IncidentInvestigator.agent.messaging.AgentExecutionEventPublisher;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionRequestedEvent;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentExecutionService {

    private final AgentExecutionPersistenceService persistenceService;
    private final AgentExecutionEventPublisher eventPublisher;

    public AgentExecutionService(
            AgentExecutionPersistenceService persistenceService,
            AgentExecutionEventPublisher eventPublisher
    ) {
        this.persistenceService = persistenceService;
        this.eventPublisher = eventPublisher;
    }

    public AgentExecutionAcceptedResponse createExecution(String agentName, Long incidentId) {
        AgentExecutionPreparation preparation = persistenceService.prepare(
                agentName,
                incidentId,
                UUID.randomUUID()
        );
        AgentExecutionRequestedEvent event = preparation.event();

        try {
            eventPublisher.publishRequested(event);
        } catch (RuntimeException ex) {
            AgentExecutionMessagingException failure = ex instanceof AgentExecutionMessagingException messaging
                    ? messaging
                    : new AgentExecutionMessagingException(
                            "Failed to publish agent execution request",
                            ex
                    );
            persistFailurePreservingOriginal(event.executionId(), failure);
            throw failure;
        }

        return new AgentExecutionAcceptedResponse(
                event.executionId(),
                event.agentName(),
                "QUEUED"
        );
    }

    private void persistFailurePreservingOriginal(
            Long executionId,
            AgentExecutionMessagingException original
    ) {
        try {
            persistenceService.persistMessagingFailure(executionId, original.getMessage());
        } catch (RuntimeException persistenceFailure) {
            if (persistenceFailure != original) {
                original.addSuppressed(persistenceFailure);
            }
        }
    }
}
