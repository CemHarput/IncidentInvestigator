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
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionFailureType;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStatus;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStep;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStepType;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentLimits;
import com.CemHarput.IncidentInvestigator.agent.domain.ProcessedAgentEvent;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionRepository;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionStepRepository;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.ProcessedAgentEventRepository;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionCompletedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionFailedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionStepEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentResult;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCauseDecisionPolicy;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AgentExecutionEventServiceTest {

    @Test
    void processStep_shouldPersistAuditAndAdvanceExecution() {
        Fixtures fixtures = fixtures(queuedExecution());
        AgentExecutionStepEvent event = new AgentExecutionStepEvent(
                UUID.randomUUID(),
                99L,
                1,
                "OBSERVATION",
                "log-analyzer",
                "Connection pool timeout signatures detected.",
                "Inspect metric evidence next.",
                Instant.parse("2026-08-30T11:00:00Z")
        );

        fixtures.service().processStep(event);

        assertThat(fixtures.execution().getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);
        assertThat(fixtures.execution().getCurrentStep()).isEqualTo(1);
        ArgumentCaptor<AgentExecutionStep> stepCaptor =
                ArgumentCaptor.forClass(AgentExecutionStep.class);
        verify(fixtures.stepRepository()).save(stepCaptor.capture());
        assertThat(stepCaptor.getValue().getEventId()).isEqualTo(event.eventId());
        assertThat(stepCaptor.getValue().getStepType())
                .isEqualTo(AgentExecutionStepType.OBSERVATION);
        assertThat(stepCaptor.getValue().getObservationSummary())
                .isEqualTo("Connection pool timeout signatures detected.");
        verify(fixtures.processedRepository()).save(any(ProcessedAgentEvent.class));
    }

    @Test
    void processStep_shouldIgnoreAlreadyProcessedEvent() {
        Fixtures fixtures = fixtures(queuedExecution());
        UUID eventId = UUID.randomUUID();
        when(fixtures.processedRepository().findById(eventId))
                .thenReturn(Optional.of(new ProcessedAgentEvent(eventId, 99L, "STEP")));
        AgentExecutionStepEvent event = new AgentExecutionStepEvent(
                eventId,
                99L,
                1,
                "PLAN",
                null,
                null,
                "Inspect log evidence.",
                Instant.now()
        );

        fixtures.service().processStep(event);

        assertThat(fixtures.execution().getStatus()).isEqualTo(AgentExecutionStatus.QUEUED);
        verify(fixtures.stepRepository(), never()).save(any());
    }

    @Test
    void processStep_shouldRejectOutOfOrderAuditEvent() {
        Fixtures fixtures = fixtures(queuedExecution());
        AgentExecutionStepEvent event = new AgentExecutionStepEvent(
                UUID.randomUUID(),
                99L,
                2,
                "PLAN",
                null,
                null,
                "Inspect evidence.",
                Instant.now()
        );

        assertThatThrownBy(() -> fixtures.service().processStep(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected agent execution step 1 but received 2");
    }

    @Test
    void processCompleted_shouldApplyRootCauseAndCompleteExecution() {
        Fixtures fixtures = fixtures(queuedExecution());
        Incident incident = incident();
        when(fixtures.incidentRepository().findByIdForAnalysis(42L))
                .thenReturn(Optional.of(incident));
        UUID eventId = UUID.randomUUID();
        AgentExecutionCompletedEvent event = new AgentExecutionCompletedEvent(
                eventId,
                99L,
                "incident-root-cause-agent",
                result("DATABASE_CONNECTION_POOL_EXHAUSTION"),
                0,
                Instant.now()
        );

        fixtures.service().processCompleted(event);

        assertThat(fixtures.execution().getStatus()).isEqualTo(AgentExecutionStatus.COMPLETED);
        assertThat(fixtures.execution().getResultEventId()).isEqualTo(eventId);
        assertThat(incident.getRootCause().getRootCauseType())
                .isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
    }

    @Test
    void processCompleted_shouldKeepIncidentUnchangedForUnknownResult() {
        Fixtures fixtures = fixtures(queuedExecution());
        AgentExecutionCompletedEvent event = new AgentExecutionCompletedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                result("UNKNOWN"),
                0,
                Instant.now()
        );

        fixtures.service().processCompleted(event);

        assertThat(fixtures.execution().getStatus()).isEqualTo(AgentExecutionStatus.COMPLETED);
        verify(fixtures.incidentRepository(), never()).findByIdForAnalysis(any());
    }

    @Test
    void processCompleted_shouldKeepIncidentUnchangedForLowConfidenceResult() {
        Fixtures fixtures = fixtures(queuedExecution());
        AgentExecutionCompletedEvent event = new AgentExecutionCompletedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                result("DATABASE_PROBLEM", 0.15d),
                0,
                Instant.now()
        );

        fixtures.service().processCompleted(event);

        assertThat(fixtures.execution().getStatus()).isEqualTo(AgentExecutionStatus.COMPLETED);
        verify(fixtures.incidentRepository(), never()).findByIdForAnalysis(any());
    }

    @Test
    void processCompleted_shouldWaitForEntireAuditHistory() {
        Fixtures fixtures = fixtures(queuedExecution());
        AgentExecutionCompletedEvent event = new AgentExecutionCompletedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                result("UNKNOWN"),
                3,
                Instant.now()
        );

        assertThatThrownBy(() -> fixtures.service().processCompleted(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Result event step count does not match persisted audit history");
    }

    @Test
    void processFailed_shouldMapTimeoutToTimedOutState() {
        Fixtures fixtures = fixtures(queuedExecution());
        UUID eventId = UUID.randomUUID();
        AgentExecutionFailedEvent event = new AgentExecutionFailedEvent(
                eventId,
                99L,
                "incident-root-cause-agent",
                "TIMEOUT",
                "Execution deadline reached",
                0,
                Instant.now()
        );

        fixtures.service().processFailed(event);

        assertThat(fixtures.execution().getStatus()).isEqualTo(AgentExecutionStatus.TIMED_OUT);
        assertThat(fixtures.execution().getFailureType())
                .isEqualTo(AgentExecutionFailureType.TIMEOUT);
        assertThat(fixtures.execution().getResultEventId()).isEqualTo(eventId);
    }

    @Test
    void processFailed_shouldMapCapabilityFailureToFailedState() {
        Fixtures fixtures = fixtures(queuedExecution());
        AgentExecutionFailedEvent event = new AgentExecutionFailedEvent(
                UUID.randomUUID(),
                99L,
                "incident-root-cause-agent",
                "CAPABILITY_FAILURE",
                "Metric analyzer failed",
                0,
                Instant.now()
        );

        fixtures.service().processFailed(event);

        assertThat(fixtures.execution().getStatus()).isEqualTo(AgentExecutionStatus.FAILED);
        assertThat(fixtures.execution().getFailureType())
                .isEqualTo(AgentExecutionFailureType.CAPABILITY_FAILURE);
    }

    private Fixtures fixtures(AgentExecution execution) {
        AgentExecutionRepository executionRepository = mock(AgentExecutionRepository.class);
        AgentExecutionStepRepository stepRepository = mock(AgentExecutionStepRepository.class);
        ProcessedAgentEventRepository processedRepository =
                mock(ProcessedAgentEventRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        when(executionRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(execution));
        when(processedRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        AgentExecutionEventService service = new AgentExecutionEventService(
                executionRepository,
                stepRepository,
                processedRepository,
                incidentRepository,
                new RootCauseDecisionPolicy()
        );
        return new Fixtures(
                service,
                execution,
                stepRepository,
                processedRepository,
                incidentRepository
        );
    }

    private AgentExecution queuedExecution() {
        AgentExecution execution = AgentExecution.create(
                new AgentDefinition(
                        "incident-root-cause-agent",
                        "1.0",
                        List.of("log-analyzer"),
                        new AgentLimits(10, Duration.ofSeconds(60))
                ),
                42L,
                UUID.randomUUID()
        );
        ReflectionTestUtils.setField(execution, "id", 99L);
        execution.queue();
        return execution;
    }

    private Incident incident() {
        Incident incident = new Incident("Latency", "Slow payments", "LATENCY", "MONITORING");
        incident.startInvestigation();
        return incident;
    }

    private AgentResult result(String rootCause) {
        return result(rootCause, "UNKNOWN".equals(rootCause) ? 0.1d : 0.91d);
    }

    private AgentResult result(String rootCause, double confidence) {
        return new AgentResult(
                rootCause,
                confidence,
                "Database connection pool saturation matches the evidence.",
                List.of("connection timeout")
        );
    }

    private record Fixtures(
            AgentExecutionEventService service,
            AgentExecution execution,
            AgentExecutionStepRepository stepRepository,
            ProcessedAgentEventRepository processedRepository,
            IncidentRepository incidentRepository
    ) {
    }
}
