package com.CemHarput.IncidentInvestigator.agent.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.CemHarput.IncidentInvestigator.agent.domain.AgentDefinition;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecution;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentExecutionStatus;
import com.CemHarput.IncidentInvestigator.agent.domain.AgentLimits;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionRepository;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.AgentExecutionStepRepository;
import com.CemHarput.IncidentInvestigator.agent.infrastructure.ProcessedAgentEventRepository;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionCompletedEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentExecutionStepEvent;
import com.CemHarput.IncidentInvestigator.agent.messaging.event.AgentResult;
import com.CemHarput.IncidentInvestigator.incident.application.IncidentService;
import com.CemHarput.IncidentInvestigator.incident.domain.Evidence;
import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "agent.kafka.result-consumer-group=agent-result-integration-test",
        "analysis.kafka.consumer.retry-backoff=0ms"
})
@Testcontainers
class AgentExecutionEventConsumerIntegrationTest {

    private static final String STEP_TOPIC = "agent.execution.step.v1";
    private static final String COMPLETED_TOPIC = "agent.execution.completed.v1";

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:4.3.1")
    );

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15.3")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    IncidentRepository incidentRepository;

    @Autowired
    IncidentService incidentService;

    @Autowired
    AgentExecutionRepository executionRepository;

    @Autowired
    AgentExecutionStepRepository stepRepository;

    @Autowired
    ProcessedAgentEventRepository processedEventRepository;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void cleanDatabase() {
        processedEventRepository.deleteAll();
        stepRepository.deleteAll();
        executionRepository.deleteAll();
        incidentRepository.deleteAll();
    }

    @Test
    void stepAndCompletedEvents_shouldPersistIdempotentAuditAndDomainResult() throws Exception {
        PreparedExecution prepared = prepareQueuedExecution();
        UUID stepEventId = UUID.randomUUID();
        AgentExecutionStepEvent stepEvent = new AgentExecutionStepEvent(
                stepEventId,
                prepared.executionId(),
                1,
                "OBSERVATION",
                "log-analyzer",
                "Connection pool timeout signatures detected.",
                "The evidence supports a database saturation hypothesis.",
                Instant.now()
        );

        kafkaTemplate.send(
                STEP_TOPIC,
                prepared.executionId().toString(),
                stepEvent
        ).get();
        kafkaTemplate.send(
                STEP_TOPIC,
                prepared.executionId().toString(),
                stepEvent
        ).get();

        await(() -> stepRepository
                .findByExecutionIdOrderByStepNumberAsc(prepared.executionId())
                .size() == 1);
        AgentExecution running = executionRepository.findById(prepared.executionId()).orElseThrow();
        assertThat(running.getStatus()).isEqualTo(AgentExecutionStatus.RUNNING);
        assertThat(running.getCurrentStep()).isEqualTo(1);

        UUID completedEventId = UUID.randomUUID();
        kafkaTemplate.send(
                COMPLETED_TOPIC,
                prepared.executionId().toString(),
                new AgentExecutionCompletedEvent(
                        completedEventId,
                        prepared.executionId(),
                        "incident-root-cause-agent",
                        new AgentResult(
                                "DATABASE_CONNECTION_POOL_EXHAUSTION",
                                0.91d,
                                "Connection pool saturation matches the evidence.",
                                List.of("HikariPool timeout")
                        ),
                        1,
                        Instant.now()
                )
        ).get();

        await(() -> executionRepository.findById(prepared.executionId())
                .map(execution -> execution.getStatus() == AgentExecutionStatus.COMPLETED)
                .orElse(false));
        AgentExecution completed = executionRepository
                .findById(prepared.executionId())
                .orElseThrow();
        assertThat(completed.getResultEventId()).isEqualTo(completedEventId);
        assertThat(stepRepository
                .findByExecutionIdOrderByStepNumberAsc(prepared.executionId()))
                .hasSize(1);
        assertThat(processedEventRepository.count()).isEqualTo(2L);
        assertThat(incidentService.getIncident(prepared.incidentId()).rootCause().rootCauseType())
                .isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
    }

    private PreparedExecution prepareQueuedExecution() {
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
                LocalDateTime.of(2026, 8, 30, 12, 0)
        ));
        incident = incidentRepository.saveAndFlush(incident);

        AgentExecution execution = AgentExecution.create(
                new AgentDefinition(
                        "incident-root-cause-agent",
                        "1.0",
                        List.of("log-analyzer"),
                        new AgentLimits(10, Duration.ofSeconds(60))
                ),
                incident.getId(),
                UUID.randomUUID()
        );
        execution.queue();
        execution = executionRepository.saveAndFlush(execution);
        return new PreparedExecution(incident.getId(), execution.getId());
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Agent execution event was not persisted before timeout");
    }

    private record PreparedExecution(Long incidentId, Long executionId) {
    }
}
