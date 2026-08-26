package com.CemHarput.IncidentInvestigator.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.CemHarput.IncidentInvestigator.analysis.client.IncidentAnalyzerClient;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionFailureType;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalyzerUnavailableException;
import com.CemHarput.IncidentInvestigator.analysis.exception.AnalysisMessagingException;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.analysis.messaging.AnalysisEventPublisher;
import com.CemHarput.IncidentInvestigator.incident.domain.Evidence;
import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "incident-analyzer.retry.max-attempts=2",
        "incident-analyzer.retry.backoff=0ms"
})
@Testcontainers
class AnalysisReliabilityIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15.3")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    AnalysisService analysisService;

    @Autowired
    IncidentRepository incidentRepository;

    @Autowired
    AnalysisExecutionRepository executionRepository;

    @MockitoBean
    IncidentAnalyzerClient analyzerClient;

    @MockitoBean
    AnalysisEventPublisher eventPublisher;

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
    void timeout_shouldRunHttpOutsideTransactionAndCommitClassifiedFailure() {
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
        Long incidentId = incidentRepository.saveAndFlush(incident).getId();

        AtomicBoolean transactionActiveDuringHttp = new AtomicBoolean(true);
        AnalyzerUnavailableException timeout = new AnalyzerUnavailableException(
                "Incident analyzer request timed out",
                AnalysisExecutionFailureType.TIMEOUT,
                new RuntimeException()
        );
        when(analyzerClient.analyze(any())).thenAnswer(invocation -> {
            transactionActiveDuringHttp.set(
                    TransactionSynchronizationManager.isActualTransactionActive()
            );
            throw timeout;
        });

        assertThatThrownBy(() -> analysisService.analyzeIncident(incidentId)).isSameAs(timeout);

        List<AnalysisExecution> executions =
                executionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
        assertThat(transactionActiveDuringHttp).isFalse();
        assertThat(executions).singleElement().satisfies(execution -> {
            assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.FAILED);
            assertThat(execution.getFailureType()).isEqualTo(AnalysisExecutionFailureType.TIMEOUT);
            assertThat(execution.getFailureReason()).isEqualTo("Incident analyzer request timed out");
            assertThat(execution.getAttemptCount()).isEqualTo(2);
        });
    }

    @Test
    void kafkaFailure_shouldRunPublishOutsideTransactionAndCommitClassifiedFailure() {
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
        Long incidentId = incidentRepository.saveAndFlush(incident).getId();

        AtomicBoolean transactionActiveDuringPublish = new AtomicBoolean(true);
        AnalysisMessagingException publishFailure = new AnalysisMessagingException(
                "Failed to publish analysis request",
                new RuntimeException("Kafka unavailable")
        );
        doAnswer(invocation -> {
            transactionActiveDuringPublish.set(
                    TransactionSynchronizationManager.isActualTransactionActive()
            );
            throw publishFailure;
        }).when(eventPublisher).publishAnalysisRequested(any());

        assertThatThrownBy(() -> analysisService.analyzeIncidentAsync(incidentId))
                .isSameAs(publishFailure);

        List<AnalysisExecution> executions =
                executionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
        assertThat(transactionActiveDuringPublish).isFalse();
        assertThat(executions).singleElement().satisfies(execution -> {
            assertThat(execution.getStatus()).isEqualTo(AnalysisExecutionStatus.FAILED);
            assertThat(execution.getFailureType())
                    .isEqualTo(AnalysisExecutionFailureType.MESSAGING_FAILURE);
            assertThat(execution.getFailureReason())
                    .isEqualTo("Failed to publish analysis request");
        });
    }
}
