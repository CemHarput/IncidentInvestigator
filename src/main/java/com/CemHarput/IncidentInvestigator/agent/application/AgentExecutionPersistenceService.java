package com.CemHarput.IncidentInvestigator.agent.application;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentDefinition;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecution;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionFailureType;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStatus;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionAlreadyRunningException;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionNotAllowedException;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionRepository;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionRequestedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentInput;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentLimitsContract;
import com.CemHarput.IncidentInvestigator.analysis.dto.AnalysisEvidence;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.IncidentStatus;
import com.CemHarput.IncidentInvestigator.incident.exception.IncidentNotFoundException;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionPersistenceService {

    private static final List<AgentExecutionStatus> ACTIVE_STATUSES = List.of(
            AgentExecutionStatus.CREATED,
            AgentExecutionStatus.QUEUED,
            AgentExecutionStatus.RUNNING
    );

    private final AgentDefinitionRegistry definitionRegistry;
    private final IncidentRepository incidentRepository;
    private final AgentExecutionRepository executionRepository;

    public AgentExecutionPersistenceService(
            AgentDefinitionRegistry definitionRegistry,
            IncidentRepository incidentRepository,
            AgentExecutionRepository executionRepository
    ) {
        this.definitionRegistry = definitionRegistry;
        this.incidentRepository = incidentRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional
    public AgentExecutionPreparation prepare(
            String agentName,
            Long incidentId,
            UUID requestEventId
    ) {
        AgentDefinition definition = definitionRegistry.getRequired(agentName);
        Incident incident = incidentRepository.findByIdForAnalysis(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        validateExecutionAllowed(incident);
        if (executionRepository
                .findFirstByAgentNameAndIncidentIdAndStatusInOrderByCreatedAtDesc(
                        agentName,
                        incidentId,
                        ACTIVE_STATUSES
                )
                .isPresent()) {
            throw new AgentExecutionAlreadyRunningException(agentName, incidentId);
        }

        AgentExecution execution = AgentExecution.create(definition, incidentId, requestEventId);
        execution.queue();
        execution = executionRepository.save(execution);

        AgentExecutionRequestedEvent event = new AgentExecutionRequestedEvent(
                requestEventId,
                execution.getId(),
                definition.name(),
                definition.version(),
                new AgentLimitsContract(
                        definition.limits().maxSteps(),
                        definition.limits().timeout().toSeconds()
                ),
                toAgentInput(incidentId, incident),
                Instant.now()
        );
        return new AgentExecutionPreparation(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistMessagingFailure(Long executionId, String reason) {
        AgentExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Agent execution not found: " + executionId
                ));
        execution.fail(AgentExecutionFailureType.MESSAGING_FAILURE, reason);
    }

    private void validateExecutionAllowed(Incident incident) {
        if (incident.getStatus() != IncidentStatus.IN_INVESTIGATION) {
            throw new AgentExecutionNotAllowedException(
                    "Only incidents under investigation can be processed by an agent"
            );
        }
        if (incident.getEvidence().isEmpty()) {
            throw new AgentExecutionNotAllowedException(
                    "Incident must contain evidence before agent execution"
            );
        }
        if (incident.hasConfirmedRootCause()) {
            throw new AgentExecutionNotAllowedException(
                    "Incident already has a confirmed root cause"
            );
        }
    }

    private AgentInput toAgentInput(Long incidentId, Incident incident) {
        List<AnalysisEvidence> evidence = incident.getEvidence().stream()
                .map(item -> new AnalysisEvidence(
                        item.getType().name(),
                        item.getSource(),
                        item.getContent(),
                        item.getObservedAt()
                ))
                .toList();

        return new AgentInput(
                incidentId,
                incident.getTitle(),
                incident.getIncidentType(),
                evidence
        );
    }
}
