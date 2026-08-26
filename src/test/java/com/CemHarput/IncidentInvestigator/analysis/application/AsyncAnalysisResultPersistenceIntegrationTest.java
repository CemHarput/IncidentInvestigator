package com.CemHarput.IncidentInvestigator.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisCompletedEvent;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisFailedEvent;
import com.CemHarput.IncidentInvestigator.incident.domain.Evidence;
import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.domain.RootCause;
import com.CemHarput.IncidentInvestigator.incident.api.IncidentResponse;
import com.CemHarput.IncidentInvestigator.incident.application.IncidentService;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class AsyncAnalysisResultPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15.3")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    AsyncAnalysisResultService resultService;

    @Autowired
    IncidentRepository incidentRepository;

    @Autowired
    IncidentService incidentService;

    @Autowired
    AnalysisExecutionRepository executionRepository;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void cleanDatabase() {
        executionRepository.deleteAll();
        incidentRepository.deleteAll();
    }

    @Test
    void completedEvent_shouldAtomicallyPersistRootCauseExecutionAndIdempotencyKey() {
        PreparedAnalysis prepared = prepareQueuedAnalysis(false);
        UUID eventId = UUID.randomUUID();
        AnalysisCompletedEvent event = completedEvent(
                prepared,
                eventId,
                candidate("DATABASE_CONNECTION_POOL_EXHAUSTION", 0.91d)
        );

        resultService.processCompleted(event);

        AnalysisExecution completed = executionRepository.findById(prepared.executionId()).orElseThrow();
        IncidentResponse incident = incidentService.getIncident(prepared.incidentId());
        Long rootCauseId = incident.rootCause().id();
        assertThat(completed.getStatus()).isEqualTo(AnalysisExecutionStatus.COMPLETED);
        assertThat(completed.getResultEventId()).isEqualTo(eventId);
        assertThat(incident.rootCause().rootCauseType())
                .isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");

        resultService.processCompleted(event);

        IncidentResponse afterDuplicate = incidentService.getIncident(prepared.incidentId());
        assertThat(afterDuplicate.rootCause().id()).isEqualTo(rootCauseId);
    }

    @Test
    void unknownCompletedEvent_shouldPersistInconclusiveWithoutRootCause() {
        PreparedAnalysis prepared = prepareQueuedAnalysis(false);
        UUID eventId = UUID.randomUUID();

        resultService.processCompleted(completedEvent(
                prepared,
                eventId,
                candidate("UNKNOWN", 0.95d)
        ));

        AnalysisExecution execution = executionRepository.findById(prepared.executionId()).orElseThrow();
        IncidentResponse incident = incidentService.getIncident(prepared.incidentId());
        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.INCONCLUSIVE);
        assertThat(execution.getResultEventId()).isEqualTo(eventId);
        assertThat(incident.rootCause()).isNull();
    }

    @Test
    void failedEvent_shouldPersistFailureWithoutChangingIncident() {
        PreparedAnalysis prepared = prepareQueuedAnalysis(false);
        UUID eventId = UUID.randomUUID();
        AnalysisFailedEvent event = new AnalysisFailedEvent(
                eventId,
                prepared.executionId(),
                prepared.incidentId(),
                "INTERNAL_ERROR",
                "Analyzer workload failed",
                LocalDateTime.now()
        );

        resultService.processFailed(event);

        AnalysisExecution execution = executionRepository.findById(prepared.executionId()).orElseThrow();
        IncidentResponse incident = incidentService.getIncident(prepared.incidentId());
        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.FAILED);
        assertThat(execution.getFailureType())
                .isEqualTo(AnalysisExecutionFailureType.INTERNAL_ERROR);
        assertThat(execution.getFailureReason()).isEqualTo("Analyzer workload failed");
        assertThat(execution.getResultEventId()).isEqualTo(eventId);
        assertThat(incident.rootCause()).isNull();
    }

    @Test
    void confirmedRootCause_shouldRollBackCompletedResult() {
        PreparedAnalysis prepared = prepareQueuedAnalysis(true);
        AnalysisCompletedEvent event = completedEvent(
                prepared,
                UUID.randomUUID(),
                candidate("DATABASE_CONNECTION_POOL_EXHAUSTION", 0.91d)
        );

        assertThatThrownBy(() -> resultService.processCompleted(event))
                .hasMessage("Confirmed root cause cannot be overwritten");

        AnalysisExecution execution = executionRepository.findById(prepared.executionId()).orElseThrow();
        IncidentResponse incident = incidentService.getIncident(prepared.incidentId());
        assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.QUEUED);
        assertThat(execution.getResultEventId()).isNull();
        assertThat(incident.rootCause().confirmed()).isTrue();
    }

    private PreparedAnalysis prepareQueuedAnalysis(boolean confirmedRootCause) {
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
        if (confirmedRootCause) {
            incident.identifyRootCause(new RootCause(
                    "Confirmed manually",
                    "MANUAL_ROOT_CAUSE",
                    true
            ));
        }
        incident = incidentRepository.saveAndFlush(incident);

        AnalysisExecution execution = AnalysisExecution.create(incident.getId());
        execution.queue();
        execution = executionRepository.saveAndFlush(execution);
        return new PreparedAnalysis(incident.getId(), execution.getId());
    }

    private AnalysisCompletedEvent completedEvent(
            PreparedAnalysis prepared,
            UUID eventId,
            RootCauseCandidateResponse candidate
    ) {
        return new AnalysisCompletedEvent(
                eventId,
                prepared.executionId(),
                prepared.incidentId(),
                List.of(candidate),
                LocalDateTime.now()
        );
    }

    private RootCauseCandidateResponse candidate(String rootCause, double confidence) {
        return new RootCauseCandidateResponse(
                rootCause,
                confidence,
                "Analyzer explanation",
                List.of("supporting-evidence")
        );
    }

    private record PreparedAnalysis(Long incidentId, Long executionId) {
    }
}
