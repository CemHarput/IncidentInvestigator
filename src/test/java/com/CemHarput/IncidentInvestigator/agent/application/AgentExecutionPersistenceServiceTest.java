package com.CemHarput.IncidentInvestigator.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentDefinition;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecution;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStatus;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentLimits;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionAlreadyRunningException;
import com.CemHarput.IncidentInvestigator.agent.exception.AgentExecutionNotAllowedException;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionRepository;
import com.CemHarput.IncidentInvestigator.incident.domain.Evidence;
import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentExecutionPersistenceServiceTest {

    @Test
    void prepare_shouldCreateQueuedExecutionAndEvidenceSnapshot() {
        AgentDefinitionRegistry registry = registry();
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AgentExecutionRepository executionRepository = mock(AgentExecutionRepository.class);
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident()));
        when(executionRepository
                .findFirstByAgentNameAndIncidentIdAndStatusInOrderByCreatedAtDesc(
                        any(), any(), any()
                ))
                .thenReturn(Optional.empty());
        AgentExecution savedExecution = mock(AgentExecution.class);
        when(savedExecution.getId()).thenReturn(99L);
        when(executionRepository.save(any(AgentExecution.class))).thenReturn(savedExecution);
        UUID requestEventId = UUID.randomUUID();
        AgentExecutionPersistenceService service = new AgentExecutionPersistenceService(
                registry,
                incidentRepository,
                executionRepository
        );

        AgentExecutionPreparation preparation = service.prepare(
                "incident-root-cause-agent",
                42L,
                requestEventId
        );

        assertThat(preparation.event().eventId()).isEqualTo(requestEventId);
        assertThat(preparation.event().executionId()).isEqualTo(99L);
        assertThat(preparation.event().agentVersion()).isEqualTo("1.0");
        assertThat(preparation.event().capabilities()).containsExactly(
                "log-analyzer",
                "metric-analyzer",
                "trace-analyzer"
        );
        assertThat(preparation.event().limits().maxSteps()).isEqualTo(10);
        assertThat(preparation.event().limits().timeoutSeconds()).isEqualTo(60L);
        assertThat(preparation.event().input().incidentId()).isEqualTo(42L);
        assertThat(preparation.event().input().evidence()).hasSize(1);
        verify(executionRepository).save(org.mockito.ArgumentMatchers.argThat(
                execution -> execution.getStatus() == AgentExecutionStatus.QUEUED
                        && execution.getRequestEventId().equals(requestEventId)
        ));
    }

    @Test
    void prepare_shouldRejectIncidentWithoutEvidence() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AgentExecutionRepository executionRepository = mock(AgentExecutionRepository.class);
        Incident incident = new Incident("Latency", "Slow", "LATENCY", "MONITORING");
        incident.startInvestigation();
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident));
        AgentExecutionPersistenceService service = new AgentExecutionPersistenceService(
                registry(),
                incidentRepository,
                executionRepository
        );

        assertThatThrownBy(() -> service.prepare(
                "incident-root-cause-agent",
                42L,
                UUID.randomUUID()
        )).isInstanceOf(AgentExecutionNotAllowedException.class)
                .hasMessage("Incident must contain evidence before agent execution");

        verify(executionRepository, never()).save(any());
    }

    @Test
    void prepare_shouldRejectDuplicateActiveExecution() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AgentExecutionRepository executionRepository = mock(AgentExecutionRepository.class);
        when(incidentRepository.findByIdForAnalysis(42L)).thenReturn(Optional.of(incident()));
        AgentExecution active = AgentExecution.create(
                definition(),
                42L,
                UUID.randomUUID()
        );
        active.queue();
        when(executionRepository
                .findFirstByAgentNameAndIncidentIdAndStatusInOrderByCreatedAtDesc(
                        any(), any(), any()
                ))
                .thenReturn(Optional.of(active));
        AgentExecutionPersistenceService service = new AgentExecutionPersistenceService(
                registry(),
                incidentRepository,
                executionRepository
        );

        assertThatThrownBy(() -> service.prepare(
                "incident-root-cause-agent",
                42L,
                UUID.randomUUID()
        )).isInstanceOf(AgentExecutionAlreadyRunningException.class);

        verify(executionRepository, never()).save(any());
    }

    private AgentDefinitionRegistry registry() {
        return agentName -> definition();
    }

    private AgentDefinition definition() {
        return new AgentDefinition(
                "incident-root-cause-agent",
                "1.0",
                List.of("log-analyzer", "metric-analyzer", "trace-analyzer"),
                new AgentLimits(10, Duration.ofSeconds(60))
        );
    }

    private Incident incident() {
        Incident incident = new Incident(
                "Payment service latency",
                "Database connection pool exhausted",
                "LATENCY",
                "MONITORING"
        );
        incident.startInvestigation();
        incident.addEvidence(new Evidence(
                EvidenceType.LOG,
                "payment-service",
                "HikariPool - Connection is not available",
                LocalDateTime.of(2026, 8, 24, 12, 0)
        ));
        return incident;
    }
}
