package com.CemHarput.IncidentInvestigator.analysis.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecution;
import com.CemHarput.IncidentInvestigator.analysis.domain.AnalysisExecutionStatus;
import com.CemHarput.IncidentInvestigator.analysis.dto.RootCauseCandidateResponse;
import com.CemHarput.IncidentInvestigator.analysis.infrastructure.AnalysisExecutionRepository;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisCompletedEvent;
import com.CemHarput.IncidentInvestigator.analysis.messaging.event.AnalysisFailedEvent;
import com.CemHarput.IncidentInvestigator.incident.application.IncidentService;
import com.CemHarput.IncidentInvestigator.incident.domain.Evidence;
import com.CemHarput.IncidentInvestigator.incident.domain.EvidenceType;
import com.CemHarput.IncidentInvestigator.incident.domain.Incident;
import com.CemHarput.IncidentInvestigator.incident.infrastructure.IncidentRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "analysis.kafka.result-consumer-group=analysis-result-integration-test",
        "analysis.kafka.consumer.retry-backoff=0ms"
})
@Testcontainers
@Import(AnalysisResultConsumerIntegrationTest.TracingTestConfiguration.class)
class AnalysisResultConsumerIntegrationTest {

    private static final String COMPLETED_TOPIC = "incident.analysis.completed.v1";
    private static final String FAILED_TOPIC = "incident.analysis.failed.v1";
    private static final String COMPLETED_DLT = COMPLETED_TOPIC + ".DLT";

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
    AnalysisExecutionRepository executionRepository;

    @Autowired
    Tracer tracer;

    @Autowired
    InMemorySpanExporter spanExporter;

    @Autowired
    MeterRegistry meterRegistry;

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
        spanExporter.reset();
        executionRepository.deleteAll();
        incidentRepository.deleteAll();
    }

    @Test
    void completedAndFailedEvents_shouldBeConsumedAndPersisted() throws Exception {
        PreparedAnalysis completedAnalysis = prepareQueuedAnalysis();
        UUID completedEventId = UUID.randomUUID();
        AnalysisCompletedEvent completedEvent = new AnalysisCompletedEvent(
                completedEventId,
                completedAnalysis.executionId(),
                completedAnalysis.incidentId(),
                List.of(new RootCauseCandidateResponse(
                        "DATABASE_CONNECTION_POOL_EXHAUSTION",
                        0.91d,
                        "Connection pool saturation matches the evidence",
                        List.of("HikariPool timeout")
                )),
                LocalDateTime.now()
        );

        Span parentSpan = tracer.nextSpan().name("test.analysis-result-parent").start();
        String expectedTraceId = parentSpan.context().traceId();
        try (Tracer.SpanInScope ignored = tracer.withSpan(parentSpan)) {
            kafkaTemplate.send(
                    COMPLETED_TOPIC,
                    completedAnalysis.incidentId().toString(),
                    completedEvent
            ).get();
        } finally {
            parentSpan.end();
        }

        AnalysisExecution completed = awaitExecution(
                completedAnalysis.executionId(),
                execution -> execution.getStatus() == AnalysisExecutionStatus.COMPLETED
        );
        assertThat(completed.getResultEventId()).isEqualTo(completedEventId);
        assertThat(incidentService.getIncident(completedAnalysis.incidentId())
                .rootCause().rootCauseType())
                .isEqualTo("DATABASE_CONNECTION_POOL_EXHAUSTION");
        assertThat(awaitSpan("spring.consume.analysis-completed", expectedTraceId).getTraceId())
                .isEqualTo(expectedTraceId);
        assertThat(awaitSpan("analysis.execution.persist-result", expectedTraceId).getTraceId())
                .isEqualTo(expectedTraceId);

        PreparedAnalysis failedAnalysis = prepareQueuedAnalysis();
        UUID failedEventId = UUID.randomUUID();
        kafkaTemplate.send(
                FAILED_TOPIC,
                failedAnalysis.incidentId().toString(),
                new AnalysisFailedEvent(
                        failedEventId,
                        failedAnalysis.executionId(),
                        failedAnalysis.incidentId(),
                        "INTERNAL_ERROR",
                        "Analyzer workload failed",
                        LocalDateTime.now()
                )
        ).get();

        AnalysisExecution failed = awaitExecution(
                failedAnalysis.executionId(),
                execution -> execution.getStatus() == AnalysisExecutionStatus.FAILED
        );
        assertThat(failed.getResultEventId()).isEqualTo(failedEventId);
        assertThat(failed.getFailureReason()).isEqualTo("Analyzer workload failed");
        assertThat(incidentService.getIncident(failedAnalysis.incidentId()).rootCause()).isNull();
    }

    @Test
    void poisonCompletedMessage_shouldBePublishedToDlt() throws Exception {
        double dltCountBefore = meterRegistry
                .counter("incident.analysis.kafka.dlt.total")
                .count();
        try (KafkaConsumer<String, String> dltConsumer = consumer("analysis-result-dlt-test")) {
            dltConsumer.subscribe(List.of(COMPLETED_DLT));
            awaitAssignment(dltConsumer);

            kafkaTemplate.send(COMPLETED_TOPIC, "42", "not-an-analysis-event").get();

            ConsumerRecord<String, String> dltRecord = awaitRecord(dltConsumer);
            assertThat(dltRecord.topic()).isEqualTo(COMPLETED_DLT);
            assertThat(dltRecord.key()).isEqualTo("42");
            assertThat(dltRecord.value()).contains("not-an-analysis-event");
            assertThat(meterRegistry.counter("incident.analysis.kafka.dlt.total").count())
                    .isEqualTo(dltCountBefore + 1.0d);
        }
    }

    private PreparedAnalysis prepareQueuedAnalysis() {
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
        incident = incidentRepository.saveAndFlush(incident);

        AnalysisExecution execution = AnalysisExecution.create(incident.getId());
        execution.queue();
        execution = executionRepository.saveAndFlush(execution);
        return new PreparedAnalysis(incident.getId(), execution.getId());
    }

    private AnalysisExecution awaitExecution(
            Long executionId,
            Predicate<AnalysisExecution> completed
    ) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            AnalysisExecution execution = executionRepository.findById(executionId).orElseThrow();
            if (completed.test(execution)) {
                return execution;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Analysis execution did not reach expected state: " + executionId);
    }

    private KafkaConsumer<String, String> consumer(String groupId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private void awaitAssignment(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (consumer.assignment().isEmpty() && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(250));
        }
        assertThat(consumer.assignment()).isNotEmpty();
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, String> record : records) {
                return record;
            }
        }
        throw new AssertionError("No record received from " + COMPLETED_DLT);
    }

    private SpanData awaitSpan(String name, String traceId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            for (SpanData span : spanExporter.getFinishedSpanItems()) {
                if (span.getName().equals(name) && span.getTraceId().equals(traceId)) {
                    return span;
                }
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Span was not exported: " + name);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TracingTestConfiguration {

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        SdkTracerProviderBuilderCustomizer testSpanExporter(
                InMemorySpanExporter spanExporter
        ) {
            return builder -> builder.addSpanProcessor(
                    SimpleSpanProcessor.create(spanExporter)
            );
        }
    }

    private record PreparedAnalysis(Long incidentId, Long executionId) {
    }
}
